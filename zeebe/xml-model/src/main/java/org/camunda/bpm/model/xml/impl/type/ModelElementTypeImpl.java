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
package org.camunda.bpm.model.xml.impl.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.ModelImpl;
import org.camunda.bpm.model.xml.impl.ModelInstanceImpl;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.impl.util.ModelTypeException;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;

/**
 * @author Daniel Meyer
 *
 */
public class ModelElementTypeImpl implements ModelElementType {

  private final ModelImpl model;

  private final String typeName;

  private final Class<? extends ModelElementInstance> instanceType;

  private String typeNamespace;

  private ModelElementTypeImpl baseType;

  private final List<ModelElementType> extendingTypes = new ArrayList<ModelElementType>();

  private final List<Attribute<?>> attributes = new ArrayList<Attribute<?>>();

  private final List<ModelElementType> childElementTypes = new ArrayList<ModelElementType>();

  private final List<ChildElementCollection<?>> childElementCollections = new ArrayList<ChildElementCollection<?>>();

  private ModelTypeInstanceProvider<?> instanceProvider;

  private boolean isAbstract;

  public ModelElementTypeImpl(ModelImpl model, String name, Class<? extends ModelElementInstance> instanceType) {
    this.model = model;
    this.typeName = name;
    this.instanceType = instanceType;
  }

  public ModelElementInstance newInstance(ModelInstance modelInstance) {
    ModelInstanceImpl modelInstanceImpl = (ModelInstanceImpl) modelInstance;
    DomDocument document = modelInstanceImpl.getDocument();
    DomElement domElement = document.createElement(typeNamespace, typeName);
    return newInstance(modelInstanceImpl, domElement);
  }

  public ModelElementInstance newInstance(ModelInstanceImpl modelInstance, DomElement domElement) {
    ModelTypeInstanceContext modelTypeInstanceContext = new ModelTypeInstanceContext(domElement, modelInstance, this);
    return createModelElementInstance(modelTypeInstanceContext);
  }

  public void registerAttribute(Attribute<?> attribute) {
    if (!attributes.contains(attribute)) {
      attributes.add(attribute);
    }
  }

  public void registerChildElementType(ModelElementType childElementType) {
    if (!childElementTypes.contains(childElementType)) {
      childElementTypes.add(childElementType);
    }
  }

  public void registerChildElementCollection(ChildElementCollection<?> childElementCollection) {
    if (!childElementCollections.contains(childElementCollection)) {
      childElementCollections.add(childElementCollection);
    }
  }

  public void registerExtendingType(ModelElementType modelType) {
    if (!extendingTypes.contains(modelType)) {
      extendingTypes.add(modelType);
    }
  }

  protected ModelElementInstance createModelElementInstance(ModelTypeInstanceContext instanceContext) {
    if (isAbstract) {
      throw new ModelTypeException("Model element type " + getTypeName() + " is abstract and no instances can be created.");
    }
    else {
      return instanceProvider.newInstance(instanceContext);
    }
  }

  public final List<Attribute<?>> getAttributes() {
    return attributes;
  }

  public String getTypeName() {
    return typeName;
  }

  public Class<? extends ModelElementInstance> getInstanceType() {
    return instanceType;
  }

  public void setTypeNamespace(String typeNamespace) {
    this.typeNamespace = typeNamespace;
  }

  public String getTypeNamespace() {
    return typeNamespace;
  }

  public void setBaseType(ModelElementTypeImpl baseType) {
    if (this.baseType == null) {
      this.baseType = baseType;
    }
    else if (!this.baseType.equals(baseType)) {
      throw new ModelException("Type can not have multiple base types. " + this.getClass() + " already extends type " + this.baseType.getClass()
          + " and can not also extend type " + baseType.getClass());
    }
  }

  public void setInstanceProvider(ModelTypeInstanceProvider<?> instanceProvider) {
    this.instanceProvider = instanceProvider;
  }
  public boolean isAbstract() {
    return isAbstract;
  }

  public void setAbstract(boolean isAbstract) {
    this.isAbstract = isAbstract;
  }

  public Collection<ModelElementType> getExtendingTypes() {
    return Collections.unmodifiableCollection(extendingTypes);
  }

  public Collection<ModelElementType> getAllExtendingTypes() {
    HashSet<ModelElementType> extendingTypes = new HashSet<ModelElementType>();
    extendingTypes.add(this);
    resolveExtendingTypes(extendingTypes);
    return extendingTypes;
  }

  /**
   * Resolve all types recursively which are extending this type
   *
   * @param allExtendingTypes set of calculated extending types
   */
  public void resolveExtendingTypes(Set<ModelElementType> allExtendingTypes) {
    for(ModelElementType modelElementType : extendingTypes) {
      ModelElementTypeImpl modelElementTypeImpl = (ModelElementTypeImpl) modelElementType;
      if (!allExtendingTypes.contains(modelElementTypeImpl)) {
        allExtendingTypes.add(modelElementType);
        modelElementTypeImpl.resolveExtendingTypes(allExtendingTypes);
      }
    }
  }

