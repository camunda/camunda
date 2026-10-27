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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Ordered completable future test. */
public class OrderedFutureTest {

  /** Tests ordered completion of future callbacks. */
  @Test
  public void testOrderedCompletion() throws Throwable {
    final CompletableFuture<String> future = new OrderedFuture<>();
    final AtomicInteger order = new AtomicInteger();
    future.whenComplete((r, e) -> assertThat(order.incrementAndGet()).isEqualTo(1));
    future.whenComplete((r, e) -> assertThat(order.incrementAndGet()).isEqualTo(2));
    future.handle(
        (r, e) -> {
          assertThat(order.incrementAndGet()).isEqualTo(3);
          assertThat(r).isEqualTo("foo");
          return "bar";
        });
    future.thenRun(() -> assertThat(order.incrementAndGet()).isEqualTo(3));
    future.thenAccept(
        r -> {
          assertThat(order.incrementAndGet()).isEqualTo(5);
          assertThat(r).isEqualTo("foo");
        });
    future.thenApply(
        r -> {
          assertThat(order.incrementAndGet()).isEqualTo(6);
          assertThat(r).isEqualTo("foo");
          return "bar";
        });
    future.whenComplete(
        (r, e) -> {
          assertThat(order.incrementAndGet()).isEqualTo(7);
          assertThat(r).isEqualTo("foo");
        });
    future.complete("foo");
  }

  /** Tests ordered failure of future callbacks. */
  public void testOrderedFailure() throws Throwable {
    final CompletableFuture<String> future = new OrderedFuture<>();
    final AtomicInteger order = new AtomicInteger();
    future.whenComplete((r, e) -> assertThat(order.incrementAndGet()).isEqualTo(1));
    future.whenComplete((r, e) -> assertThat(order.incrementAndGet()).isEqualTo(2));
    future.handle(
        (r, e) -> {
          assertThat(order.incrementAndGet()).isEqualTo(3);
          return "bar";
        });
    future.thenRun(() -> Assertions.fail());
    future.thenAccept(r -> Assertions.fail());
    future.exceptionally(
        e -> {
          assertThat(order.incrementAndGet()).isEqualTo(3);
          return "bar";
        });
    future.completeExceptionally(new RuntimeException("foo"));
  }

  /** Tests calling callbacks that are added after completion. */
  public void testAfterComplete() throws Throwable {
    final CompletableFuture<String> future = new OrderedFuture<>();
    future.whenComplete((result, error) -> assertThat(result).isEqualTo("foo"));
    future.complete("foo");
    final AtomicInteger count = new AtomicInteger();
    future.whenComplete(
        (result, error) -> {
          assertThat(result).isEqualTo("foo");
          assertThat(count.incrementAndGet()).isEqualTo(1);
        });
    future.thenAccept(
        result -> {
          assertThat(result).isEqualTo("foo");
          assertThat(count.incrementAndGet()).isEqualTo(2);
        });
    assertThat(count.get()).isEqualTo(2);
  }
}
