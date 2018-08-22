/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.internal.*
import kotlin.coroutines.experimental.*

/**
 * Defines elements in [CoroutineContext] that are installed into thread context
 * every time the coroutine with this element in the context is resumed on a thread.
 *
 * Implementations of this interface define a type [S] of the thread-local state that they need to store on
 * resume of a coroutine and restore later on suspend and the infrastructure provides the corresponding storage.
 *
 * Example usage looks like this:
 *
 * ```
 * // Appends "name" of a coroutine to a current thread name when coroutine is executed
 * class CoroutineName(val name: String) : ThreadContextElement<String> {
 *     // declare companion object for a key of this element in coroutine context
 *     companion object Key : CoroutineContext.Key<CoroutineName>
 *
 *     // provide the key of the corresponding context element
 *     override val key: CoroutineContext.Key<CoroutineName>
 *         get() = Key
 *
 *     // this is invoked before coroutine is resumed on current thread
 *     override fun updateThreadContext(context: CoroutineContext): String {
 *         val previousName = Thread.currentThread().name
 *         Thread.currentThread().name = "$previousName # $name"
 *         return previousName
 *     }
 *
 *     // this is invoked after coroutine has suspended on current thread
 *     override fun restoreThreadContext(context: CoroutineContext, oldState: String) {
 *         Thread.currentThread().name = oldState
 *     }
 * }
 *
 * // Usage
 * launch(UI + CoroutineName("Progress bar coroutine")) { ... }
 * ```
 * Every time launched coroutine is executed, UI thread name will be updated to "UI thread original name # Progress bar coroutine"
 *
 * Note that for raw [ThreadLocal]s [asContextElement] factory should be used without any intermediate [ThreadContextElement] implementations
 */
public interface ThreadContextElement<S> : CoroutineContext.Element {
    /**
     * Updates context of the current thread.
     * This function is invoked before the coroutine in the specified [context] is resumed in the current thread
     * when the context of the coroutine this element.
     * The result of this function is the old value of the thread-local state that will be passed to [restoreThreadContext].
     *
     * @param context the coroutine context.
     */
    public fun updateThreadContext(context: CoroutineContext): S

    /**
     * Restores context of the current thread.
     * This function is invoked after the coroutine in the specified [context] is suspended in the current thread
     * if [updateThreadContext] was previously invoked on resume of this coroutine.
     * The value of [oldState] is the result of the previous invocation of [updateThreadContext] and it should
     * be restored in the thread-local state by this function.
     *
     * @param context the coroutine context.
     * @param oldState the value returned by the previous invocation of [updateThreadContext].
     */
    public fun restoreThreadContext(context: CoroutineContext, oldState: S)
}

/**
 * Wraps [ThreadLocal] into [ThreadContextElement]. Resulting [ThreadContextElement] will
 * maintain given [ThreadLocal] value for coroutine not depending on actual thread it's run on.
 * By default [ThreadLocal.get] is used as a initial value for the element, but it can be overridden with [initialValue] parameter.
 *
 * Example usage looks like this:
 * ```
 * val myThreadLocal = ThreadLocal<String?>()
 * ...
 * println(myThreadLocal.get()) // Will print "null"
 * launch(CommonPool + myThreadLocal.asContextElement(initialValue = "foo")) {
 *   println(myThreadLocal.get()) // Will print "foo"
 *   withContext(UI) {
 *     println(myThreadLocal.get()) // Will print "foo", but it's UI thread
 *   }
 * }
 *
 * println(myThreadLocal.get()) // Will print "null"
 * ```
 *
 * Note that context element doesn't track modifications of thread local, for example
 *
 * ```
 * myThreadLocal.set("main")
 * withContext(UI) {
 *   println(myThreadLocal.get()) // will print "main"
 *   myThreadLocal.set("UI")
 * }
 *
 * println(myThreadLocal.get()) // will print "main", not "UI"
 * ```
 *
 * For modifications mutable boxes should be used instead
 */
public fun <T> ThreadLocal<T>.asContextElement(initialValue: T = get()): ThreadContextElement<T> {
    return ThreadLocalElement(initialValue, this)
}
