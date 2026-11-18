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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.ModelImpl;
import org.camunda.bpm.model.xml.impl.parser.AbstractModelParser;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelConstants;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author Ronny Bräunlich
 */
public class AlternativeNsTest extends TestModelTest {

  private static final String MECHANICAL_NS = "http://camunda.org/mechanical";
  private static final String YET_ANOTHER_NS = "http://camunda.org/yans";

  public AlternativeNsTest(
      String testName, ModelInstance testModelInstance, AbstractModelParser modelParser) {
    super(testName, testModelInstance, modelParser);
  }

  @Parameters(name = "Model {0}")
  public static Collection<Object[]> models() {
    return Collections.singleton(parseModel(AlternativeNsTest.class));
  }

  @Before
  public void setUp() {
    modelInstance = cloneModelInstance();
    final ModelImpl modelImpl = (ModelImpl) modelInstance.getModel();
    modelImpl.declareAlternativeNamespace(MECHANICAL_NS, TestModelConstants.NEWER_NAMESPACE);
    modelImpl.declareAlternativeNamespace(YET_ANOTHER_NS, TestModelConstants.NEWER_NAMESPACE);
  }

  @After
  public void tearDown() {
    final ModelImpl modelImpl = (ModelImpl) modelInstance.getModel();
    modelImpl.undeclareAlternativeNamespace(MECHANICAL_NS);
    modelImpl.undeclareAlternativeNamespace(YET_ANOTHER_NS);
  }

  @Test
  public void getUniqueChildElementByNameNsForAlternativeNs() {
    final ModelElementInstance hedwig = modelInstance.getModelElementById("hedwig");
    assertThat(hedwig, is(notNullValue()));
    final ModelElementInstance childElementByNameNs =
        hedwig.getUniqueChildElementByNameNs(TestModelConstants.NEWER_NAMESPACE, "wings");
    assertThat(childElementByNameNs, is(notNullValue()));
    assertThat(childElementByNameNs.getTextContent(), is("wusch"));
  }

  @Test
  public void getUniqueChildElementByNameNsForSecondAlternativeNs() {
    // givne
    final ModelElementInstance donald = modelInstance.getModelElementById("donald");

    // when
    final ModelElementInstance childElementByNameNs =
        donald.getUniqueChildElementByNameNs(TestModelConstants.NEWER_NAMESPACE, "wings");

    // then
    assertThat(childElementByNameNs, is(notNullValue()));
    assertThat(childElementByNameNs.getTextContent(), is("flappy"));
  }

  @Test
  public void getChildElementsByTypeForAlternativeNs() {
    final ModelElementInstance birdo = modelInstance.getModelElementById("birdo");
    assertThat(birdo, is(notNullValue()));
    final Collection<Wings> elements = birdo.getChildElementsByType(Wings.class);
    assertThat(elements.size(), is(1));
    assertThat(elements.iterator().next().getTextContent(), is("zisch"));
  }

  @Test
  public void getChildElementsByTypeForSecondAlternativeNs() {
    // given
    final ModelElementInstance donald = modelInstance.getModelElementById("donald");

    // when
    final Collection<Wings> elements = donald.getChildElementsByType(Wings.class);

    // then
    assertThat(elements.size(), is(1));
    assertThat(elements.iterator().next().getTextContent(), is("flappy"));
  }

  @Test
  public void getAttributeValueNsForAlternativeNs() {
    final Bird plucky = modelInstance.getModelElementById("plucky");
    assertThat(plucky, is(notNullValue()));
    final Boolean extendedWings = plucky.canHazExtendedWings();
    assertThat(extendedWings, is(false));
  }

  @Test
  public void getAttributeValueNsForSecondAlternativeNs() {
    // given
    final Bird donald = modelInstance.getModelElementById("donald");

    // when
    final Boolean extendedWings = donald.canHazExtendedWings();

    // then
    assertThat(extendedWings, is(true));
  }

