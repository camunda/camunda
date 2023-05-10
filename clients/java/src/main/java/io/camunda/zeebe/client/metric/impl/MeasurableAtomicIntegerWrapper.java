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
package io.camunda.zeebe.client.metric.impl;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MeasurableAtomicIntegerWrapper {

  private final AtomicInteger value;
  private final Consumer<Integer> measureFunction;

  public MeasurableAtomicIntegerWrapper(
      final AtomicInteger value, final Consumer<Integer> measureFunction) {
    this.value = value;
    this.measureFunction = measureFunction;
    initialMeasure();
  }

  private void initialMeasure() {
    measureFunction.accept(get());
  }

  public int get() {
    return value.get();
  }

  public int addAndGet(int delta) {
    final int result = value.addAndGet(delta);
    measureFunction.accept(result);
    return result;
  }

  public int decrementAndGet() {
    final int result = value.decrementAndGet();
    measureFunction.accept(result);
    return result;
  }
}
