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
package io.zeebe.util;

import java.util.Arrays;

public enum ByteUnit {
  BYTES(0, ""),
  KILOBYTES(1, "K"),
  MEGABYTES(2, "M"),
  GIGABYTES(3, "G");

  private final double unitFactor;
  private final String metric;

  ByteUnit(double factor, String metric) {
    this.unitFactor = factor;
    this.metric = metric;
  }

  public long toBytes(long value) {
    final double methodFactor = 0;
    return calculateValue(value, methodFactor);
  }

  public long toKilobytes(long value) {
    final double methodFactor = 1;
    return calculateValue(value, methodFactor);
  }

  public long toMegabytes(long value) {
    final double methodFactor = 2;
    return calculateValue(value, methodFactor);
  }

  public long toGigabytes(long value) {
    final double methodFactor = 3;
    return calculateValue(value, methodFactor);
  }

  private long calculateValue(long value, double methodFactor) {
    if (unitFactor < methodFactor) {
      return value / (long) Math.pow(1024, methodFactor - unitFactor);
    } else if (unitFactor == methodFactor) {
      return value;
    } else {
      return value * (long) Math.pow(1024, unitFactor - methodFactor);
    }
  }

  public static ByteUnit getUnit(String unitString) {
    return Arrays.stream(ByteUnit.values())
        .filter(unit -> unit.metric.equalsIgnoreCase(unitString))
        .findAny()
        .orElse(BYTES);
  }

  public String metric() {
    return metric;
  }
}
