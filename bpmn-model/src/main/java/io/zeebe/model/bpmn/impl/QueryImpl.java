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

package io.zeebe.model.bpmn.impl;

import io.zeebe.model.bpmn.BpmnModelException;
import io.zeebe.model.bpmn.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;

/** @author Sebastian Menski */
public class QueryImpl<T extends ModelElementInstance> implements Query<T> {

  private final Collection<T> collection;

  public QueryImpl(final Collection<T> collection) {
    this.collection = collection;
  }

  public Stream<T> stream() {
    return collection.stream();
  }

  @Override
  public List<T> list() {
    return new ArrayList<T>(collection);
  }

  @Override
  public int count() {
    return collection.size();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends ModelElementInstance> Query<V> filterByType(
      final ModelElementType elementType) {
    final Class<V> elementClass = (Class<V>) elementType.getInstanceType();
    return filterByType(elementClass);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends ModelElementInstance> Query<V> filterByType(final Class<V> elementClass) {
    final List<V> filtered = new ArrayList<V>();
    for (final T instance : collection) {
      if (elementClass.isAssignableFrom(instance.getClass())) {
        filtered.add((V) instance);
      }
    }
    return new QueryImpl<V>(filtered);
  }

  @Override
  public T singleResult() {
    if (collection.size() == 1) {
      return collection.iterator().next();
    } else {
      throw new BpmnModelException(
          "Collection expected to have <1> entry but has <" + collection.size() + ">");
    }
  }
}
