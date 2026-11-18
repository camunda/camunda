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

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.impl.ModelBuildOperation;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.attribute.AttributeBuilder;


/**
 *
 * @author Daniel Meyer
 *
 */
public abstract class AttributeBuilderImpl<T> implements AttributeBuilder<T>, ModelBuildOperation {

  private final AttributeImpl<T> attribute;
  private final ModelElementTypeImpl modelType;

  AttributeBuilderImpl(String attributeName, ModelElementTypeImpl modelType, AttributeImpl<T> attribute) {
    this.modelType = modelType;
    this.attribute = attribute;
    attribute.setAttributeName(attributeName);
  }

  public AttributeBuilder<T> namespace(String namespaceUri) {
    attribute.setNamespaceUri(namespaceUri);
    return this;
  }

  public AttributeBuilder<T> idAttribute() {
    attribute.setId();
    return this;
  }


  public AttributeBuilder<T> defaultValue(T defaultValue) {
    attribute.setDefaultValue(defaultValue);
    return this;
  }

  public AttributeBuilder<T> required() {
    attribute.setRequired(true);
    return this;
  }

  public Attribute<T> build() {
    modelType.registerAttribute(attribute);
    return attribute;
  }

  public void performModelBuild(Model model) {
    // do nothing
  }

}