  @Test
  public void modifyingAttributeWithAlternativeNamespaceKeepsAlternativeNamespace() {
    final Bird plucky = modelInstance.getModelElementById("plucky");
    assertThat(plucky, is(notNullValue()));
    // validate old value
    final Boolean extendedWings = plucky.canHazExtendedWings();
    assertThat(extendedWings, is(false));
    // change it
    plucky.setCanHazExtendedWings(true);
    final String attributeValueNs =
        plucky.getAttributeValueNs(MECHANICAL_NS, "canHazExtendedWings");
    assertThat(attributeValueNs, is("true"));
  }

  @Test
  public void modifyingAttributeWithSecondAlternativeNamespaceKeepsSecondAlternativeNamespace() {
    // given
    final Bird donald = modelInstance.getModelElementById("donald");

    // when
    donald.setCanHazExtendedWings(false);

    // then
    final String attributeValueNs =
        donald.getAttributeValueNs(YET_ANOTHER_NS, "canHazExtendedWings");
    assertThat(attributeValueNs, is("false"));
  }

  @Test
  public void modifyingAttributeWithNewNamespaceKeepsNewNamespace() {
    final Bird bird = createBird(modelInstance, "waldo", Gender.Male);
    bird.setCanHazExtendedWings(true);
    final String attributeValueNs =
        bird.getAttributeValueNs(TestModelConstants.NEWER_NAMESPACE, "canHazExtendedWings");
    assertThat(attributeValueNs, is("true"));
  }

  @Test
  public void modifyingElementWithAlternativeNamespaceKeepsAlternativeNamespace() {
    final Bird birdo = modelInstance.getModelElementById("birdo");
    assertThat(birdo, is(notNullValue()));
    final Wings wings = birdo.getWings();
    assertThat(wings, is(notNullValue()));
    wings.setTextContent("kawusch");

    final List<DomElement> childElementsByNameNs =
        birdo.getDomElement().getChildElementsByNameNs(MECHANICAL_NS, "wings");
    assertThat(childElementsByNameNs.size(), is(1));
    assertThat(childElementsByNameNs.get(0).getTextContent(), is("kawusch"));
  }

  @Test
  public void modifyingElementWithSecondAlternativeNamespaceKeepsSecondAlternativeNamespace() {
    // given
    final Bird donald = modelInstance.getModelElementById("donald");
    final Wings wings = donald.getWings();

    // when
    wings.setTextContent("kawusch");

    // then
    final List<DomElement> childElementsByNameNs =
        donald.getDomElement().getChildElementsByNameNs(YET_ANOTHER_NS, "wings");
    assertThat(childElementsByNameNs.size(), is(1));
    assertThat(childElementsByNameNs.get(0).getTextContent(), is("kawusch"));
  }

  @Test
  public void modifyingElementWithNewNamespaceKeepsNewNamespace() {
    final Bird bird = createBird(modelInstance, "waldo", Gender.Male);
    bird.setWings(modelInstance.newInstance(Wings.class));

    final List<DomElement> childElementsByNameNs =
        bird.getDomElement().getChildElementsByNameNs(TestModelConstants.NEWER_NAMESPACE, "wings");
    assertThat(childElementsByNameNs.size(), is(1));
  }

  @Test
  public void useExistingNamespace() {
    assertThatThereIsNoNewerNamespaceUrl();

    final Bird plucky = modelInstance.getModelElementById("plucky");
    plucky.setAttributeValueNs(MECHANICAL_NS, "canHazExtendedWings", "true");

    final Bird donald = modelInstance.getModelElementById("donald");
    donald.setAttributeValueNs(YET_ANOTHER_NS, "canHazExtendedWings", "false");
    assertThatThereIsNoNewerNamespaceUrl();

    assertTrue(plucky.canHazExtendedWings());
    assertThatThereIsNoNewerNamespaceUrl();
  }

  protected void assertThatThereIsNoNewerNamespaceUrl() {
    final Node rootElement = modelInstance.getDocument().getDomSource().getNode().getFirstChild();
    final NamedNodeMap attributes = rootElement.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      final Node item = attributes.item(i);
      final String nodeValue = item.getNodeValue();
      assertNotEquals(
          "Found newer namespace url which shouldn't exist",
          TestModelConstants.NEWER_NAMESPACE,
          nodeValue);
    }
  }
}
