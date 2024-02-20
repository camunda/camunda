/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.micrometer.core.instrument.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FutureHelper {
  public static <R> CompletableFuture<R> withTimer(
      Timer timer, Supplier<CompletableFuture<R>> supplier) {
    final var t = Timer.start();
    return supplier.get().whenComplete((response, e) -> t.stop(timer));
  }
}
