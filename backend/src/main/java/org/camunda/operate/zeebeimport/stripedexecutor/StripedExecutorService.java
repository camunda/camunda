/*
 * Copyright (C) 2000-2013 Heinz Max Kabutz
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Heinz Max Kabutz licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.zeebeimport.stripedexecutor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.camunda.operate.exceptions.OperateRuntimeException;

/**
 * The StripedExecutorService accepts Runnable/Callable objects
 * that also implement the StripedObject interface.  It executes
 * all the tasks for a single "stripe" consecutively.
 * <p/>
 * In this version, submitted tasks do not necessarily have to
 * implement the StripedObject interface.  If they do not, then
 * they will simply be passed onto the wrapped ExecutorService
 * directly.
 * <p/>
 * Idea inspired by Glenn McGregor on the Concurrency-interest
 * mailing list and using the SerialExecutor presented in the
 * Executor interface's JavaDocs.
 * <p/>
 * http://cs.oswego.edu/mailman/listinfo/concurrency-interest
 *
 * @author Dr Heinz M. Kabutz
 */
public class StripedExecutorService extends AbstractExecutorService {
  /**
   * The wrapped ExecutorService that will actually execute our
   * tasks.
   */
  private final ExecutorService executor;

  /**
   * The lock prevents shutdown from being called in the middle
   * of a submit.  It also guards the executors IdentityHashMap.
   */
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * This condition allows us to cleanly terminate this executor
   * service.
   */
  private final Condition terminating = lock.newCondition();

  /**
   * Whenever a new StripedObject is submitted to the pool, it
   * is added to this IdentityHashMap.  As soon as the
   * SerialExecutor is empty, the entry is removed from the map,
   * in order to avoid a memory leak.
   */
  private final Map<Object, SerialExecutor> executors =
      new IdentityHashMap<>();

  /**
   * The default submit() method creates a new FutureTask and
   * wraps our StripedRunnable with it.  We thus need to
   * remember the stripe object somewhere.  In our case, we will
   * do this inside the ThreadLocal "stripes".  Before the
   * thread returns from submitting the runnable, it will always
   * remove the thread local entry.
   */
  private final static ThreadLocal<Object> stripes =
      new ThreadLocal<>();

  /**
   * Valid states are RUNNING and SHUTDOWN.  We rely on the
   * underlying executor service for the remaining states.
   */
  private State state = State.RUNNING;

  private static enum State {
    RUNNING, SHUTDOWN
  }

  /**
   * The constructor taking executors is private, since we do
   * not want users to shutdown their executors directly,
   * otherwise jobs might get stuck in our queues.
   *
   * @param executor the executor service that we use to execute
   *                 the tasks
   */
  private StripedExecutorService(ExecutorService executor) {
    this.executor = executor;
  }

  /**
   * This constructs a StripedExecutorService that wraps a
   * cached thread pool.
   */
  public StripedExecutorService() {
    this(Executors.newCachedThreadPool());
  }

  /**
   * This constructs a StripedExecutorService that wraps a fixed
   * thread pool with the given number of threads.
   */
  public StripedExecutorService(int numberOfThreads) {
    this(Executors.newFixedThreadPool(numberOfThreads));
  }

  /**
   * If the runnable also implements StripedObject, we store the
   * stripe object in a thread local, since the actual runnable
   * will be wrapped with a FutureTask.
   */
  protected <T> RunnableFuture<T> newTaskFor(
      Runnable runnable, T value) {
    saveStripedObject(runnable);
    return super.newTaskFor(runnable, value);
  }

  /**
   * If the callable also implements StripedObject, we store the
   * stripe object in a thread local, since the actual callable
   * will be wrapped with a FutureTask.
   */
  protected <T> RunnableFuture<T> newTaskFor(
      Callable<T> callable) {
    saveStripedObject(callable);
    return super.newTaskFor(callable);
  }

  /**
   * Saves the stripe in a ThreadLocal until we can use it to
   * schedule the task into our pool.
   */
  private void saveStripedObject(Object task) {
    if (isStripedObject(task)) {
      stripes.set(((StripedObject) task).getStripe());
    }
  }

  /**
   * Returns true if the object implements the StripedObject
   * interface.
   */
  private static boolean isStripedObject(Object o) {
    return o instanceof StripedObject;
  }

  public Future<?> submit(Runnable task) {
    if (!(task instanceof StripedRunnable)) {
      throw new UnsupportedOperationException("Runnable cannot be submitted. Consider using StripedCallable.");
    }
    return submit(task, null);
  }

