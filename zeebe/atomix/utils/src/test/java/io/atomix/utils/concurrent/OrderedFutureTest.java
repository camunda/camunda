/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Ordered completable future test. */
public class OrderedFutureTest {

  /** Tests ordered completion of future callbacks. */
  @Test
  public void testOrderedCompletion() throws Throwable {
    final CompletableFuture<String> future = new OrderedFuture<>();
    final AtomicInteger order = new AtomicInteger();
    future.whenComplete((r, e) -> assertEquals(1, order.incrementAndGet()));
    future.whenComplete((r, e) -> assertEquals(2, order.incrementAndGet()));
    future.handle(
        (r, e) -> {
          assertEquals(3, order.incrementAndGet());
          assertEquals("foo", r);
          return "bar";
        });
    future.thenRun(() -> assertEquals(3, order.incrementAndGet()));
    future.thenAccept(
        r -> {
          assertEquals(5, order.incrementAndGet());
          assertEquals("foo", r);
        });
    future.thenApply(
        r -> {
          assertEquals(6, order.incrementAndGet());
          assertEquals("foo", r);
          return "bar";
        });
    future.whenComplete(
        (r, e) -> {
          assertEquals(7, order.incrementAndGet());
          assertEquals("foo", r);
        });
    future.complete("foo");
  }

  /** Tests ordered failure of future callbacks. */
  public void testOrderedFailure() throws Throwable {
    final CompletableFuture<String> future = new OrderedFuture<>();
    final AtomicInteger order = new AtomicInteger();
    future.whenComplete((r, e) -> assertEquals(1, order.incrementAndGet()));
    future.whenComplete((r, e) -> assertEquals(2, order.incrementAndGet()));
    future.handle(
        (r, e) -> {
          assertEquals(3, order.incrementAndGet());
          return "bar";
        });
    future.thenRun(() -> fail());
    future.thenAccept(r -> fail());
    future.exceptionally(
        e -> {
          assertEquals(3, order.incrementAndGet());
          return "bar";
        });
    future.completeExceptionally(new RuntimeException("foo"));
  }

  /** Tests calling callbacks that are added after completion. */
  public void testAfterComplete() throws Throwable {
    final CompletableFuture<String> future = new OrderedFuture<>();
    future.whenComplete((result, error) -> assertEquals("foo", result));
    future.complete("foo");
    final AtomicInteger count = new AtomicInteger();
    future.whenComplete(
        (result, error) -> {
          assertEquals("foo", result);
          assertEquals(1, count.incrementAndGet());
        });
    future.thenAccept(
        result -> {
          assertEquals("foo", result);
          assertEquals(2, count.incrementAndGet());
        });
    assertEquals(2, count.get());
  }
}
