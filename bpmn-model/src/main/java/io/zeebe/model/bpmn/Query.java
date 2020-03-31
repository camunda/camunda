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

package io.zeebe.model.bpmn;

import java.util.List;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;

/** @author Sebastian Menski */
public interface Query<T extends ModelElementInstance> {

  Stream<T> stream();

  List<T> list();

  int count();

  <V extends ModelElementInstance> Query<V> filterByType(ModelElementType elementType);

  <V extends ModelElementInstance> Query<V> filterByType(Class<V> elementClass);

  T singleResult();
}
