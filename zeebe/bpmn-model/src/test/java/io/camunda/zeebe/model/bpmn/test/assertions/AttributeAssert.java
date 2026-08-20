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

import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.AbstractAssert;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.Reference;

/**
 * @author Sebastian Menski
 */
public class AttributeAssert extends AbstractAssert<AttributeAssert, Attribute<?>> {

  private final String attributeName;

  protected AttributeAssert(final Attribute<?> actual) {
    super(actual, AttributeAssert.class);
    attributeName = actual.getAttributeName();
  }

  public AttributeAssert isRequired() {
    isNotNull();

    if (!actual.isRequired()) {
      failWithMessage("Expected attribute <%s> to be required but was not", attributeName);
    }

    return this;
  }

  public AttributeAssert isOptional() {
    isNotNull();

    if (actual.isRequired()) {
      failWithMessage("Expected attribute <%s> to be optional but was required", attributeName);
    }

    return this;
  }

  public AttributeAssert isIdAttribute() {
    isNotNull();

    if (!actual.isIdAttribute()) {
      failWithMessage("Expected attribute <%s> to be an ID attribute but was not", attributeName);
    }

    return this;
  }

  public AttributeAssert isNotIdAttribute() {
    isNotNull();

    if (actual.isIdAttribute()) {
      failWithMessage("Expected attribute <%s> to be not an ID attribute but was", attributeName);
    }

    return this;
  }

  public AttributeAssert hasDefaultValue(final Object defaultValue) {
    isNotNull();

    final Object actualDefaultValue = actual.getDefaultValue();

    if (!defaultValue.equals(actualDefaultValue)) {
      failWithMessage(
          "Expected attribute <%s> to have default value <%s> but was <%s>",
          attributeName, defaultValue, actualDefaultValue);
    }

    return this;
  }

  public AttributeAssert hasNoDefaultValue() {
    isNotNull();

    final Object actualDefaultValue = actual.getDefaultValue();

    if (actualDefaultValue != null) {
      failWithMessage(
          "Expected attribute <%s> to have no default value but was <%s>",
          attributeName, actualDefaultValue);
    }

    return this;
  }

  public AttributeAssert hasOwningElementType(final ModelElementType owningElementType) {
    isNotNull();

    final ModelElementType actualOwningElementType = actual.getOwningElementType();

    if (!owningElementType.equals(actualOwningElementType)) {
      failWithMessage(
          "Expected attribute <%s> to have owning element type <%s> but was <%s>",
          attributeName, owningElementType, actualOwningElementType);
    }

    return this;
  }

  public AttributeAssert hasValue(final ModelElementInstance modelElementInstance) {
    isNotNull();

    final Object actualValue = actual.getValue(modelElementInstance);

    if (actualValue == null) {
      failWithMessage("Expected attribute <%s> to have a value but has not", attributeName);
    }

    return this;
  }

  public AttributeAssert hasValue(
      final ModelElementInstance modelElementInstance, final Object value) {
    isNotNull();

    final Object actualValue = actual.getValue(modelElementInstance);

    if (!value.equals(actualValue)) {
      failWithMessage(
          "Expected attribute <%s> to have value <%s> but was <%s>",
          attributeName, value, actualValue);
    }

    return this;
  }

  public AttributeAssert hasNoValue(final ModelElementInstance modelElementInstance) {
    isNotNull();

    final Object actualValue = actual.getValue(modelElementInstance);

    if (actualValue != null) {
      failWithMessage(
          "Expected attribute <%s> to have no value but was <%s>", attributeName, actualValue);
    }

    return this;
  }

  public AttributeAssert hasAttributeName(final String attributeName) {
    isNotNull();

    if (!attributeName.equals(this.attributeName)) {
      failWithMessage(
          "Expected attribute to have attribute name <%s> but was <%s>",
          attributeName, this.attributeName);
    }

    return this;
  }

  public AttributeAssert hasNamespaceUri(final String namespaceUri) {
    isNotNull();

    final String actualNamespaceUri1 = actual.getNamespaceUri();

    if (!namespaceUri.equals(actualNamespaceUri1)) {
      failWithMessage(
          "Expected attribute <%s> to have namespace URI <%s> but was <%s>",
          attributeName, namespaceUri, actualNamespaceUri1);
    }

    return this;
  }

  public AttributeAssert hasNoNamespaceUri() {
    isNotNull();

    final String actualNamespaceUri = actual.getNamespaceUri();

    if (actualNamespaceUri != null) {
      failWithMessage(
          "Expected attribute <%s> to have no namespace URI but was <%s>",
          attributeName, actualNamespaceUri);
    }

    return this;
  }

  public AttributeAssert hasIncomingReferences() {
    isNotNull();

    final List<Reference<?>> actualIncomingReferences = actual.getIncomingReferences();

    if (actualIncomingReferences.isEmpty()) {
      failWithMessage(
          "Expected attribute <%s> to have incoming references but has not", attributeName);
    }

    return this;
  }

  public AttributeAssert hasIncomingReferences(final Reference<?>... references) {
    isNotNull();

    final List<Reference<?>> incomingReferences = Arrays.asList(references);
    final List<Reference<?>> actualIncomingReferences = actual.getIncomingReferences();

    if (!actualIncomingReferences.containsAll(incomingReferences)) {
      failWithMessage(
          "Expected attribute <%s> to have incoming references <%s> but has <%s>",
          attributeName, incomingReferences, actualIncomingReferences);
    }

    return this;
  }

  public AttributeAssert hasNoIncomingReferences() {
    isNotNull();

    final List<Reference<?>> actualIncomingReferences = actual.getIncomingReferences();

    if (!actualIncomingReferences.isEmpty()) {
      failWithMessage(
          "Expected attribute <%s> to have no incoming references but has <%s>",
          attributeName, actualIncomingReferences);
    }

    return this;
  }

  public AttributeAssert hasOutgoingReferences() {
    isNotNull();

    final List<Reference<?>> actualOutgoingReferences = actual.getOutgoingReferences();

    if (actualOutgoingReferences.isEmpty()) {
      failWithMessage(
          "Expected attribute <%s> to have outgoing references but has not", attributeName);
    }

    return this;
  }

  public AttributeAssert hasOutgoingReferences(final Reference<?>... references) {
    isNotNull();

    final List<Reference<?>> outgoingReferences = Arrays.asList(references);
    final List<Reference<?>> actualOutgoingReferences = actual.getOutgoingReferences();

    if (!actualOutgoingReferences.containsAll(outgoingReferences)) {
      failWithMessage(
          "Expected attribute <%s> to have outgoing references <%s> but has <%s>",
          attributeName, outgoingReferences, actualOutgoingReferences);
    }

    return this;
  }

  public AttributeAssert hasNoOutgoingReferences() {
    isNotNull();

    final List<Reference<?>> actualOutgoingReferences = actual.getOutgoingReferences();

    if (!actualOutgoingReferences.isEmpty()) {
      failWithMessage(
          "Expected attribute <%s> to have no outgoing references but has <%s>",
          attributeName, actualOutgoingReferences);
    }

    return this;
  }
}
