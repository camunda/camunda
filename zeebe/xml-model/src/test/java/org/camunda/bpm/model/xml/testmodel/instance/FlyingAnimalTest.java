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
import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.MODEL_NAMESPACE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Sebastian Menski
 */
public class FlyingAnimalTest extends TestModelTest {

  private FlyingAnimal tweety;
  private FlyingAnimal hedwig;
  private FlyingAnimal birdo;
  private FlyingAnimal plucky;
  private FlyingAnimal fiffy;
  private FlyingAnimal timmy;
  private FlyingAnimal daisy;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(FlyingAnimalTest.class)};
    return Arrays.asList(models);
  }

  public static Object[] createModel() {
    final TestModelParser modelParser = new TestModelParser();
    final ModelInstance modelInstance = modelParser.getEmptyModel();

    final Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    // add a tns namespace prefix for QName testing
    animals.getDomElement().registerNamespace("tns", MODEL_NAMESPACE);

    final FlyingAnimal tweety = createBird(modelInstance, "tweety", Gender.Female);
    final FlyingAnimal hedwig = createBird(modelInstance, "hedwig", Gender.Male);
    final FlyingAnimal birdo = createBird(modelInstance, "birdo", Gender.Female);
    final FlyingAnimal plucky = createBird(modelInstance, "plucky", Gender.Unknown);
    final FlyingAnimal fiffy = createBird(modelInstance, "fiffy", Gender.Female);
    createBird(modelInstance, "timmy", Gender.Male);
    createBird(modelInstance, "daisy", Gender.Female);

    tweety.setFlightInstructor(hedwig);

    tweety.getFlightPartnerRefs().add(hedwig);
    tweety.getFlightPartnerRefs().add(birdo);
    tweety.getFlightPartnerRefs().add(plucky);
    tweety.getFlightPartnerRefs().add(fiffy);

    return new Object[] {Named.of("created", modelInstance)};
  }

  private void copyModelInstance(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();
    tweety = modelInstance.getModelElementById("tweety");
    hedwig = modelInstance.getModelElementById("hedwig");
    birdo = modelInstance.getModelElementById("birdo");
    plucky = modelInstance.getModelElementById("plucky");
    fiffy = modelInstance.getModelElementById("fiffy");
    timmy = modelInstance.getModelElementById("timmy");
    daisy = modelInstance.getModelElementById("daisy");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetWingspanAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final double wingspan = 2.123;
    tweety.setWingspan(wingspan);
    assertThat(tweety.getWingspan()).isEqualTo(wingspan);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetWingspanAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Double wingspan = 2.123;
    tweety.setAttributeValue("wingspan", wingspan.toString(), false);
    assertThat(tweety.getWingspan()).isEqualTo(wingspan);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveWingspanAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final double wingspan = 2.123;
    tweety.setWingspan(wingspan);
    assertThat(tweety.getWingspan()).isEqualTo(wingspan);

    tweety.removeAttribute("wingspan");

    assertThat(tweety.getWingspan()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetFlightInstructorByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setFlightInstructor(timmy);
    assertThat(tweety.getFlightInstructor()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightInstructorByIdHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.setId("new-" + hedwig.getId());
    assertThat(tweety.getFlightInstructor()).isEqualTo(hedwig);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightInstructorByIdAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.setAttributeValue("id", "new-" + hedwig.getId(), true);
    assertThat(tweety.getFlightInstructor()).isEqualTo(hedwig);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightInstructorByReplaceElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.replaceWithElement(timmy);
    assertThat(tweety.getFlightInstructor()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightInstructorByRemoveElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Animals animals = (Animals) modelInstance.getDocumentElement();
    animals.getAnimals().remove(hedwig);
    assertThat(tweety.getFlightInstructor()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearFlightInstructor(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeFlightInstructor();
    assertThat(tweety.getFlightInstructor()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddFlightPartnerRefsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getFlightPartnerRefs())
        .isNotEmpty()
        .hasSize(4)
        .containsOnly(hedwig, birdo, plucky, fiffy);

    tweety.getFlightPartnerRefs().add(timmy);
    tweety.getFlightPartnerRefs().add(daisy);

    assertThat(tweety.getFlightPartnerRefs())
        .isNotEmpty()
        .hasSize(6)
        .containsOnly(hedwig, birdo, plucky, fiffy, timmy, daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightPartnerRefsByIdByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.setId("new-" + hedwig.getId());
    plucky.setId("new-" + plucky.getId());
    assertThat(tweety.getFlightPartnerRefs()).hasSize(4).containsOnly(hedwig, birdo, plucky, fiffy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightPartnerRefsByIdByAttributeName(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    birdo.setAttributeValue("id", "new-" + birdo.getId(), true);
    fiffy.setAttributeValue("id", "new-" + fiffy.getId(), true);
    assertThat(tweety.getFlightPartnerRefs()).hasSize(4).containsOnly(hedwig, birdo, plucky, fiffy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightPartnerRefsByReplaceElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.replaceWithElement(timmy);
    plucky.replaceWithElement(daisy);
    assertThat(tweety.getFlightPartnerRefs()).hasSize(4).containsOnly(birdo, fiffy, timmy, daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightPartnerRefsByRemoveElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getFlightPartnerRefs().remove(birdo);
    tweety.getFlightPartnerRefs().remove(fiffy);
    assertThat(tweety.getFlightPartnerRefs()).hasSize(2).containsOnly(hedwig, plucky);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearFlightPartnerRefs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getFlightPartnerRefs().clear();
    assertThat(tweety.getFlightPartnerRefs()).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddFlightPartnerRefElementsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getFlightPartnerRefElements()).isNotEmpty().hasSize(4);

    final FlightPartnerRef timmyFlightPartnerRef =
        modelInstance.newInstance(FlightPartnerRef.class);
    timmyFlightPartnerRef.setTextContent(timmy.getId());
    tweety.getFlightPartnerRefElements().add(timmyFlightPartnerRef);

    final FlightPartnerRef daisyFlightPartnerRef =
        modelInstance.newInstance(FlightPartnerRef.class);
    daisyFlightPartnerRef.setTextContent(daisy.getId());
    tweety.getFlightPartnerRefElements().add(daisyFlightPartnerRef);

    assertThat(tweety.getFlightPartnerRefElements())
        .isNotEmpty()
        .hasSize(6)
        .contains(timmyFlightPartnerRef, daisyFlightPartnerRef);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testFlightPartnerRefElementsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<FlightPartnerRef> flightPartnerRefElements =
        tweety.getFlightPartnerRefElements();
    final Collection<String> textContents = new ArrayList<String>();
    for (FlightPartnerRef flightPartnerRefElement : flightPartnerRefElements) {
      final String textContent = flightPartnerRefElement.getTextContent();
      assertThat(textContent).isNotEmpty();
      textContents.add(textContent);
    }
    assertThat(textContents)
        .isNotEmpty()
        .hasSize(4)
        .containsOnly(hedwig.getId(), birdo.getId(), plucky.getId(), fiffy.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightPartnerRefElementsByTextContent(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<FlightPartnerRef> flightPartnerRefs =
        new ArrayList<FlightPartnerRef>(tweety.getFlightPartnerRefElements());

    flightPartnerRefs.get(0).setTextContent(timmy.getId());
    flightPartnerRefs.get(2).setTextContent(daisy.getId());

    assertThat(tweety.getFlightPartnerRefs()).hasSize(4).containsOnly(birdo, fiffy, timmy, daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateFlightPartnerRefElementsByRemoveElements(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<FlightPartnerRef> flightPartnerRefs =
        new ArrayList<FlightPartnerRef>(tweety.getFlightPartnerRefElements());
    tweety.getFlightPartnerRefElements().remove(flightPartnerRefs.get(1));
    tweety.getFlightPartnerRefElements().remove(flightPartnerRefs.get(3));
    assertThat(tweety.getFlightPartnerRefs()).hasSize(2).containsOnly(hedwig, plucky);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearFlightPartnerRefElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getFlightPartnerRefElements().clear();
    assertThat(tweety.getFlightPartnerRefElements()).isEmpty();

    // should not affect animals collection
    final Animals animals = (Animals) modelInstance.getDocumentElement();
    assertThat(animals.getAnimals()).isNotEmpty().hasSize(7);
  }
}
