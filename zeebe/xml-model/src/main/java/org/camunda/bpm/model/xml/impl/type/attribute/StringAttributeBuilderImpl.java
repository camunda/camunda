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
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.impl.ModelBuildOperation;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;
import org.camunda.bpm.model.xml.impl.type.reference.AttributeReferenceBuilderImpl;
import org.camunda.bpm.model.xml.impl.type.reference.AttributeReferenceCollectionBuilderImpl;
import org.camunda.bpm.model.xml.impl.type.reference.QNameAttributeReferenceBuilderImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.attribute.StringAttributeBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReferenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReferenceCollection;
import org.camunda.bpm.model.xml.type.reference.AttributeReferenceCollectionBuilder;


/**
 *
 * @author Daniel Meyer
 *
 */
public class StringAttributeBuilderImpl extends AttributeBuilderImpl<String> implements StringAttributeBuilder {

  private AttributeReferenceBuilder<?> referenceBuilder;

  public StringAttributeBuilderImpl(String attributeName, ModelElementTypeImpl modelType) {
    super(attributeName, modelType, new StringAttribute(modelType));
  }

  @Override
  public StringAttributeBuilder namespace(String namespaceUri) {
    return (StringAttributeBuilder) super.namespace(namespaceUri);
  }

  @Override
  public StringAttributeBuilder defaultValue(String defaultValue) {
    return (StringAttributeBuilder) super.defaultValue(defaultValue);
  }

  @Override
  public StringAttributeBuilder required() {
    return (StringAttributeBuilder) super.required();
  }

  @Override
  public StringAttributeBuilder idAttribute() {
    return (StringAttributeBuilder) super.idAttribute();
  }

  /**
   * Create a new {@link AttributeReferenceBuilder} for the reference source element instance
   *
   * @param referenceTargetElement the reference target model element instance
   * @return the new attribute reference builder
   */
  public <V extends ModelElementInstance> AttributeReferenceBuilder<V> qNameAttributeReference(Class<V> referenceTargetElement) {
    AttributeImpl<String> attribute = (AttributeImpl<String>) build();
    AttributeReferenceBuilderImpl<V> referenceBuilder = new QNameAttributeReferenceBuilderImpl<V>(attribute, referenceTargetElement);
    setAttributeReference(referenceBuilder);
    return referenceBuilder;
  }

  public <V extends ModelElementInstance> AttributeReferenceBuilder<V> idAttributeReference(Class<V> referenceTargetElement) {
    AttributeImpl<String> attribute = (AttributeImpl<String>) build();
    AttributeReferenceBuilderImpl<V> referenceBuilder = new AttributeReferenceBuilderImpl<V>(attribute, referenceTargetElement);
    setAttributeReference(referenceBuilder);
    return referenceBuilder;
  }

  @SuppressWarnings("rawtypes")
  public <V extends ModelElementInstance> AttributeReferenceCollectionBuilder<V> idAttributeReferenceCollection(Class<V> referenceTargetElement, Class<? extends AttributeReferenceCollection> attributeReferenceCollection) {
    AttributeImpl<String> attribute = (AttributeImpl<String>) build();
    AttributeReferenceCollectionBuilder<V> referenceBuilder = new AttributeReferenceCollectionBuilderImpl<V>(attribute, referenceTargetElement, attributeReferenceCollection);
    setAttributeReference(referenceBuilder);
    return referenceBuilder;
  }

  protected <V extends ModelElementInstance> void setAttributeReference(AttributeReferenceBuilder<V> referenceBuilder) {
    if (this.referenceBuilder != null) {
      throw new ModelException("An attribute cannot have more than one reference");
    }
    this.referenceBuilder = referenceBuilder;
  }


  @Override
  public void performModelBuild(Model model) {
    super.performModelBuild(model);
    if (referenceBuilder != null) {
      ((ModelBuildOperation) referenceBuilder).performModelBuild(model);
    }
  }

}
