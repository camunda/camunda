/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import java.util.Iterator;

@SuppressWarnings({"rawtypes"})
public class RecordProcessorMap {
  private final TypedRecordProcessor[] elements;

  private final int valueTypeCardinality;
  private final int intentCardinality;

  private final ValueIterator valueIt = new ValueIterator();

  public <R extends Enum<R>, S extends Enum<S>> RecordProcessorMap() {
    final int recordTypeCardinality = RecordType.class.getEnumConstants().length;
    this.valueTypeCardinality = ValueType.class.getEnumConstants().length;
    this.intentCardinality = Intent.maxCardinality();

    final int cardinality = recordTypeCardinality * valueTypeCardinality * intentCardinality;
    this.elements = new TypedRecordProcessor[cardinality];
  }

  public TypedRecordProcessor get(RecordType key1, ValueType key2, int key3) {
    final int index = mapToIndex(key1, key2, key3);

    if (index >= 0) {
      return elements[index];
    } else {
      return null;
    }
  }

  public void put(RecordType key1, ValueType key2, int key3, TypedRecordProcessor value) {
    final int index = mapToIndex(key1, key2, key3);

    if (index < 0) {
      throw new RuntimeException("Invalid intent value " + key3);
    }

    final TypedRecordProcessor oldElement = elements[index];
    if (oldElement != null) {
      final String exceptionMsg =
          String.format(
              "Expected to have a single processor per intent,"
                  + " got for intent %s duplicate processor %s have already %s",
              Intent.fromProtocolValue(key2, (short) key3),
              value.getClass().getName(),
              oldElement.getClass().getName());
      throw new IllegalStateException(exceptionMsg);
    }

    elements[index] = value;
  }

  public boolean containsKey(RecordType key1, ValueType key2, int key3) {
    final int index = mapToIndex(key1, key2, key3);
    return index >= 0 && elements[index] != null;
  }

  private int mapToIndex(RecordType key1, ValueType key2, int key3) {
    if (key3 >= intentCardinality) {
      return -1;
    }

    return (key1.ordinal() * valueTypeCardinality * intentCardinality)
        + (key2.ordinal() * intentCardinality)
        + key3;
  }

  /** BEWARE: does not detect concurrent modifications and behaves incorrectly in this case */
  public Iterator<TypedRecordProcessor> values() {
    valueIt.init();
    return valueIt;
  }

  private class ValueIterator implements Iterator<TypedRecordProcessor> {
    private int next;

    private void scanToNext() {
      do {
        next++;
      } while (next < elements.length && elements[next] == null);
    }

    public void init() {
      next = -1;
      scanToNext();
    }

    @Override
    public boolean hasNext() {
      return next < elements.length;
    }

    @Override
    public TypedRecordProcessor next() {
      final TypedRecordProcessor element = elements[next];
      scanToNext();
      return element;
    }
  }
}
