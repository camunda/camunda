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
package org.camunda.bpm.model.xml.testmodel.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.ModelImpl;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelConstants;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author Ronny Bräunlich
 */
public class AlternativeNsTest extends TestModelTest {

  private static final String MECHANICAL_NS = "http://camunda.org/mechanical";
  private static final String YET_ANOTHER_NS = "http://camunda.org/yans";

  public static Collection<Object[]> models() {
    return Collections.singleton(parseModel(AlternativeNsTest.class));
  }

  private void setUp(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();
    final ModelImpl modelImpl = (ModelImpl) modelInstance.getModel();
    modelImpl.declareAlternativeNamespace(MECHANICAL_NS, TestModelConstants.NEWER_NAMESPACE);
    modelImpl.declareAlternativeNamespace(YET_ANOTHER_NS, TestModelConstants.NEWER_NAMESPACE);
  }

  @AfterEach
  public void tearDown() {
    final ModelImpl modelImpl = (ModelImpl) modelInstance.getModel();
    modelImpl.undeclareAlternativeNamespace(MECHANICAL_NS);
    modelImpl.undeclareAlternativeNamespace(YET_ANOTHER_NS);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void getUniqueChildElementByNameNsForAlternativeNs(final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final ModelElementInstance hedwig = modelInstance.getModelElementById("hedwig");
    assertThat(hedwig).isNotNull();
    final ModelElementInstance childElementByNameNs =
        hedwig.getUniqueChildElementByNameNs(TestModelConstants.NEWER_NAMESPACE, "wings");
    assertThat(childElementByNameNs).isNotNull();
    assertThat(childElementByNameNs.getTextContent()).isEqualTo("wusch");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void getUniqueChildElementByNameNsForSecondAlternativeNs(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    // givne
    final ModelElementInstance donald = modelInstance.getModelElementById("donald");

    // when
    final ModelElementInstance childElementByNameNs =
        donald.getUniqueChildElementByNameNs(TestModelConstants.NEWER_NAMESPACE, "wings");

    // then
    assertThat(childElementByNameNs).isNotNull();
    assertThat(childElementByNameNs.getTextContent()).isEqualTo("flappy");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void getChildElementsByTypeForAlternativeNs(final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final ModelElementInstance birdo = modelInstance.getModelElementById("birdo");
    assertThat(birdo).isNotNull();
    final Collection<Wings> elements = birdo.getChildElementsByType(Wings.class);
    assertThat(elements).hasSize(1);
    assertThat(elements.iterator().next().getTextContent()).isEqualTo("zisch");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void getChildElementsByTypeForSecondAlternativeNs(final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    // given
    final ModelElementInstance donald = modelInstance.getModelElementById("donald");

    // when
    final Collection<Wings> elements = donald.getChildElementsByType(Wings.class);

    // then
    assertThat(elements).hasSize(1);
    assertThat(elements.iterator().next().getTextContent()).isEqualTo("flappy");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void getAttributeValueNsForAlternativeNs(final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final Bird plucky = modelInstance.getModelElementById("plucky");
    assertThat(plucky).isNotNull();
    final Boolean extendedWings = plucky.canHazExtendedWings();
    assertThat(extendedWings).isFalse();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void getAttributeValueNsForSecondAlternativeNs(final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    // given
    final Bird donald = modelInstance.getModelElementById("donald");

    // when
    final Boolean extendedWings = donald.canHazExtendedWings();

    // then
    assertThat(extendedWings).isTrue();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void modifyingAttributeWithAlternativeNamespaceKeepsAlternativeNamespace(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final Bird plucky = modelInstance.getModelElementById("plucky");
    assertThat(plucky).isNotNull();
    // validate old value
    final Boolean extendedWings = plucky.canHazExtendedWings();
    assertThat(extendedWings).isFalse();
    // change it
    plucky.setCanHazExtendedWings(true);
    final String attributeValueNs =
        plucky.getAttributeValueNs(MECHANICAL_NS, "canHazExtendedWings");
    assertThat(attributeValueNs).isEqualTo("true");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void modifyingAttributeWithSecondAlternativeNamespaceKeepsSecondAlternativeNamespace(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    // given
    final Bird donald = modelInstance.getModelElementById("donald");

    // when
    donald.setCanHazExtendedWings(false);

    // then
    final String attributeValueNs =
        donald.getAttributeValueNs(YET_ANOTHER_NS, "canHazExtendedWings");
    assertThat(attributeValueNs).isEqualTo("false");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void modifyingAttributeWithNewNamespaceKeepsNewNamespace(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final Bird bird = createBird(modelInstance, "waldo", Gender.Male);
    bird.setCanHazExtendedWings(true);
    final String attributeValueNs =
        bird.getAttributeValueNs(TestModelConstants.NEWER_NAMESPACE, "canHazExtendedWings");
    assertThat(attributeValueNs).isEqualTo("true");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void modifyingElementWithAlternativeNamespaceKeepsAlternativeNamespace(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final Bird birdo = modelInstance.getModelElementById("birdo");
    assertThat(birdo).isNotNull();
    final Wings wings = birdo.getWings();
    assertThat(wings).isNotNull();
    wings.setTextContent("kawusch");

    final List<DomElement> childElementsByNameNs =
        birdo.getDomElement().getChildElementsByNameNs(MECHANICAL_NS, "wings");
    assertThat(childElementsByNameNs).hasSize(1);
    assertThat(childElementsByNameNs.get(0).getTextContent()).isEqualTo("kawusch");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void modifyingElementWithSecondAlternativeNamespaceKeepsSecondAlternativeNamespace(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    // given
    final Bird donald = modelInstance.getModelElementById("donald");
    final Wings wings = donald.getWings();

    // when
    wings.setTextContent("kawusch");

    // then
    final List<DomElement> childElementsByNameNs =
        donald.getDomElement().getChildElementsByNameNs(YET_ANOTHER_NS, "wings");
    assertThat(childElementsByNameNs).hasSize(1);
    assertThat(childElementsByNameNs.get(0).getTextContent()).isEqualTo("kawusch");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void modifyingElementWithNewNamespaceKeepsNewNamespace(
      final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    final Bird bird = createBird(modelInstance, "waldo", Gender.Male);
    bird.setWings(modelInstance.newInstance(Wings.class));

    final List<DomElement> childElementsByNameNs =
        bird.getDomElement().getChildElementsByNameNs(TestModelConstants.NEWER_NAMESPACE, "wings");
    assertThat(childElementsByNameNs).hasSize(1);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void useExistingNamespace(final ModelInstance testModelInstance) {
    setUp(testModelInstance);

    assertThatThereIsNoNewerNamespaceUrl();

    final Bird plucky = modelInstance.getModelElementById("plucky");
    plucky.setAttributeValueNs(MECHANICAL_NS, "canHazExtendedWings", "true");

    final Bird donald = modelInstance.getModelElementById("donald");
    donald.setAttributeValueNs(YET_ANOTHER_NS, "canHazExtendedWings", "false");
    assertThatThereIsNoNewerNamespaceUrl();

    assertThat(plucky.canHazExtendedWings()).isTrue();
    assertThatThereIsNoNewerNamespaceUrl();
  }

  protected void assertThatThereIsNoNewerNamespaceUrl() {
    final Node rootElement = modelInstance.getDocument().getDomSource().getNode().getFirstChild();
    final NamedNodeMap attributes = rootElement.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      final Node item = attributes.item(i);
      final String nodeValue = item.getNodeValue();
      assertThat(nodeValue)
          .as("Found newer namespace url which shouldn't exist")
          .isNotEqualTo(TestModelConstants.NEWER_NAMESPACE);
    }
  }
}
