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
package io.zeebe.util.metrics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;

public class Metric {
  private static final byte[] EMPTY = new byte[0];
  private static final byte[] DESCRIPTION_PREFIX = "# HELP".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TYPE_PREFIX = "# TYPE".getBytes(StandardCharsets.UTF_8);
  private static final byte[] OPENING_CURLY_BRACE = "{".getBytes(StandardCharsets.UTF_8);
  private static final byte[] CLOSING_CURLY_BRACE = "}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] NEW_LINE = "\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] DOUBLE_QUOTE = "\"".getBytes(StandardCharsets.UTF_8);
  private static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);
  private static final byte[] EQUALS = "=".getBytes(StandardCharsets.UTF_8);
  private static final byte[] WHITESPACE = " ".getBytes(StandardCharsets.UTF_8);

  private static class Label {
    private final byte[] name;
    private final byte[] value;

    Label(String name, String value) {
      this.name = name.getBytes(StandardCharsets.UTF_8);
      this.value = value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
      return new String(name, StandardCharsets.UTF_8)
          + " = "
          + new String(value, StandardCharsets.UTF_8);
    }
  }

  private final AtomicCounter value;
  private final byte[] name;
  private final byte[] type;
  private final byte[] description;
  private final Label[] labels;
  private final Consumer<Metric> onClose;

  public Metric(
      String name,
      String type,
      String description,
      Map<String, String> labels,
      Consumer<Metric> onClose) {
    this.onClose = onClose;
    this.value = new AtomicCounter(new UnsafeBuffer(new byte[BitUtil.SIZE_OF_LONG]), 0);
    this.name = name.getBytes(StandardCharsets.UTF_8);
    this.type = type.getBytes(StandardCharsets.UTF_8);
    this.description = description == null ? EMPTY : description.getBytes(StandardCharsets.UTF_8);
    this.labels = new Label[labels.size()];

    final List<Entry<String, String>> labelSet = new ArrayList<>(labels.entrySet());
    for (int i = 0; i < labelSet.size(); i++) {
      final Entry<String, String> entry = labelSet.get(i);
      this.labels[i] = new Label(entry.getKey(), entry.getValue());
    }
  }

  public long incrementOrdered() {
    return value.incrementOrdered();
  }

  public void setOrdered(long value) {
    this.value.setOrdered(value);
  }

  public long getWeak() {
    return value.getWeak();
  }

  public long get() {
    return value.get();
  }

  public int id() {
    return value.id();
  }

  public long getAndAddOrdered(long increment) {
    return value.getAndAddOrdered(increment);
  }

  public String getName() {
    return new String(name, StandardCharsets.UTF_8);
  }

  public int dump(MutableDirectBuffer buffer, int offset, long now) {
    if (description.length > 0) {
      offset = writeArray(buffer, offset, DESCRIPTION_PREFIX);
      offset = writeArray(buffer, offset, WHITESPACE);
      offset = writeArray(buffer, offset, name);
      offset = writeArray(buffer, offset, WHITESPACE);
      offset = writeArray(buffer, offset, description);
      offset = writeArray(buffer, offset, NEW_LINE);
    }

    offset = writeArray(buffer, offset, TYPE_PREFIX);
    offset = writeArray(buffer, offset, WHITESPACE);
    offset = writeArray(buffer, offset, name);
    offset = writeArray(buffer, offset, WHITESPACE);
    offset = writeArray(buffer, offset, type);
    offset = writeArray(buffer, offset, NEW_LINE);

    offset = writeArray(buffer, offset, name);
    offset = writeArray(buffer, offset, OPENING_CURLY_BRACE);
    for (int i = 0; i < labels.length; i++) {
      if (i != 0) {
        offset = writeArray(buffer, offset, COMMA);
      }
      offset = writeArray(buffer, offset, labels[i].name);
      offset = writeArray(buffer, offset, EQUALS);
      offset = writeArray(buffer, offset, DOUBLE_QUOTE);
      offset = writeArray(buffer, offset, labels[i].value);
      offset = writeArray(buffer, offset, DOUBLE_QUOTE);
    }
    offset = writeArray(buffer, offset, CLOSING_CURLY_BRACE);
    offset = writeArray(buffer, offset, WHITESPACE);
    offset =
        writeArray(buffer, offset, Long.toString(value.get()).getBytes(StandardCharsets.UTF_8));
    offset = writeArray(buffer, offset, WHITESPACE);
    offset = writeArray(buffer, offset, Long.toString(now).getBytes(StandardCharsets.UTF_8));
    offset = writeArray(buffer, offset, NEW_LINE);
    return offset;
  }

  private int writeArray(MutableDirectBuffer buffer, int offset, byte[] array) {
    buffer.putBytes(offset, array);
    return offset + array.length;
  }

  public void close() {
    onClose.accept(this);
  }
}
