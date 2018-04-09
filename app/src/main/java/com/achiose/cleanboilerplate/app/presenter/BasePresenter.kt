package com.achiose.cleanboilerplate.app.presenter

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver

/**
 * Created by daitan on 09/04/18.
 */
interface BasePresenter<View> : LifecycleObserver {
    fun attachView(view: View, viewLifecyle : Lifecycle)
    fun addStickyContinuation(continuation: StickyContinuation<*>, block: View.(StickyContinuation<*>) -> Unit)
    fun removeStickyContinuation(continuation: StickyContinuation<*>): Boolean
}