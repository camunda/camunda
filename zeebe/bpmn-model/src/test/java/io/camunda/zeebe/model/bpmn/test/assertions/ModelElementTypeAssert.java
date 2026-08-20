/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package io.camunda.zeebe.model.bpmn.test.assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.assertj.core.api.AbstractAssert;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.impl.util.QName;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * @author Sebastian Menski
 */
public class ModelElementTypeAssert
    extends AbstractAssert<ModelElementTypeAssert, ModelElementType> {

  private final String typeName;

  protected ModelElementTypeAssert(final ModelElementType actual) {
    super(actual, ModelElementTypeAssert.class);
    typeName = actual.getTypeName();
  }

  private List<String> getActualAttributeNames() {
    final List<String> actualAttributeNames = new ArrayList<>();
    for (final Attribute<?> attribute : actual.getAttributes()) {
      actualAttributeNames.add(attribute.getAttributeName());
    }
    return actualAttributeNames;
  }

  private Collection<String> getTypeNames(final Collection<ModelElementType> elementTypes) {
    final List<String> typeNames = new ArrayList<>();
    QName qName;
    for (final ModelElementType elementType : elementTypes) {
      qName = new QName(elementType.getTypeNamespace(), elementType.getTypeName());
      typeNames.add(qName.toString());
    }
    return typeNames;
  }

  public ModelElementTypeAssert isAbstract() {
    isNotNull();

    if (!actual.isAbstract()) {
      failWithMessage("Expected element type <%s> to be abstract but was not", typeName);
    }

    return this;
  }

  public ModelElementTypeAssert isNotAbstract() {
    isNotNull();

    if (actual.isAbstract()) {
      failWithMessage("Expected element type <%s> not to be abstract but was", typeName);
    }

    return this;
  }

  public ModelElementTypeAssert extendsType(final ModelElementType baseType) {
    isNotNull();

    final ModelElementType actualBaseType = actual.getBaseType();

    if (!actualBaseType.equals(baseType)) {
      failWithMessage(
          "Expected element type <%s> to extend type <%s> but extends <%s>",
          typeName, actualBaseType.getTypeName(), baseType.getTypeName());
    }

    return this;
  }

  public ModelElementTypeAssert extendsNoType() {
    isNotNull();

    final ModelElementType actualBaseType = actual.getBaseType();

    if (actualBaseType != null) {
      failWithMessage(
          "Expected element type <%s> to not extend any type but extends <%s>",
          typeName, actualBaseType.getTypeName());
    }

    return this;
  }

  public ModelElementTypeAssert hasAttributes() {
    isNotNull();

    final List<Attribute<?>> actualAttributes = actual.getAttributes();

    if (actualAttributes.isEmpty()) {
      failWithMessage("Expected element type <%s> to have attributes but has none", typeName);
    }

    return this;
  }

  public ModelElementTypeAssert hasAttributes(final String... attributeNames) {
    isNotNull();

    final List<String> actualAttributeNames = getActualAttributeNames();

    if (!actualAttributeNames.containsAll(Arrays.asList(attributeNames))) {
      failWithMessage(
          "Expected element type <%s> to have attributes <%s> but has <%s>",
          typeName, attributeNames, actualAttributeNames);
    }

    return this;
  }

  public ModelElementTypeAssert hasNoAttributes() {
    isNotNull();

    final List<String> actualAttributeNames = getActualAttributeNames();

    if (!actualAttributeNames.isEmpty()) {
      failWithMessage(
          "Expected element type <%s> to have no attributes but has <%s>",
          typeName, actualAttributeNames);
    }

    return this;
  }

  public ModelElementTypeAssert hasChildElements() {
    isNotNull();

    final List<ModelElementType> childElementTypes = actual.getChildElementTypes();

    if (childElementTypes.isEmpty()) {
      failWithMessage("Expected element type <%s> to have child elements but has non", typeName);
    }

    return this;
  }

  public ModelElementTypeAssert hasChildElements(final ModelElementType... types) {
    isNotNull();

    final List<ModelElementType> childElementTypes = Arrays.asList(types);
    final List<ModelElementType> actualChildElementTypes = actual.getChildElementTypes();

    if (!actualChildElementTypes.containsAll(childElementTypes)) {
      final Collection<String> typeNames = getTypeNames(childElementTypes);
      final Collection<String> actualTypeNames = getTypeNames(actualChildElementTypes);
      failWithMessage(
          "Expected element type <%s> to have child elements <%s> but has <%s>",
          typeName, typeNames, actualTypeNames);
    }

    return this;
  }

  public ModelElementTypeAssert hasNoChildElements() {
    isNotNull();

    final Collection<String> actualChildElementTypeNames =
        getTypeNames(actual.getChildElementTypes());

    if (!actualChildElementTypeNames.isEmpty()) {
      failWithMessage(
          "Expected element type <%s> to have no child elements but has <%s>",
          typeName, actualChildElementTypeNames);
    }

    return this;
  }

  public ModelElementTypeAssert hasTypeName(final String typeName) {
    isNotNull();

    if (!typeName.equals(this.typeName)) {
      failWithMessage(
          "Expected element type to have name <%s> but was <%s>", typeName, this.typeName);
    }

    return this;
  }

  public ModelElementTypeAssert hasTypeNamespace(final String typeNamespace) {
    isNotNull();

    final String actualTypeNamespace = actual.getTypeNamespace();

    if (!typeNamespace.equals(actualTypeNamespace)) {
      failWithMessage(
          "Expected element type <%s> has type namespace <%s> but was <%s>",
          typeName, typeNamespace, actualTypeNamespace);
    }

    return this;
  }

  public ModelElementTypeAssert hasInstanceType(
      final Class<? extends ModelElementInstance> instanceType) {
    isNotNull();

    final Class<? extends ModelElementInstance> actualInstanceType = actual.getInstanceType();

    if (!instanceType.equals(actualInstanceType)) {
      failWithMessage(
          "Expected element type <%s> has instance type <%s> but was <%s>",
          typeName, instanceType, actualInstanceType);
    }

    return this;
  }

  public ModelElementTypeAssert isExtended() {
    isNotNull();

    final Collection<ModelElementType> actualExtendingTypes = actual.getExtendingTypes();

    if (actualExtendingTypes.isEmpty()) {
      failWithMessage("Expected element type <%s> to be extended by types but was not", typeName);
    }

    return this;
  }

  public ModelElementTypeAssert isExtendedBy(final ModelElementType... types) {
    isNotNull();

    final List<ModelElementType> extendingTypes = Arrays.asList(types);
    final Collection<ModelElementType> actualExtendingTypes = actual.getExtendingTypes();

    if (!actualExtendingTypes.containsAll(extendingTypes)) {
      final Collection<String> typeNames = getTypeNames(extendingTypes);
      final Collection<String> actualTypeNames = getTypeNames(actualExtendingTypes);
      failWithMessage(
          "Expected element type <%s> to be extended by types <%s> but is extended by <%s>",
          typeName, typeNames, actualTypeNames);
    }

    return this;
  }

  public ModelElementTypeAssert isNotExtended() {
    isNotNull();

    final Collection<String> actualExtendingTypeNames = getTypeNames(actual.getExtendingTypes());

    if (!actualExtendingTypeNames.isEmpty()) {
      failWithMessage(
          "Expected element type <%s> to be not extend but is extended by <%s>",
          typeName, actualExtendingTypeNames);
    }

    return this;
  }

  public ModelElementTypeAssert isNotExtendedBy(final ModelElementType... types) {
    isNotNull();

    final List<ModelElementType> notExtendingTypes = Arrays.asList(types);
    final Collection<ModelElementType> actualExtendingTypes = actual.getExtendingTypes();

    final List<ModelElementType> errorTypes = new ArrayList<>();

    for (final ModelElementType notExtendingType : notExtendingTypes) {
      if (actualExtendingTypes.contains(notExtendingType)) {
        errorTypes.add(notExtendingType);
      }
    }

    if (!errorTypes.isEmpty()) {
      final Collection<String> errorTypeNames = getTypeNames(errorTypes);
      final Collection<String> notExtendingTypeNames = getTypeNames(notExtendingTypes);
      failWithMessage(
          "Expected element type <%s> to be not extended by types <%s> but is extended by <%s>",
          typeName, notExtendingTypeNames, errorTypeNames);
    }

    return this;
  }

  public ModelElementTypeAssert isPartOfModel(final Model model) {
    isNotNull();

    final Model actualModel = actual.getModel();

    if (!model.equals(actualModel)) {
      failWithMessage(
          "Expected element type <%s> to be part of model <%s> but was part of <%s>",
          typeName, model.getModelName(), actualModel.getModelName());
    }

    return this;
  }
}
