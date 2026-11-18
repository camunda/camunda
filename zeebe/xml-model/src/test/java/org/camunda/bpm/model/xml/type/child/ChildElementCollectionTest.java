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
package org.camunda.bpm.model.xml.type.child;

import static org.assertj.core.api.Assertions.fail;
import static org.camunda.bpm.model.xml.test.assertions.ModelAssertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.UnsupportedModelOperationException;
import org.camunda.bpm.model.xml.impl.type.child.ChildElementCollectionImpl;
import org.camunda.bpm.model.xml.impl.type.child.ChildElementImpl;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.camunda.bpm.model.xml.testmodel.instance.*;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Sebastian Menski
 */
public class ChildElementCollectionTest extends TestModelTest {

  private Bird tweety;
  private Bird daffy;
  private Bird daisy;
  private Bird plucky;
  private Bird birdo;
  private ChildElement<FlightInstructor> flightInstructorChild;
  private ChildElementCollection<FlightPartnerRef> flightPartnerRefCollection;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(ChildElementCollectionTest.class)};
    return Arrays.asList(models);
  }

  public static Object[] createModel() {
    final TestModelParser modelParser = new TestModelParser();
    final ModelInstance modelInstance = modelParser.getEmptyModel();

    final Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    final Bird tweety = createBird(modelInstance, "tweety", Gender.Female);
    final Bird daffy = createBird(modelInstance, "daffy", Gender.Male);
    final Bird daisy = createBird(modelInstance, "daisy", Gender.Female);
    final Bird plucky = createBird(modelInstance, "plucky", Gender.Male);
    createBird(modelInstance, "birdo", Gender.Female);

    tweety.setFlightInstructor(daffy);
    tweety.getFlightPartnerRefs().add(daisy);
    tweety.getFlightPartnerRefs().add(plucky);

    return new Object[] {Named.of("created", modelInstance)};
  }

  private void copyModelInstance(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();

    tweety = modelInstance.getModelElementById("tweety");
    daffy = modelInstance.getModelElementById("daffy");
    daisy = modelInstance.getModelElementById("daisy");
    plucky = modelInstance.getModelElementById("plucky");
    birdo = modelInstance.getModelElementById("birdo");

    flightInstructorChild =
        (ChildElement<FlightInstructor>)
            FlyingAnimal.flightInstructorChild.getReferenceSourceCollection();
    flightPartnerRefCollection = FlyingAnimal.flightPartnerRefsColl.getReferenceSourceCollection();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testImmutable(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(flightInstructorChild).isMutable();
    assertThat(flightPartnerRefCollection).isMutable();

    ((ChildElementImpl<FlightInstructor>) flightInstructorChild).setImmutable();
    ((ChildElementCollectionImpl<FlightPartnerRef>) flightPartnerRefCollection).setImmutable();
    assertThat(flightInstructorChild).isImmutable();
    assertThat(flightPartnerRefCollection).isImmutable();

    ((ChildElementImpl<FlightInstructor>) flightInstructorChild).setMutable(true);
    ((ChildElementCollectionImpl<FlightPartnerRef>) flightPartnerRefCollection).setMutable(true);
    assertThat(flightInstructorChild).isMutable();
    assertThat(flightPartnerRefCollection).isMutable();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testMinOccurs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(flightInstructorChild).isOptional();
    assertThat(flightPartnerRefCollection).isOptional();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testMaxOccurs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(flightInstructorChild).occursMaximal(1);
    assertThat(flightPartnerRefCollection).isUnbounded();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testChildElementType(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(flightInstructorChild).containsType(FlightInstructor.class);
    assertThat(flightPartnerRefCollection).containsType(FlightPartnerRef.class);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testParentElementType(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final ModelElementType flyingAnimalType = modelInstance.getModel().getType(FlyingAnimal.class);

    assertThat(flightInstructorChild).hasParentElementType(flyingAnimalType);
    assertThat(flightPartnerRefCollection).hasParentElementType(flyingAnimalType);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGetChildElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(flightInstructorChild).hasSize(tweety, 1);
    assertThat(flightPartnerRefCollection).hasSize(tweety, 2);

    final FlightInstructor flightInstructor = flightInstructorChild.getChild(tweety);
    assertThat(flightInstructor.getTextContent()).isEqualTo(daffy.getId());

    for (FlightPartnerRef flightPartnerRef : flightPartnerRefCollection.get(tweety)) {
      assertThat(flightPartnerRef.getTextContent()).isIn(daisy.getId(), plucky.getId());
    }
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveChildElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(flightInstructorChild).isNotEmpty(tweety);
    assertThat(flightPartnerRefCollection).isNotEmpty(tweety);

    flightInstructorChild.removeChild(tweety);
    flightPartnerRefCollection.get(tweety).clear();

    assertThat(flightInstructorChild).isEmpty(tweety);
    assertThat(flightPartnerRefCollection).isEmpty(tweety);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testChildElementsCollection(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<FlightPartnerRef> flightPartnerRefs = flightPartnerRefCollection.get(tweety);

    final Iterator<FlightPartnerRef> iterator = flightPartnerRefs.iterator();
    final FlightPartnerRef daisyRef = iterator.next();
    final FlightPartnerRef pluckyRef = iterator.next();
    assertThat(daisyRef.getTextContent()).isEqualTo(daisy.getId());
    assertThat(pluckyRef.getTextContent()).isEqualTo(plucky.getId());

    final FlightPartnerRef birdoRef = modelInstance.newInstance(FlightPartnerRef.class);
    birdoRef.setTextContent(birdo.getId());

    final Collection<FlightPartnerRef> flightPartners =
        Arrays.asList(birdoRef, daisyRef, pluckyRef);

    // directly test collection methods and not use the appropriate assertion methods
    assertThat(flightPartnerRefs.size()).isEqualTo(2);
    assertThat(flightPartnerRefs.isEmpty()).isFalse();
    assertThat(flightPartnerRefs.contains(daisyRef));
    assertThat(flightPartnerRefs.toArray()).isEqualTo(new Object[] {daisyRef, pluckyRef});
    assertThat(flightPartnerRefs.toArray(new FlightPartnerRef[1]))
        .isEqualTo(new FlightPartnerRef[] {daisyRef, pluckyRef});

    assertThat(flightPartnerRefs.add(birdoRef)).isTrue();
    assertThat(flightPartnerRefs).hasSize(3).containsOnly(birdoRef, daisyRef, pluckyRef);

    assertThat(flightPartnerRefs.remove(daisyRef)).isTrue();
    assertThat(flightPartnerRefs).hasSize(2).containsOnly(birdoRef, pluckyRef);

    assertThat(flightPartnerRefs.addAll(flightPartners)).isTrue();
    assertThat(flightPartnerRefs.containsAll(flightPartners)).isTrue();
    assertThat(flightPartnerRefs).hasSize(3).containsOnly(birdoRef, daisyRef, pluckyRef);

    assertThat(flightPartnerRefs.removeAll(flightPartners)).isTrue();
    assertThat(flightPartnerRefs).isEmpty();

    try {
      flightPartnerRefs.retainAll(flightPartners);
      fail("retainAll method is not implemented");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnsupportedModelOperationException.class);
    }

    flightPartnerRefs.addAll(flightPartners);
    assertThat(flightPartnerRefs).isNotEmpty();
    flightPartnerRefs.clear();
    assertThat(flightPartnerRefs).isEmpty();
  }
}
