package com.achiose.cleanboilerplate.app.presenter

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.OnLifecycleEvent
import android.arch.lifecycle.ViewModel
import android.support.annotation.CallSuper
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine
import com.achiose.cleanboilerplate.app.utils.CoroutinesUtils.Companion.tryCatchFinally
import com.achiose.cleanboilerplate.app.utils.CoroutinesUtils.Companion.tryCatch
import com.achiose.cleanboilerplate.app.utils.CoroutinesUtils.Companion.tryFinally


/**
 * Created by daitan on 09/04/18.
 */
abstract class BasePresenterImpl<View> : ViewModel(), BasePresenter<View> {

    private val asyncJobs : MutableList<Job> = mutableListOf()

    private var viewInstance: View? = null
    private var viewLifecycle: Lifecycle? = null
    private val isViewResumed = AtomicBoolean(false)
    private val viewContinuations: MutableList<Continuation<View>> = mutableListOf()
    private val stickyContinuations: MutableMap<StickyContinuation<*>, View.(StickyContinuation<*>) -> Unit> = mutableMapOf()
    private var mustRestoreStickyContinuations: Boolean = false

    @Synchronized
    protected suspend fun view(): View {
        if(isViewResumed.get()) {
            viewInstance?.let { return it }
        }

        // wait unitl the view is ready to be used again
        return suspendCoroutine { continuation -> viewContinuations.add(continuation) }
    }

    @Synchronized
    override fun attachView(view: View, viewLifecyle: Lifecycle) {
        viewInstance = view
        this.viewLifecycle = viewLifecycle

        onViewAttached(view)
    }

    open protected fun onViewAttached(view: View) {

    }

    @Synchronized
    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    private fun onViewStateChanged() {
        isViewResumed.set(viewLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: false)
    }

    @Synchronized
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onViewReadyForContinuations() {
        val view = viewInstance

        if (view != null) {
            val viewContinuationsIterator = viewContinuations.listIterator()

            while (viewContinuationsIterator.hasNext()) {
                val continuation = viewContinuationsIterator.next()

                // The view was not ready when the presenter needed it earlier,
                // but now it's ready again so the presenter can continue
                // interacting with it.
                viewContinuationsIterator.remove()
                continuation.resume(view)
            }
        }
    }

    @Synchronized
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onViewReadyForStickyContinuations() {
        val view = viewInstance

        if (mustRestoreStickyContinuations && view != null) {
            mustRestoreStickyContinuations = false

            val stickyContinuationsIterator = stickyContinuations.iterator()

            while (stickyContinuationsIterator.hasNext()) {
                val stickyContinuationBlockMap = stickyContinuationsIterator.next()
                val stickyContinuation = stickyContinuationBlockMap.key
                val stickyContinuationBlock = stickyContinuationBlockMap.value
                view.stickyContinuationBlock(stickyContinuation)
            }
        }
    }

    @Synchronized
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onViewDestroyed() {
        viewInstance = null
        viewLifecycle = null
        mustRestoreStickyContinuations = true
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }

    @Synchronized
    override fun addStickyContinuation(continuation: StickyContinuation<*>,
                                       block: View.(StickyContinuation<*>) -> Unit) {
        stickyContinuations[continuation] = block
    }

    @Synchronized
    override fun removeStickyContinuation(continuation: StickyContinuation<*>): Boolean {
        return stickyContinuations.remove(continuation) != null
    }

    /**
     * Executes the given block on the view. The block is executed again
     * every time the view instance changes and the new view is resumed.
     * This, for example, is useful for dialogs that need to be persisted
     * across orientation changes.
     *
     * @param block code that has to be executed on the view
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <ReturnType> View.stickySuspension(
            block: View.(StickyContinuation<ReturnType>) -> Unit): ReturnType {
        return suspendCoroutine<ReturnType> { continuation ->
            val stickyContinuation: StickyContinuation<ReturnType> = StickyContinuation(continuation, this@BasePresenterImpl)
            addStickyContinuation(stickyContinuation, block as View.(StickyContinuation<*>) -> Unit)
            block(stickyContinuation)
        }
    }

    @CallSuper
    @Synchronized
    protected fun launchAsync(block: suspend CoroutineScope.() -> Unit) {
        val job: Job = launch(UI) { block() }
        asyncJobs.add(job)
        job.invokeOnCompletion { asyncJobs.remove(job) }
    }

    @Synchronized
    protected fun launchAsyncTryCatch(
            tryBlock: suspend CoroutineScope.() -> Unit,
            catchBlock: suspend CoroutineScope.(Throwable) -> Unit,
            handleCancellationExceptionManually: Boolean = false) {
        launchAsync { tryCatch(tryBlock, catchBlock, handleCancellationExceptionManually) }
    }

    @Synchronized
    protected fun launchAsyncTryCatchFinally(
            tryBlock: suspend CoroutineScope.() -> Unit,
            catchBlock: suspend CoroutineScope.(Throwable) -> Unit,
            finallyBlock: suspend CoroutineScope.() -> Unit,
            handleCancellationExceptionManually: Boolean = false) {
        launchAsync { tryCatchFinally(tryBlock, catchBlock, finallyBlock, handleCancellationExceptionManually) }
    }

    @Synchronized
    protected fun launchAsyncTryFinally(
            tryBlock: suspend CoroutineScope.() -> Unit,
            finallyBlock: suspend CoroutineScope.() -> Unit,
            suppressCancellationException: Boolean = false) {
        launchAsync { tryFinally(tryBlock, finallyBlock, suppressCancellationException) }
    }

    @CallSuper
    @Synchronized
    protected fun cancelAllAsync() {
        val asyncJobsSize = asyncJobs.size

        if (asyncJobsSize > 0) {
            for (i in asyncJobsSize - 1 downTo 0) {
                asyncJobs[i].cancel()
            }
        }
    }

    @CallSuper
    @Synchronized
    open fun cleanup() {
        cancelAllAsync()
    }
}