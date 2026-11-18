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
package org.camunda.bpm.model.xml.impl.type.reference;

import org.camunda.bpm.model.xml.impl.type.attribute.AttributeImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * @author Sebastian Menski
 */
public class QNameAttributeReferenceBuilderImpl<T extends ModelElementInstance> extends AttributeReferenceBuilderImpl<T> {

  /**
   * Create a new {@link AttributeReferenceBuilderImpl} from the reference source attribute
   * to the reference target model element instance
   *
   * @param referenceSourceAttribute the reference source attribute
   * @param referenceTargetElement   the reference target model element instance
   */
  public QNameAttributeReferenceBuilderImpl(AttributeImpl<String> referenceSourceAttribute, Class<T> referenceTargetElement) {
    super(referenceSourceAttribute, referenceTargetElement);
    this.attributeReferenceImpl = new QNameAttributeReferenceImpl<T>(referenceSourceAttribute);
  }
}
