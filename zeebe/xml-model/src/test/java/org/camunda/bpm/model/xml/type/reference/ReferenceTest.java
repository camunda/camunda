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
package org.camunda.bpm.model.xml.type.reference;

import static org.assertj.core.api.Assertions.fail;
import static org.camunda.bpm.model.xml.test.assertions.ModelAssertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.UnsupportedModelOperationException;
import org.camunda.bpm.model.xml.impl.type.reference.AttributeReferenceImpl;
import org.camunda.bpm.model.xml.impl.type.reference.QNameAttributeReferenceImpl;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.camunda.bpm.model.xml.testmodel.instance.*;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Sebastian Menski
 */
public class ReferenceTest extends TestModelTest {

  private Bird tweety;
  private Bird daffy;
  private Bird daisy;
  private Bird plucky;
  private Bird birdo;
  private FlightPartnerRef flightPartnerRef;

  private ModelElementType animalType;
  private QNameAttributeReferenceImpl<Animal> fatherReference;
  private AttributeReferenceImpl<Animal> motherReference;
  private ElementReferenceCollection<FlyingAnimal, FlightPartnerRef> flightPartnerRefsColl;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(ReferenceTest.class)};
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
    createBird(modelInstance, "plucky", Gender.Male);
    createBird(modelInstance, "birdo", Gender.Female);
    tweety.setFather(daffy);
    tweety.setMother(daisy);

    tweety.getFlightPartnerRefs().add(daffy);

    return new Object[] {Named.of("created", modelInstance)};
  }

  @SuppressWarnings("unchecked")
  private void copyModelInstance(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();

    tweety = modelInstance.getModelElementById("tweety");
    daffy = modelInstance.getModelElementById("daffy");
    daisy = modelInstance.getModelElementById("daisy");
    plucky = modelInstance.getModelElementById("plucky");
    birdo = modelInstance.getModelElementById("birdo");

    animalType = modelInstance.getModel().getType(Animal.class);

    // QName attribute reference
    fatherReference =
        (QNameAttributeReferenceImpl<Animal>)
            animalType.getAttribute("father").getOutgoingReferences().iterator().next();

    // ID attribute reference
    motherReference =
        (AttributeReferenceImpl<Animal>)
            animalType.getAttribute("mother").getOutgoingReferences().iterator().next();

    // ID element reference
    flightPartnerRefsColl = FlyingAnimal.flightPartnerRefsColl;

    final ModelElementType flightPartnerRefType =
        modelInstance.getModel().getType(FlightPartnerRef.class);
    flightPartnerRef =
        (FlightPartnerRef)
            modelInstance.getModelElementsByType(flightPartnerRefType).iterator().next();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReferenceIdentifier(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(fatherReference).hasIdentifier(tweety, daffy.getId());
    assertThat(motherReference).hasIdentifier(tweety, daisy.getId());
    assertThat(flightPartnerRefsColl).hasIdentifier(tweety, daffy.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReferenceTargetElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(fatherReference).hasTargetElement(tweety, daffy);
    assertThat(motherReference).hasTargetElement(tweety, daisy);
    assertThat(flightPartnerRefsColl).hasTargetElement(tweety, daffy);

    fatherReference.setReferenceTargetElement(tweety, plucky);
    motherReference.setReferenceTargetElement(tweety, birdo);
    flightPartnerRefsColl.setReferenceTargetElement(flightPartnerRef, daisy);

    assertThat(fatherReference).hasTargetElement(tweety, plucky);
    assertThat(motherReference).hasTargetElement(tweety, birdo);
    assertThat(flightPartnerRefsColl).hasTargetElement(tweety, daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReferenceTargetAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Attribute<?> idAttribute = animalType.getAttribute("id");
    assertThat(idAttribute).hasIncomingReferences(fatherReference, motherReference);

    assertThat(fatherReference).hasTargetAttribute(idAttribute);
    assertThat(motherReference).hasTargetAttribute(idAttribute);
    assertThat(flightPartnerRefsColl).hasTargetAttribute(idAttribute);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReferenceSourceAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Attribute<?> fatherAttribute = animalType.getAttribute("father");
    final Attribute<?> motherAttribute = animalType.getAttribute("mother");

    assertThat(fatherReference).hasSourceAttribute(fatherAttribute);
    assertThat(motherReference).hasSourceAttribute(motherAttribute);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveReference(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    fatherReference.referencedElementRemoved(daffy, daffy.getId());

    assertThat(fatherReference).hasNoTargetElement(tweety);
    assertThat(tweety.getFather()).isNull();

    motherReference.referencedElementRemoved(daisy, daisy.getId());
    assertThat(motherReference).hasNoTargetElement(tweety);
    assertThat(tweety.getMother()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testTargetElementsCollection(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<FlyingAnimal> referenceTargetElements =
        flightPartnerRefsColl.getReferenceTargetElements(tweety);
    final Collection<FlyingAnimal> flightPartners =
        Arrays.asList(new FlyingAnimal[] {birdo, daffy, daisy, plucky});

    // directly test collection methods and not use the appropriate assertion methods
    assertThat(referenceTargetElements.size()).isEqualTo(1);
    assertThat(referenceTargetElements.isEmpty()).isFalse();
    assertThat(referenceTargetElements.contains(daffy)).isTrue();
    assertThat(referenceTargetElements.toArray()).isEqualTo(new Object[] {daffy});
    assertThat(referenceTargetElements.toArray(new FlyingAnimal[1]))
        .isEqualTo(new FlyingAnimal[] {daffy});

    assertThat(referenceTargetElements.add(daisy)).isTrue();
    assertThat(referenceTargetElements).hasSize(2).containsOnly(daffy, daisy);

    assertThat(referenceTargetElements.remove(daisy)).isTrue();
    assertThat(referenceTargetElements).hasSize(1).containsOnly(daffy);

    assertThat(referenceTargetElements.addAll(flightPartners)).isTrue();
    assertThat(referenceTargetElements.containsAll(flightPartners)).isTrue();
    assertThat(referenceTargetElements).hasSize(4).containsOnly(daffy, daisy, plucky, birdo);

    assertThat(referenceTargetElements.removeAll(flightPartners)).isTrue();
    assertThat(referenceTargetElements).isEmpty();

    try {
      referenceTargetElements.retainAll(flightPartners);
      fail("retainAll method is not implemented");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnsupportedModelOperationException.class);
    }

    referenceTargetElements.addAll(flightPartners);
    assertThat(referenceTargetElements).isNotEmpty();
    referenceTargetElements.clear();
    assertThat(referenceTargetElements).isEmpty();
  }
}
