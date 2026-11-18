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
package org.camunda.bpm.model.xml.impl.type.child;

import org.camunda.bpm.model.xml.impl.type.reference.ElementReferenceBuilderImpl;
import org.camunda.bpm.model.xml.impl.type.reference.QNameElementReferenceBuilderImpl;
import org.camunda.bpm.model.xml.impl.type.reference.UriElementReferenceBuilderImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceBuilder;

/**
 * @author Daniel Meyer
 *
 */
public class ChildElementBuilderImpl<T extends ModelElementInstance> extends ChildElementCollectionBuilderImpl<T> implements ChildElementBuilder<T> {

  public ChildElementBuilderImpl(Class<T> childElementTypeClass, ModelElementType parentElementType) {
    super(childElementTypeClass, parentElementType);
  }

  @Override
  protected ChildElementCollectionImpl<T> createCollectionInstance() {
    return new ChildElementImpl<T>(childElementType, parentElementType);
  }

  public ChildElementBuilder<T> immutable() {
    super.immutable();
    return this;
  }

  public ChildElementBuilder<T> required() {
    super.required();
    return this;
  }

  public ChildElementBuilder<T> minOccurs(int i) {
    super.minOccurs(i);
    return this;
  }

  public ChildElementBuilder<T> maxOccurs(int i) {
    super.maxOccurs(i);
    return this;
  }

  public ChildElement<T> build() {
    return (ChildElement<T>) super.build();
  }

  public <V extends ModelElementInstance> ElementReferenceBuilder<V, T> qNameElementReference(Class<V> referenceTargetType) {
    ChildElementImpl<T> child = (ChildElementImpl<T>) build();
    QNameElementReferenceBuilderImpl<V,T> builder = new QNameElementReferenceBuilderImpl<V, T>(childElementType, referenceTargetType, child);
    setReferenceBuilder(builder);
    return builder;
  }

  public <V extends ModelElementInstance> ElementReferenceBuilder<V, T> idElementReference(Class<V> referenceTargetType) {
    ChildElementImpl<T> child = (ChildElementImpl<T>) build();
    ElementReferenceBuilderImpl<V, T> builder = new ElementReferenceBuilderImpl<V, T>(childElementType, referenceTargetType, child);
    setReferenceBuilder(builder);
    return builder;
  }

  public <V extends ModelElementInstance> ElementReferenceBuilder<V, T> uriElementReference(Class<V> referenceTargetType) {
    ChildElementImpl<T> child = (ChildElementImpl<T>) build();
    ElementReferenceBuilderImpl<V, T> builder = new UriElementReferenceBuilderImpl<V, T>(childElementType, referenceTargetType, child);
    setReferenceBuilder(builder);
    return builder;
  }

}
