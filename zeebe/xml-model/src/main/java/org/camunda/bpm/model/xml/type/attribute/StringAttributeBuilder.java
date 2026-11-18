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
package org.camunda.bpm.model.xml.type.attribute;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.reference.AttributeReferenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReferenceCollection;
import org.camunda.bpm.model.xml.type.reference.AttributeReferenceCollectionBuilder;

/**
 * @author Sebastian Menski
 *
 */
public interface StringAttributeBuilder extends AttributeBuilder<String> {

  StringAttributeBuilder namespace(String namespaceUri);

  StringAttributeBuilder defaultValue(String defaultValue);

  StringAttributeBuilder required();

  StringAttributeBuilder idAttribute();

  <V extends ModelElementInstance> AttributeReferenceBuilder<V> qNameAttributeReference(Class<V> referenceTargetElement);

  <V extends ModelElementInstance> AttributeReferenceBuilder<V> idAttributeReference(Class<V>  referenceTargetElement);

  @SuppressWarnings("rawtypes")
  <V extends ModelElementInstance> AttributeReferenceCollectionBuilder<V> idAttributeReferenceCollection(Class<V> referenceTargetElement, Class<? extends AttributeReferenceCollection> attributeReferenceCollection);

}
