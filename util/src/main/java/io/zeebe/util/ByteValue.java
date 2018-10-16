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

import static io.zeebe.util.ByteUnit.BYTES;
import static io.zeebe.util.ByteUnit.KILOBYTES;
import static io.zeebe.util.ByteUnit.MEGABYTES;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ByteValue {
  private static final Pattern PATTERN =
      Pattern.compile("(\\d+)([K|M|G]?)", Pattern.CASE_INSENSITIVE);

  private final ByteUnit unit;
  private final long value;

  public ByteValue(long value, ByteUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public static ByteValue ofBytes(long value) {
    return new ByteValue(value, ByteUnit.BYTES);
  }

  public static ByteValue ofKilobytes(long value) {
    return new ByteValue(value, ByteUnit.KILOBYTES);
  }

  public static ByteValue ofMegabytes(long value) {
    return new ByteValue(value, ByteUnit.MEGABYTES);
  }

  public static ByteValue ofGigabytes(long value) {
    return new ByteValue(value, ByteUnit.GIGABYTES);
  }

  public ByteValue(String humanReadable) {
    final Matcher matcher = PATTERN.matcher(humanReadable);

    if (!matcher.matches()) {
      final String err =
          String.format(
              "Illegal byte value '%s'. Must match '%s'. Valid examples: 100M, 4K, ...",
              humanReadable, PATTERN.pattern());
      throw new IllegalArgumentException(err);
    }

    final String valueString = matcher.group(1);
    value = Long.parseLong(valueString);

    final String unitString = matcher.group(2).toUpperCase();

    unit = ByteUnit.getUnit(unitString);
  }

  public ByteUnit getUnit() {
    return unit;
  }

  public long getValue() {
    return value;
  }

  public long toBytes() {
    return unit.toBytes(value);
  }

  public ByteValue toBytesValue() {
    return new ByteValue(unit.toBytes(value), BYTES);
  }

  public ByteValue toKilobytesValue() {
    return new ByteValue(unit.toKilobytes(value), KILOBYTES);
  }

  public ByteValue toMegabytesValue() {
    return new ByteValue(unit.toMegabytes(value), MEGABYTES);
  }

  public ByteValue toGigabytesValue() {
    return new ByteValue(unit.toGigabytes(value), ByteUnit.GIGABYTES);
  }

  @Override
  public String toString() {
    return String.format("%d%s", value, unit.metric());
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((unit == null) ? 0 : unit.hashCode());
    result = prime * result + (int) (value ^ (value >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ByteValue other = (ByteValue) obj;
    if (unit != other.unit) {
      return false;
    }
    if (value != other.value) {
      return false;
    }
    return true;
  }
}
