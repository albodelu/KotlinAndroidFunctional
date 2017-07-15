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
typealias KleisliF<F, D> = HK2<Kleisli.F, F, D>

class KleisliMonadError<F, D, E>(val FM: MonadError<F, E>) : MonadError<KleisliF<F, D>, E> {
    override fun <A, B> flatMap(fa: HK<KleisliF<F, D>, A>, f: (A) -> HK<KleisliF<F, D>, B>): Kleisli<F, D, B> =
            fa.ev().flatMap(f.andThen { it.ev() })

    override fun <A, B> map(fa: HK<KleisliF<F, D>, A>, f: (A) -> B): Kleisli<F, D, B> =
            fa.ev().map(f)

    override fun <A, B> product(fa: HK<KleisliF<F, D>, A>, fb: HK<KleisliF<F, D>, B>): Kleisli<F, D, Tuple2<A, B>> =
            Kleisli(FM, { FM.product(fa.ev().run(it), fb.ev().run(it)) })

    override fun <A> handleErrorWith(fa: HK<KleisliF<F, D>, A>, f: (E) -> HK<KleisliF<F, D>, A>): Kleisli<F, D, A> =
            Kleisli(FM, {
                FM.handleErrorWith(fa.ev().run(it), { e: E -> f(e).ev().run(it) })
            })

    override fun <A, B> tailRecM(a: A, f: (A) -> HK<KleisliF<F, D>, Either<A, B>>): Kleisli<F, D, B> =
            Kleisli(FM, { b -> FM.tailRecM(a, { f(it).ev().run(b) }) })

    override fun <A> raiseError(e: E): Kleisli<F, D, A> =
            Kleisli(FM, { FM.raiseError(e) })

    override fun <A> pure(a: A): Kleisli<F, D, A> =
            Kleisli(FM, { FM.pure(a) })
}

