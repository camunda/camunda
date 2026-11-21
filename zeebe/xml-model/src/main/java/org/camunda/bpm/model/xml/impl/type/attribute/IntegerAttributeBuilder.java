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
package org.camunda.bpm.model.xml.impl.type.attribute;

import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;

/**
 * @author Sebastian Menski
 */
public class IntegerAttributeBuilder extends AttributeBuilderImpl<Integer> {

  public IntegerAttributeBuilder(String attributeName, ModelElementTypeImpl modelType) {
    super(attributeName, modelType, new IntegerAttribute(modelType));
  }

  @Override
  public IntegerAttributeBuilder namespace(String namespaceUri) {
    return (IntegerAttributeBuilder) super.namespace(namespaceUri);
  }

  @Override
  public IntegerAttributeBuilder defaultValue(Integer defaultValue) {
    return (IntegerAttributeBuilder) super.defaultValue(defaultValue);
  }

  @Override
  public IntegerAttributeBuilder required() {
    return (IntegerAttributeBuilder) super.required();
  }

  @Override
  public IntegerAttributeBuilder idAttribute() {
    return (IntegerAttributeBuilder) super.idAttribute();
  }
}
