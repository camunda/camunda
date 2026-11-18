/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.xml.type.child;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollectionBuilder;

/**
 * @author Daniel Meyer
 *
 */
public interface ChildElementCollectionBuilder<T extends ModelElementInstance> {

  ChildElementCollectionBuilder<T> immutable();

  ChildElementCollectionBuilder<T> required();

  ChildElementCollectionBuilder<T> minOccurs(int i);

  ChildElementCollectionBuilder<T> maxOccurs(int i);

  ChildElementCollection<T> build();

  <V extends ModelElementInstance> ElementReferenceCollectionBuilder<V,T> qNameElementReferenceCollection(Class<V> referenceTargetType);

  <V extends ModelElementInstance> ElementReferenceCollectionBuilder<V, T> idElementReferenceCollection(Class<V> referenceTargetType);

  <V extends ModelElementInstance> ElementReferenceCollectionBuilder<V, T> idsElementReferenceCollection(Class<V> referenceTargetType);

  <V extends ModelElementInstance> ElementReferenceCollectionBuilder<V, T> uriElementReferenceCollection(Class<V> referenceTargetType);

}
