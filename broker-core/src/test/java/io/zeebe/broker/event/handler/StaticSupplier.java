/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event.handler;

import java.util.function.Supplier;

public class StaticSupplier<T> implements Supplier<T> {
  protected T[] values;
  protected int currentValue;

  @SafeVarargs
  public static <T> StaticSupplier<T> returnInOrder(T... args) {
    final StaticSupplier<T> supplier = new StaticSupplier<>();
    supplier.values = args;
    supplier.currentValue = 0;

    return supplier;
  }

  @Override
  public T get() {
    if (currentValue < values.length) {
      final T value = values[currentValue];
      currentValue++;
      return value;
    } else {
      throw new RuntimeException("does not compute");
    }
  }
}
