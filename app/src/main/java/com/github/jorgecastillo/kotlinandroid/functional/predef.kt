package com.github.jorgecastillo.kotlinandroid.functional

import com.github.jorgecastillo.architecturecomponentssample.model.error.*
import com.github.jorgecastillo.kotlinandroid.di.context.GetHeroesContext
import kategory.*

typealias Result<A> = Kleisli<AsyncResult.F, GetHeroesContext, A>
typealias AsyncResultKind<A> = HK<AsyncResult.F, A>

fun <A> AsyncResultKind<A>.ev(): AsyncResult<A> =
        this as AsyncResult<A>

//There should be a monad error instance for EitherT in kategory
class AsyncResult<A>(val value: EitherT<Future.F, CharacterError, A>) : AsyncResultKind<A> {

    class F private constructor()

    fun <B> flatMap(f: (A) -> AsyncResultKind<B>): AsyncResult<B> =
            AsyncResult(ETM.flatMap(value, f.andThen({ it.ev().value })))

    fun <B> map(f: (A) -> B): AsyncResult<B> =
            AsyncResult(ETM.map(value, f))

    fun <B> product(fb: AsyncResult<B>): AsyncResult<Tuple2<A, B>> =
            AsyncResult(ETM.product(value, fb.ev().value).ev())

    fun handleErrorWith(f: (CharacterError) -> AsyncResult<A>): AsyncResult<A> {
        return AsyncResult(EitherT(Future, Future.flatMap(value.value.ev(), {
            it.fold({ f(it).value.value.ev() }, { Future.pure(Either.Right(it)) })
        })))
    }

    companion object : MonadError<AsyncResult.F, CharacterError> {

        val ETM = EitherTMonad<Future.F, CharacterError>(Future)

        override fun <A, B> flatMap(fa: AsyncResultKind<A>, f: (A) -> AsyncResultKind<B>): AsyncResult<B> =
                fa.ev().flatMap(f)

        override fun <A, B> map(fa: AsyncResultKind<A>, f: (A) -> B): HK<F, B> =
                fa.ev().map(f)

        override fun <A, B> product(fa: AsyncResultKind<A>, fb: AsyncResultKind<B>): AsyncResult<Tuple2<A, B>> =
                fa.ev().product(fb.ev())

        override fun <A> handleErrorWith(fa: AsyncResultKind<A>, f: (CharacterError) -> AsyncResultKind<A>): AsyncResult<A> =
                fa.ev().handleErrorWith(f.andThen({ it.ev() }))

        override fun <A, B> tailRecM(a: A, f: (A) -> AsyncResultKind<Either<A, B>>): AsyncResult<B> =
                AsyncResult(ETM.tailRecM(a, f.andThen({ it.ev().value })))

        override fun <A> raiseError(e: CharacterError): AsyncResult<A> =
                AsyncResult(EitherT.left(e, Future))

        override fun <A> pure(a: A): AsyncResult<A> =
                AsyncResult(ETM.pure(a))
    }
}

