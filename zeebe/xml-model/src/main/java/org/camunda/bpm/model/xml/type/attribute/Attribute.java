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
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.reference.Reference;

import java.util.List;

/**
 * @author meyerd
 *
 * @param <T>
 */
public interface Attribute<T> {

  /**
   * returns the value of the attribute.
   *
   * @return the value of the attribute.
   */
  T getValue(ModelElementInstance modelElement);

  /**
   * sets the value of the attribute.
   *
   * @param value the value of the attribute.
   */
  void setValue(ModelElementInstance modelElement, T value);

  /**
   * sets the value of the attribute.
   *
   * @param value the value of the attribute.
   * @param withReferenceUpdate true to update id references in other elements, false otherwise
   */
  void setValue(ModelElementInstance modelElement, T value, boolean withReferenceUpdate);

  T getDefaultValue();

  void removeAttribute(ModelElementInstance modelElement);

  boolean isRequired();

  /**
   * @return the namespaceUri
   */
  String getNamespaceUri();

  /**
   * @return the attributeName
   */
  String getAttributeName();

  boolean isIdAttribute();

  ModelElementType getOwningElementType();

  List<Reference<?>> getIncomingReferences();

  List<Reference<?>> getOutgoingReferences();

}