  /**
   * Resolve all types which are base types of this type
   *
   * @param baseTypes list of calculated base types
   */
  public void resolveBaseTypes(List<ModelElementType> baseTypes) {
    if (baseType != null) {
      baseTypes.add(baseType);
      baseType.resolveBaseTypes(baseTypes);
    }
  }


  public ModelElementType getBaseType() {
    return baseType;
  }

  public Model getModel() {
    return model;
  }

  public List<ModelElementType> getChildElementTypes() {
    return childElementTypes;
  }

  public List<ModelElementType> getAllChildElementTypes() {
    List<ModelElementType> allChildElementTypes = new ArrayList<ModelElementType>();
    if (baseType != null) {
      allChildElementTypes.addAll(baseType.getAllChildElementTypes());
    }
    allChildElementTypes.addAll(childElementTypes);
    return allChildElementTypes;
  }

  public List<ChildElementCollection<?>> getChildElementCollections() {
    return childElementCollections;
  }

  public List<ChildElementCollection<?>> getAllChildElementCollections() {
    List<ChildElementCollection<?>> allChildElementCollections = new ArrayList<ChildElementCollection<?>>();
    if (baseType != null) {
      allChildElementCollections.addAll(baseType.getAllChildElementCollections());
    }
    allChildElementCollections.addAll(childElementCollections);
    return allChildElementCollections;
  }

  public Collection<ModelElementInstance> getInstances(ModelInstance modelInstance) {
    ModelInstanceImpl modelInstanceImpl = (ModelInstanceImpl) modelInstance;
    DomDocument document = modelInstanceImpl.getDocument();

    List<DomElement> elements = getElementsByNameNs(document, typeNamespace);

    List<ModelElementInstance> resultList = new ArrayList<ModelElementInstance>();
    for (DomElement element : elements) {
      resultList.add(ModelUtil.getModelElement(element, modelInstanceImpl, this));
    }
    return resultList;
  }

  protected List<DomElement> getElementsByNameNs(DomDocument document, String namespaceURI) {
    List<DomElement> elements = document.getElementsByNameNs(namespaceURI, typeName);

    if (elements.isEmpty()) {
      Set<String> alternativeNamespaces = getModel().getAlternativeNamespaces(namespaceURI);

      if (alternativeNamespaces != null)
      {
        Iterator<String> namespaceIt = alternativeNamespaces.iterator();
        while (elements.isEmpty() && namespaceIt.hasNext()) {
          elements = getElementsByNameNs(document, namespaceIt.next());
        }
      }
    }

    return elements;
  }

  /**
   * Test if a element type is a base type of this type. So this type extends the given element type.
   *
   * @param elementType the element type to test
   * @return true if {@code childElementTypeClass} is a base type of this type, else otherwise
   */
  public boolean isBaseTypeOf(ModelElementType elementType) {
    if (this.equals(elementType)) {
      return true;
    }
    else {
      Collection<ModelElementType> baseTypes = ModelUtil.calculateAllBaseTypes(elementType);
      return baseTypes.contains(this);
    }
  }

  /**
   * Returns a list of all attributes, including the attributes of all base types.
   *
   * @return the list of all attributes
   */
  public Collection<Attribute<?>> getAllAttributes() {
    List<Attribute<?>> allAttributes = new ArrayList<Attribute<?>>();
    allAttributes.addAll(getAttributes());
    Collection<ModelElementType> baseTypes = ModelUtil.calculateAllBaseTypes(this);
    for (ModelElementType baseType : baseTypes) {
      allAttributes.addAll(baseType.getAttributes());
    }
    return allAttributes;
  }

  /**
   * Return the attribute for the attribute name
   *
   * @param attributeName the name of the attribute
   * @return the attribute or null if it not exists
   */
  public Attribute<?> getAttribute(String attributeName) {
    for (Attribute<?> attribute : getAllAttributes()) {
      if (attribute.getAttributeName().equals(attributeName)) {
        return attribute;
      }
    }
    return null;
  }

  public ChildElementCollection<?> getChildElementCollection(ModelElementType childElementType) {
    for (ChildElementCollection<?> childElementCollection : getChildElementCollections()) {
      if (childElementType.equals(childElementCollection.getChildElementType(model))) {
        return childElementCollection;
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    int prime = 31;
    int result = 1;
    result = prime * result + ((model == null) ? 0 : model.hashCode());
    result = prime * result + ((typeName == null) ? 0 : typeName.hashCode());
    result = prime * result + ((typeNamespace == null) ? 0 : typeNamespace.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ModelElementTypeImpl other = (ModelElementTypeImpl) obj;
    if (model == null) {
      if (other.model != null) {
        return false;
      }
    } else if (!model.equals(other.model)) {
      return false;
    }
    if (typeName == null) {
      if (other.typeName != null) {
        return false;
      }
    } else if (!typeName.equals(other.typeName)) {
      return false;
    }
    if (typeNamespace == null) {
      if (other.typeNamespace != null) {
        return false;
      }
    } else if (!typeNamespace.equals(other.typeNamespace)) {
      return false;
    }
    return true;
  }

}
