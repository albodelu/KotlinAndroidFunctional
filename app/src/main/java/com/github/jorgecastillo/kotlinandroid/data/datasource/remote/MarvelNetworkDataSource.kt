package com.github.jorgecastillo.kotlinandroid.data.datasource.remote

import com.github.jorgecastillo.architecturecomponentssample.model.error.CharacterError
import com.github.jorgecastillo.architecturecomponentssample.model.error.CharacterError.*
import com.github.jorgecastillo.kotlinandroid.di.context.GetHeroesContext
import com.github.jorgecastillo.kotlinandroid.functional.*
import com.karumi.marvelapiclient.MarvelApiException
import com.karumi.marvelapiclient.MarvelAuthApiException
import com.karumi.marvelapiclient.model.CharacterDto
import com.karumi.marvelapiclient.model.CharactersQuery
import kategory.Either.Left
import kategory.Either.Right
import kategory.*
import java.net.HttpURLConnection

/*
 * This is the network data source. Calls are made using Karumi's MarvelApiClient.
 * @see "https://github.com/Karumi/MarvelApiClientAndroid"
 *
 * Both requests return a new Reader enclosing an action to resolve when you provide them with the
 * required execution context.
 *
 * The getHeroesFromAvengerComicsUseCase() method maps the fetchAllHeroes() result to filter the list with just the
 * elements with given conditions. It's returning heroes appearing on comics with the  "Avenger"
 * word in the title. Yep, I wanted to retrieve Avengers but the Marvel API is a bit weird
 * sometimes.
 */
fun exceptionAsCharacterError(e: Throwable): CharacterError =
        when (e) {
            is MarvelAuthApiException -> AuthenticationError
            is MarvelApiException ->
                if (e.httpCode == HttpURLConnection.HTTP_NOT_FOUND) NotFoundError
                else UnknownServerError(Option.Some(e))
            else -> UnknownServerError((Option.Some(e)))
        }


inline fun <reified F> fetchAllHeroes(C : Control<F> = control()): HK<F, List<CharacterDto>> =
        C.binding {
            val query = CharactersQuery.Builder.create().withOffset(0).withLimit(50).build()
            val ctx = !C.ask()
            C.catch(
                    { ctx.apiClient.getAll(query).response.characters },
                    { exceptionAsCharacterError(it) }
            )
        }

inline fun <reified F> fetchHeroesFromAvengerComics(C : Control<F> = control()): HK<F, List<CharacterDto>> =
    C.map(fetchAllHeroes(), {
            it.filter {
                it.comics.items.map { it.name }.filter {
                    it.contains("Avenger", true)
                }.count() > 0
            }
    })
