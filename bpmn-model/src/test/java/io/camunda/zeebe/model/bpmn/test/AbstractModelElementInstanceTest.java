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

package io.camunda.zeebe.model.bpmn.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.xml.test.assertions.ModelAssertions.assertThat;
import static org.junit.Assert.fail;

import java.util.Collection;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;
import org.camunda.bpm.model.xml.impl.util.ModelTypeException;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.test.GetModelElementTypeRule;
import org.camunda.bpm.model.xml.test.assertions.AttributeAssert;
import org.camunda.bpm.model.xml.test.assertions.ChildElementAssert;
import org.camunda.bpm.model.xml.test.assertions.ModelElementTypeAssert;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.Test;
import org.w3c.dom.DOMException;

public abstract class AbstractModelElementInstanceTest {

  public static ModelInstance modelInstance;
  public static Model model;
  public static ModelElementType modelElementType;

  public static void initModelElementType(final GetModelElementTypeRule modelElementTypeRule) {
    modelInstance = modelElementTypeRule.getModelInstance();
    model = modelElementTypeRule.getModel();
    modelElementType = modelElementTypeRule.getModelElementType();
    assertThat(modelInstance).isNotNull();
    assertThat(model).isNotNull();
    assertThat(modelElementType).isNotNull();
  }

  public abstract String getDefaultNamespace();

  public abstract TypeAssumption getTypeAssumption();

  public abstract Collection<ChildElementAssumption> getChildElementAssumptions();

  public abstract Collection<AttributeAssumption> getAttributesAssumptions();

  public ModelElementTypeAssert assertThatType() {
    return assertThat(modelElementType);
  }

  public AttributeAssert assertThatAttribute(final String attributeName) {
    return assertThat(modelElementType.getAttribute(attributeName));
  }

  public ChildElementAssert assertThatChildElement(final ModelElementType childElementType) {
    final ModelElementTypeImpl modelElementTypeImpl = (ModelElementTypeImpl) modelElementType;
    return assertThat(modelElementTypeImpl.getChildElementCollection(childElementType));
  }

  public ModelElementType getType(final Class<? extends ModelElementInstance> instanceClass) {
    return model.getType(instanceClass);
  }

  @Test
  public void testType() {
    assertThatType().isPartOfModel(model);

    final TypeAssumption assumption = getTypeAssumption();
    assertThatType().hasTypeNamespace(assumption.namespaceUri);

    if (assumption.isAbstract) {
      assertThatType().isAbstract();
    } else {
      assertThatType().isNotAbstract();
    }
    if (assumption.extendsType == null) {
      assertThatType().extendsNoType();
    } else {
      assertThatType().extendsType(assumption.extendsType);
    }

    if (assumption.isAbstract) {
      try {
        modelInstance.newInstance(modelElementType);
        fail("Element type " + modelElementType.getTypeName() + " is abstract.");
      } catch (final DOMException e) {
        // expected exception
      } catch (final ModelTypeException e) {
        // expected exception
      } catch (final Exception e) {
        fail("Unexpected exception " + e.getMessage());
      }
    } else {
      final ModelElementInstance modelElementInstance = modelInstance.newInstance(modelElementType);
      assertThat(modelElementInstance).isNotNull();
    }
  }

  @Test
  public void testChildElements() {
    final Collection<ChildElementAssumption> childElementAssumptions = getChildElementAssumptions();
    if (childElementAssumptions == null) {
      assertThatType().hasNoChildElements();
    } else {
      assertThat(modelElementType.getChildElementTypes().size())
          .isEqualTo(childElementAssumptions.size());
      for (final ChildElementAssumption assumption : childElementAssumptions) {
        assertThatType().hasChildElements(assumption.childElementType);
        if (assumption.namespaceUri != null) {
          assertThat(assumption.childElementType).hasTypeNamespace(assumption.namespaceUri);
        }
        assertThatChildElement(assumption.childElementType)
            .occursMinimal(assumption.minOccurs)
            .occursMaximal(assumption.maxOccurs);
      }
    }
  }

