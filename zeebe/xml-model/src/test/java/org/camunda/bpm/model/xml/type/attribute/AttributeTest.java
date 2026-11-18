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

import static org.camunda.bpm.model.xml.test.assertions.ModelAssertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.type.attribute.AttributeImpl;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.camunda.bpm.model.xml.testmodel.instance.Animal;
import org.camunda.bpm.model.xml.testmodel.instance.AnimalTest;
import org.camunda.bpm.model.xml.testmodel.instance.Animals;
import org.camunda.bpm.model.xml.testmodel.instance.Bird;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Sebastian Menski
 */
public class AttributeTest extends TestModelTest {

  private Bird tweety;
  private Attribute<String> idAttribute;
  private Attribute<String> nameAttribute;
  private Attribute<String> fatherAttribute;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(AnimalTest.class)};
    return Arrays.asList(models);
  }

  public static Object[] createModel() {
    final TestModelParser modelParser = new TestModelParser();
    final ModelInstance modelInstance = modelParser.getEmptyModel();

    final Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    createBird(modelInstance, "tweety", Gender.Female);

    return new Object[] {Named.of("created", modelInstance)};
  }

  @SuppressWarnings("unchecked")
  private void copyModelInstance(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();

    tweety = modelInstance.getModelElementById("tweety");

    final ModelElementType animalType = modelInstance.getModel().getType(Animal.class);
    idAttribute = (Attribute<String>) animalType.getAttribute("id");
    nameAttribute = (Attribute<String>) animalType.getAttribute("name");
    fatherAttribute = (Attribute<String>) animalType.getAttribute("father");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testOwningElementType(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final ModelElementType animalType = modelInstance.getModel().getType(Animal.class);

    assertThat(idAttribute).hasOwningElementType(animalType);
    assertThat(nameAttribute).hasOwningElementType(animalType);
    assertThat(fatherAttribute).hasOwningElementType(animalType);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetAttributeValue(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String identifier = "new-" + tweety.getId();
    idAttribute.setValue(tweety, identifier);
    assertThat(idAttribute).hasValue(tweety, identifier);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetAttributeValueWithoutUpdateReference(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String identifier = "new-" + tweety.getId();
    idAttribute.setValue(tweety, identifier, false);
    assertThat(idAttribute).hasValue(tweety, identifier);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetDefaultValue(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String defaultName = "default-name";
    assertThat(tweety.getName()).isNull();
    assertThat(nameAttribute).hasNoDefaultValue();

    ((AttributeImpl<String>) nameAttribute).setDefaultValue(defaultName);
    assertThat(nameAttribute).hasDefaultValue(defaultName);
    assertThat(tweety.getName()).isEqualTo(defaultName);

    tweety.setName("not-" + defaultName);
    assertThat(tweety.getName()).isNotEqualTo(defaultName);

    tweety.removeAttribute("name");
    assertThat(tweety.getName()).isEqualTo(defaultName);
    ((AttributeImpl<String>) nameAttribute).setDefaultValue(null);
    assertThat(nameAttribute).hasNoDefaultValue();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRequired(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeAttribute("name");
    assertThat(nameAttribute).isOptional();

    ((AttributeImpl<String>) nameAttribute).setRequired(true);
    assertThat(nameAttribute).isRequired();

    ((AttributeImpl<String>) nameAttribute).setRequired(false);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetNamespaceUri(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String testNamespace = "http://camunda.org/test";

    ((AttributeImpl<String>) idAttribute).setNamespaceUri(testNamespace);
    assertThat(idAttribute).hasNamespaceUri(testNamespace);

    ((AttributeImpl<String>) idAttribute).setNamespaceUri(null);
    assertThat(idAttribute).hasNoNamespaceUri();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testIdAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(idAttribute).isIdAttribute();
    assertThat(nameAttribute).isNotIdAttribute();
    assertThat(fatherAttribute).isNotIdAttribute();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(idAttribute).hasAttributeName("id");
    assertThat(nameAttribute).hasAttributeName("name");
    assertThat(fatherAttribute).hasAttributeName("father");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setName("test");
    assertThat(tweety.getName()).isNotNull();
    assertThat(nameAttribute).hasValue(tweety);

    ((AttributeImpl<String>) nameAttribute).removeAttribute(tweety);
    assertThat(tweety.getName()).isNull();
    assertThat(nameAttribute).hasNoValue(tweety);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testIncomingReferences(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(idAttribute).hasIncomingReferences();
    assertThat(nameAttribute).hasNoIncomingReferences();
    assertThat(fatherAttribute).hasNoIncomingReferences();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testOutgoingReferences(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(idAttribute).hasNoOutgoingReferences();
    assertThat(nameAttribute).hasNoOutgoingReferences();
    assertThat(fatherAttribute).hasOutgoingReferences();
  }
}
