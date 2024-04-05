/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.function.Supplier;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;

public final class LazySupplier<T> implements Supplier<T> {
  private final LazyInitializer<T> lazyInitializer;

  private LazySupplier(LazyInitializer<T> lazyInitializer) {
    this.lazyInitializer = lazyInitializer;
  }

  public static <T> LazySupplier<T> of(Supplier<T> supplier) {
    return new LazySupplier<>(LazyInitializer.<T>builder().setInitializer(supplier::get).get());
  }

  @Override
  public T get() {
    try {
      return lazyInitializer.get();
    } catch (ConcurrentException e) {
      throw new TasklistRuntimeException(e);
    }
  }
}