  public <T> Future<T> submit(Runnable task, T result) {
    if (!(task instanceof StripedRunnable)) {
      throw new UnsupportedOperationException("Runnable cannot be submitted. Consider using StripedCallable.");
    }
    lock.lock();
    try {
      checkPoolIsRunning();
      return super.submit(task, result);
    } finally {
      lock.unlock();
    }
  }

  /**
   * If the task is a StripedObject, we execute it in-order by
   * its stripe, otherwise we submit it directly to the wrapped
   * executor.  If the pool is not running, we throw a
   * RejectedExecutionException.
   */
  @Override
  public <T> Future<T> submit(Callable<T> task) {
    if (!(task instanceof StripedCallable)) {
      throw new UnsupportedOperationException("Callable cannot be submitted. Consider using StripedCallable.");
    }
    lock.lock();
    try {
      checkPoolIsRunning();
      return super.submit(task);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Throws a RejectedExecutionException if the state is not
   * RUNNING.
   */
  private void checkPoolIsRunning() {
    assert lock.isHeldByCurrentThread();
    if (state != State.RUNNING) {
      throw new RejectedExecutionException(
          "executor not running");
    }
  }

  /**
   * Executes the command.  If command implements StripedObject,
   * we execute it with a SerialExecutor.  This method can be
   * called directly by clients or it may be called by the
   * AbstractExecutorService's submit() methods. In that case,
   * we check whether the stripes thread local has been set.  If
   * it is, we remove it and use it to determine the
   * StripedObject and execute it with a SerialExecutor.  If no
   * StripedObject is set, we instead pass the command to the
   * wrapped ExecutorService directly.
   */
  public void execute(Runnable command) {
    lock.lock();
    try {
      checkPoolIsRunning();
      Object stripe = getStripe(command);
      if (stripe != null) {
        SerialExecutor ser_exec = executors.get(stripe);
        if (ser_exec == null) {
          executors.put(stripe, ser_exec =
              new SerialExecutor(stripe));
        }
        ser_exec.execute(command);
      } else {
        //TODO validate earlier
        throw new OperateRuntimeException("Stripe is null.");
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * We get the stripe object either from the Runnable if it
   * also implements StripedObject, or otherwise from the thread
   * local temporary storage.  Result may be null.
   */
  private Object getStripe(Runnable command) {
    Object stripe;
    if (command instanceof StripedObject) {
      stripe = (((StripedObject) command).getStripe());
    } else {
      stripe = stripes.get();
    }
    stripes.remove();
    return stripe;
  }

  /**
   * Shuts down the StripedExecutorService.  No more tasks will
   * be submitted.  If the map of SerialExecutors is empty, we
   * shut down the wrapped executor.
   */
  public void shutdown() {
    lock.lock();
    try {
      state = State.SHUTDOWN;
      if (executors.isEmpty()) {
        executor.shutdown();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * All the tasks in each of the SerialExecutors are drained
   * to a list, as well as the tasks inside the wrapped
   * ExecutorService.  This is then returned to the user.  Also,
   * the shutdownNow method of the wrapped executor is called.
   */
  public List<Runnable> shutdownNow() {
    lock.lock();
    try {
      shutdown();
      List<Runnable> result = new ArrayList<>();
      for (SerialExecutor ser_ex : executors.values()) {
        ser_ex.tasks.drainTo(result);
      }
      result.addAll(executor.shutdownNow());
      return result;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns true if shutdown() or shutdownNow() have been
   * called; false otherwise.
   */
  public boolean isShutdown() {
    lock.lock();
    try {
      return state == State.SHUTDOWN;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns true if this pool has been terminated, that is, all
   * the SerialExecutors are empty and the wrapped
   * ExecutorService has been terminated.
   */
  public boolean isTerminated() {
    lock.lock();
    try {
      if (state == State.RUNNING) return false;
      for (SerialExecutor executor : executors.values()) {
        if (!executor.isEmpty()) return false;
      }
      return executor.isTerminated();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns true if the wrapped ExecutorService terminates
   * within the allotted amount of time.
   */
  public boolean awaitTermination(long timeout, TimeUnit unit)
      throws InterruptedException {
    lock.lock();
    try {
      long waitUntil = System.nanoTime() + unit.toNanos(timeout);
      long remainingTime;
      while ((remainingTime = waitUntil - System.nanoTime()) > 0
          && !executors.isEmpty()) {
        terminating.awaitNanos(remainingTime);
      }
      if (remainingTime <= 0) return false;
      if (executors.isEmpty()) {
        return executor.awaitTermination(
            remainingTime, TimeUnit.NANOSECONDS);
      }
      return false;
    } finally {
      lock.unlock();
    }
  }

  /**
   * As soon as a SerialExecutor is empty, we remove it from the
   * executors map.  We might thus remove the SerialExecutors
   * more quickly than necessary, but at least we can avoid a
   * memory leak.
   */
  private void removeEmptySerialExecutor(Object stripe,
      SerialExecutor ser_ex) {
    assert ser_ex == executors.get(stripe);
    assert lock.isHeldByCurrentThread();
    assert ser_ex.isEmpty();

    executors.remove(stripe);
    terminating.signalAll();
    if (state == State.SHUTDOWN && executors.isEmpty()) {
      executor.shutdown();
    }
  }

  /**
   * Prints information about current state of this executor, the
   * wrapped executor and the serial executors.
   */
  public String toString() {
    lock.lock();
    try {
      return "StripedExecutorService: state=" + state + ", " +
          "executor=" + executor + ", " +
          "serialExecutors=" + executors;
    } finally {
      lock.unlock();
    }

  }

  /**
   * This field is used for conditional compilation.  If it is
   * false, then the finalize method is an empty method, in
   * which case the SerialExecutor will not be registered with
   * the Finalizer.
   */
  private static boolean DEBUG = false;

  /**
   * SerialExecutor is based on the construct with the same name
   * described in the {@link Executor} JavaDocs.  The difference
   * with our SerialExecutor is that it can be terminated.  It
   * also removes itself automatically once the queue is empty.
   */
  private class SerialExecutor implements Executor {
    /**
     * The queue of unexecuted tasks.
     */
    private final BlockingQueue<Runnable> tasks =
        new LinkedBlockingQueue<>();
    /**
     * The runnable that we are currently busy with.
     */
    private Runnable active;
    /**
     * The stripe that this SerialExecutor was defined for.  It
     * is needed so that we can remove this executor from the
     * map once it is empty.
     */
    private final Object stripe;

    /**
     * Creates a SerialExecutor for a particular stripe.
     */
    private SerialExecutor(Object stripe) {
      this.stripe = stripe;
      if (DEBUG) {
        System.out.println("SerialExecutor created " + stripe);
      }
    }

    /**
     * We use finalize() only for debugging purposes.  If
     * DEBUG==false, the body of the method will be compiled
     * away, thus rendering it a trivial finalize() method,
     * which means that the object will not incur any overhead
     * since it won't be registered with the Finalizer.
     */
    protected void finalize() throws Throwable {
      if (DEBUG) {
        System.out.println("SerialExecutor finalized " + stripe);
        super.finalize();
      }
    }

    /**
     * For every task that is executed, we add() a wrapper to
     * the queue of tasks that will run the current task and
     * then schedule the next task in the queue.
     */
    public void execute(final Runnable r) {
      lock.lock();
      try {
        tasks.add(() -> {
          try {
            boolean succeeded = false;
            try {
              r.run();
              succeeded = true;
            } finally {
              if (succeeded) {
                scheduleNext();
              } else {
                rescheduleCurrent();
              }
            }
          } catch (Exception ex) {
            rescheduleCurrent();
          }
        });
        if (active == null) {
          scheduleNext();
        }
      } finally {
        lock.unlock();
      }
    }

    /**
     * Schedules the next task for this stripe.  Should only be
     * called if active == null or if we are finished executing
     * the currently active task.
     */
    private void scheduleNext() {
      lock.lock();
      try {
        if ((active = tasks.poll()) != null) {
          executor.submit(active);
          terminating.signalAll();
        }
      } finally {
        lock.unlock();
      }
    }

    /**
     * Schedules the next task for this stripe.  Should only be
     * called if active == null or if we are finished executing
     * the currently active task.
     */
    private void rescheduleCurrent() {
      lock.lock();
      try {
        if (active != null) {
          executor.submit(active);
          terminating.signalAll();
        }
      } finally {
        lock.unlock();
      }
    }

    /**
     * Returns true if the list is empty and there is no task
     * currently executing.
     */
    public boolean isEmpty() {
      lock.lock();
      try {
        return active == null && tasks.isEmpty();
      } finally {
        lock.unlock();
      }
    }

    public String toString() {
      assert lock.isHeldByCurrentThread();
      return "SerialExecutor: active=" + active + ", " + "tasks=" + tasks;
    }
  }
}
