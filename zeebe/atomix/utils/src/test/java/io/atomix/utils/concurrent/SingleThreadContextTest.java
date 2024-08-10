/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.concurrent;

import static io.atomix.utils.concurrent.Threads.namedThreads;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleThreadContextTest {

  private final Logger log = LoggerFactory.getLogger("thread");
  private Consumer<Throwable> exceptionHandler;
  private final SingleThreadContext threadContext =
      new SingleThreadContext(namedThreads("test", log), e -> exceptionHandler.accept(e));

  @Test
  public void shouldInvokeHandlerOnException() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    exceptionHandler = e -> latch.countDown();

    // when
    threadContext.execute(
        () -> {
          throw new RuntimeException();
        });

    latch.await(2, TimeUnit.SECONDS);

    // then
    assertEquals(0, latch.getCount());
  }
}
