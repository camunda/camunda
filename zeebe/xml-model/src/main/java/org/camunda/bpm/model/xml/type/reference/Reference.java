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
package org.camunda.bpm.model.xml.type.reference;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

import java.util.Collection;

/**
 *
 * @author Sebastian Menski
 *
 * @param <T> the type of the referenced element
 */
public interface Reference<T extends ModelElementInstance> {

  /**
   * Get the reference identifier which is set in the reference source
   *
   * @param referenceSourceElement the reference source model element instance
   * @return the reference identifier
   */
  String getReferenceIdentifier(ModelElementInstance referenceSourceElement);

  T getReferenceTargetElement(ModelElementInstance modelElement);

  void setReferenceTargetElement(ModelElementInstance referenceSourceElement, T referenceTargetElement);

  Attribute<String> getReferenceTargetAttribute();

  /**
   * Find all reference source element instances of the reference target model element instance
   *
   * @param referenceTargetElement the reference target model element instance
   * @return the collection of all reference source element instances
   */
  Collection<ModelElementInstance> findReferenceSourceElements(ModelElementInstance referenceTargetElement);

  /** @return the {@link ModelElementType} of the source element.
   * */
  ModelElementType getReferenceSourceElementType();
}