  @Test
  public void testAttributes() {
    final Collection<AttributeAssumption> attributesAssumptions = getAttributesAssumptions();
    if (attributesAssumptions == null) {
      assertThatType().hasNoAttributes();
    } else {
      assertThat(attributesAssumptions).hasSameSizeAs(modelElementType.getAttributes());
      for (final AttributeAssumption assumption : attributesAssumptions) {
        assertThatType().hasAttributes(assumption.attributeName);
        final AttributeAssert attributeAssert = assertThatAttribute(assumption.attributeName);

        attributeAssert.hasOwningElementType(modelElementType);

        if (assumption.namespace != null) {
          attributeAssert.hasNamespaceUri(assumption.namespace);
        } else {
          attributeAssert.hasNoNamespaceUri();
        }

        if (assumption.isIdAttribute) {
          attributeAssert.isIdAttribute();
        } else {
          attributeAssert.isNotIdAttribute();
        }

        if (assumption.isRequired) {
          attributeAssert.isRequired();
        } else {
          attributeAssert.isOptional();
        }

        if (assumption.defaultValue == null) {
          attributeAssert.hasNoDefaultValue();
        } else {
          attributeAssert.hasDefaultValue(assumption.defaultValue);
        }
      }
    }
  }

  protected class TypeAssumption {

    public final String namespaceUri;
    public final ModelElementType extendsType;
    public final boolean isAbstract;

    public TypeAssumption(final boolean isAbstract) {
      this(getDefaultNamespace(), isAbstract);
    }

    public TypeAssumption(final String namespaceUri, final boolean isAbstract) {
      this(namespaceUri, null, isAbstract);
    }

    public TypeAssumption(
        final Class<? extends ModelElementInstance> extendsType, final boolean isAbstract) {
      this(getDefaultNamespace(), extendsType, isAbstract);
    }

    public TypeAssumption(
        final String namespaceUri,
        final Class<? extends ModelElementInstance> extendsType,
        final boolean isAbstract) {
      this.namespaceUri = namespaceUri;
      this.extendsType = model.getType(extendsType);
      this.isAbstract = isAbstract;
    }
  }

  protected class ChildElementAssumption {

    public final String namespaceUri;
    public final ModelElementType childElementType;
    public final int minOccurs;
    public final int maxOccurs;

    public ChildElementAssumption(final Class<? extends ModelElementInstance> childElementType) {
      this(childElementType, 0, -1);
    }

    public ChildElementAssumption(
        final String namespaceUri, final Class<? extends ModelElementInstance> childElementType) {
      this(namespaceUri, childElementType, 0, -1);
    }

    public ChildElementAssumption(
        final Class<? extends ModelElementInstance> childElementType, final int minOccurs) {
      this(childElementType, minOccurs, -1);
    }

    public ChildElementAssumption(
        final String namespaceUri,
        final Class<? extends ModelElementInstance> childElementType,
        final int minOccurs) {
      this(namespaceUri, childElementType, minOccurs, -1);
    }

    public ChildElementAssumption(
        final Class<? extends ModelElementInstance> childElementType,
        final int minOccurs,
        final int maxOccurs) {
      this(getDefaultNamespace(), childElementType, minOccurs, maxOccurs);
    }

    public ChildElementAssumption(
        final String namespaceUri,
        final Class<? extends ModelElementInstance> childElementType,
        final int minOccurs,
        final int maxOccurs) {
      this.namespaceUri = namespaceUri;
      this.childElementType = model.getType(childElementType);
      this.minOccurs = minOccurs;
      this.maxOccurs = maxOccurs;
    }
  }

  protected class AttributeAssumption {

    public final String attributeName;
    public final String namespace;
    public final boolean isIdAttribute;
    public final boolean isRequired;
    public final Object defaultValue;

    public AttributeAssumption(final String attributeName) {
      this(attributeName, false, false);
    }

    public AttributeAssumption(final String namespace, final String attributeName) {
      this(namespace, attributeName, false, false);
    }

    public AttributeAssumption(final String attributeName, final boolean isIdAttribute) {
      this(attributeName, isIdAttribute, false);
    }

    public AttributeAssumption(
        final String namespace, final String attributeName, final boolean isIdAttribute) {
      this(namespace, attributeName, isIdAttribute, false);
    }

    public AttributeAssumption(
        final String attributeName, final boolean isIdAttribute, final boolean isRequired) {
      this(attributeName, isIdAttribute, isRequired, null);
    }

    public AttributeAssumption(
        final String namespace,
        final String attributeName,
        final boolean isIdAttribute,
        final boolean isRequired) {
      this(namespace, attributeName, isIdAttribute, isRequired, null);
    }

    public AttributeAssumption(
        final String attributeName,
        final boolean isIdAttribute,
        final boolean isRequired,
        final Object defaultValue) {
      this(null, attributeName, isIdAttribute, isRequired, defaultValue);
    }

    public AttributeAssumption(
        final String namespace,
        final String attributeName,
        final boolean isIdAttribute,
        final boolean isRequired,
        final Object defaultValue) {
      this.attributeName = attributeName;
      this.namespace = namespace;
      this.isIdAttribute = isIdAttribute;
      this.isRequired = isRequired;
      this.defaultValue = defaultValue;
    }
  }
}
