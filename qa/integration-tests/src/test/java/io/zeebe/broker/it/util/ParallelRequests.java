/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class ParallelRequests {
  protected List<FutureTask<?>> tasks = new ArrayList<>();

  public <T> SilentFuture<T> submitRequest(Callable<T> request) {
    final FutureTask<T> futureTask = new FutureTask<>(request);
    tasks.add(futureTask);
    return new SilentFuture<>(futureTask);
  }

  public void execute() {
    final List<Thread> threads = new ArrayList<>();

    for (FutureTask<?> task : tasks) {
      threads.add(new Thread(task));
    }

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static ParallelRequests prepare() {
    return new ParallelRequests();
  }

  public static class SilentFuture<T> {
    Future<T> future;

    public SilentFuture(Future<T> future) {
      this.future = future;
    }

    public T get() {
      try {
        return future.get();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        // ignore
        e.printStackTrace();
        return null;
      }
    }
  }
}
