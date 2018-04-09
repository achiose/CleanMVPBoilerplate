package com.achiose.cleanboilerplate.app.domain

import android.support.annotation.CallSuper
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async

/**
 * Created by daitan on 09/04/18.
 */
abstract class UseCase {

    private val deferredObjects: MutableList<Deferred<*>> = mutableListOf()

    @CallSuper
    @Synchronized
    protected suspend fun <T> async(block: suspend CoroutineScope.() -> T): Deferred<T> {
        val deferred: Deferred<T> = async(CommonPool) { block() }
        deferredObjects.add(deferred)
        deferred.invokeOnCompletion { deferredObjects.remove(deferred) }
        return deferred
    }

    @CallSuper
    @Synchronized
    protected suspend fun <T> asyncAwait(block: suspend CoroutineScope.() -> T) : T {
        return async(block).await()
    }

    @CallSuper
    @Synchronized
    protected fun cancelAllAsync() {
        val deferredObjectsSize = deferredObjects.size

        if (deferredObjectsSize > 0) {
            for (i in deferredObjectsSize -1 downTo 0) {
                deferredObjects[i].cancel()
            }
        }
    }

    @CallSuper
    @Synchronized
    open fun cleanup() {
        cancelAllAsync()
    }
}