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
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.MODEL_NAMESPACE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Sebastian Menski
 */
public class AnimalTest extends TestModelTest {

  private Animal tweety;
  private Animal hedwig;
  private Animal birdo;
  private Animal plucky;
  private Animal fiffy;
  private Animal timmy;
  private Animal daisy;
  private RelationshipDefinition hedwigRelationship;
  private RelationshipDefinition birdoRelationship;
  private RelationshipDefinition pluckyRelationship;
  private RelationshipDefinition fiffyRelationship;
  private RelationshipDefinition timmyRelationship;
  private RelationshipDefinition daisyRelationship;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(AnimalTest.class)};
    return Arrays.asList(models);
  }

  public static Object[] createModel() {
    final TestModelParser modelParser = new TestModelParser();
    final ModelInstance modelInstance = modelParser.getEmptyModel();

    final Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    // add a tns namespace prefix for QName testing
    animals.getDomElement().registerNamespace("tns", MODEL_NAMESPACE);

    final Animal tweety = createBird(modelInstance, "tweety", Gender.Female);
    final Animal hedwig = createBird(modelInstance, "hedwig", Gender.Male);
    final Animal birdo = createBird(modelInstance, "birdo", Gender.Female);
    final Animal plucky = createBird(modelInstance, "plucky", Gender.Unknown);
    final Animal fiffy = createBird(modelInstance, "fiffy", Gender.Female);
    createBird(modelInstance, "timmy", Gender.Male);
    createBird(modelInstance, "daisy", Gender.Female);

    // create and add some relationships
    final RelationshipDefinition hedwigRelationship =
        createRelationshipDefinition(modelInstance, hedwig, ChildRelationshipDefinition.class);
    addRelationshipDefinition(tweety, hedwigRelationship);
    final RelationshipDefinition birdoRelationship =
        createRelationshipDefinition(modelInstance, birdo, ChildRelationshipDefinition.class);
    addRelationshipDefinition(tweety, birdoRelationship);
    final RelationshipDefinition pluckyRelationship =
        createRelationshipDefinition(modelInstance, plucky, FriendRelationshipDefinition.class);
    addRelationshipDefinition(tweety, pluckyRelationship);
    final RelationshipDefinition fiffyRelationship =
        createRelationshipDefinition(modelInstance, fiffy, FriendRelationshipDefinition.class);
    addRelationshipDefinition(tweety, fiffyRelationship);

    tweety.getRelationshipDefinitionRefs().add(hedwigRelationship);
    tweety.getRelationshipDefinitionRefs().add(birdoRelationship);
    tweety.getRelationshipDefinitionRefs().add(pluckyRelationship);
    tweety.getRelationshipDefinitionRefs().add(fiffyRelationship);

    tweety.getBestFriends().add(birdo);
    tweety.getBestFriends().add(plucky);

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

    hedwigRelationship = modelInstance.getModelElementById("tweety-hedwig");
    birdoRelationship = modelInstance.getModelElementById("tweety-birdo");
    pluckyRelationship = modelInstance.getModelElementById("tweety-plucky");
    fiffyRelationship = modelInstance.getModelElementById("tweety-fiffy");

    timmyRelationship =
        createRelationshipDefinition(modelInstance, timmy, FriendRelationshipDefinition.class);
    daisyRelationship =
        createRelationshipDefinition(modelInstance, daisy, ChildRelationshipDefinition.class);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetIdAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String newId = "new-" + tweety.getId();
    tweety.setId(newId);
    assertThat(tweety.getId()).isEqualTo(newId);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetIdAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("id", "duffy", true);
    assertThat(tweety.getId()).isEqualTo("duffy");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveIdAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeAttribute("id");
    assertThat(tweety.getId()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetNameAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setName("tweety");
    assertThat(tweety.getName()).isEqualTo("tweety");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetNameAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("name", "daisy");
    assertThat(tweety.getName()).isEqualTo("daisy");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveNameAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeAttribute("name");
    assertThat(tweety.getName()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetFatherAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setFather(timmy);
    assertThat(tweety.getFather()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetFatherAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("father", timmy.getId());
    assertThat(tweety.getFather()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetFatherAttributeByAttributeNameWithNamespace(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("father", "tns:hedwig");
    assertThat(tweety.getFather()).isEqualTo(hedwig);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveFatherAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setFather(timmy);
    assertThat(tweety.getFather()).isEqualTo(timmy);
    tweety.removeAttribute("father");
    assertThat(tweety.getFather()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testChangeIdAttributeOfFatherReference(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setFather(timmy);
    assertThat(tweety.getFather()).isEqualTo(timmy);
    timmy.setId("new-" + timmy.getId());
    assertThat(tweety.getFather()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReplaceFatherReferenceWithNewAnimal(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setFather(timmy);
    assertThat(tweety.getFather()).isEqualTo(timmy);
    timmy.replaceWithElement(plucky);
    assertThat(tweety.getFather()).isEqualTo(plucky);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetMotherAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setMother(daisy);
    assertThat(tweety.getMother()).isEqualTo(daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetMotherAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("mother", fiffy.getId());
    assertThat(tweety.getMother()).isEqualTo(fiffy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveMotherAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setMother(daisy);
    assertThat(tweety.getMother()).isEqualTo(daisy);
    tweety.removeAttribute("mother");
    assertThat(tweety.getMother()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReplaceMotherReferenceWithNewAnimal(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setMother(daisy);
    assertThat(tweety.getMother()).isEqualTo(daisy);
    daisy.replaceWithElement(birdo);
    assertThat(tweety.getMother()).isEqualTo(birdo);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testChangeIdAttributeOfMotherReference(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setMother(daisy);
    assertThat(tweety.getMother()).isEqualTo(daisy);
    daisy.setId("new-" + daisy.getId());
    assertThat(tweety.getMother()).isEqualTo(daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetIsEndangeredAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setIsEndangered(true);
    assertThat(tweety.isEndangered()).isTrue();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetIsEndangeredAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("isEndangered", "false");
    assertThat(tweety.isEndangered()).isFalse();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveIsEndangeredAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeAttribute("isEndangered");
    // default value of isEndangered: false
    assertThat(tweety.isEndangered()).isFalse();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetGenderAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setGender(Gender.Male);
    assertThat(tweety.getGender()).isEqualTo(Gender.Male);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetGenderAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("gender", Gender.Unknown.toString());
    assertThat(tweety.getGender()).isEqualTo(Gender.Unknown);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveGenderAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeAttribute("gender");
    assertThat(tweety.getGender()).isNull();

    // gender is required, so the model is invalid without
    try {
      validateModel();
      fail("The model is invalid cause the gender of an animal is a required attribute.");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ModelValidationException.class);
    }

    // add gender to make model valid
    tweety.setGender(Gender.Female);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetAgeAttributeByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAge(13);
    assertThat(tweety.getAge()).isEqualTo(13);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetAgeAttributeByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("age", "23");
    assertThat(tweety.getAge()).isEqualTo(23);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveAgeAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeAttribute("age");
    assertThat(tweety.getAge()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddRelationshipDefinitionsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getRelationshipDefinitions())
        .isNotEmpty()
        .hasSize(4)
        .containsOnly(hedwigRelationship, birdoRelationship, pluckyRelationship, fiffyRelationship);

    tweety.getRelationshipDefinitions().add(timmyRelationship);
    tweety.getRelationshipDefinitions().add(daisyRelationship);

    assertThat(tweety.getRelationshipDefinitions())
        .hasSize(6)
        .containsOnly(
            hedwigRelationship,
            birdoRelationship,
            pluckyRelationship,
            fiffyRelationship,
            timmyRelationship,
            daisyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionsByIdByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwigRelationship.setId("new-" + hedwigRelationship.getId());
    pluckyRelationship.setId("new-" + pluckyRelationship.getId());
    assertThat(tweety.getRelationshipDefinitions())
        .hasSize(4)
        .containsOnly(hedwigRelationship, birdoRelationship, pluckyRelationship, fiffyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionsByIdByAttributeName(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    birdoRelationship.setAttributeValue("id", "new-" + birdoRelationship.getId(), true);
    fiffyRelationship.setAttributeValue("id", "new-" + fiffyRelationship.getId(), true);
    assertThat(tweety.getRelationshipDefinitions())
        .hasSize(4)
        .containsOnly(hedwigRelationship, birdoRelationship, pluckyRelationship, fiffyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionsByReplaceElements(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwigRelationship.replaceWithElement(timmyRelationship);
    pluckyRelationship.replaceWithElement(daisyRelationship);
    assertThat(tweety.getRelationshipDefinitions())
        .hasSize(4)
        .containsOnly(birdoRelationship, fiffyRelationship, timmyRelationship, daisyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionsByRemoveElements(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitions().remove(birdoRelationship);
    tweety.getRelationshipDefinitions().remove(fiffyRelationship);
    assertThat(tweety.getRelationshipDefinitions())
        .hasSize(2)
        .containsOnly(hedwigRelationship, pluckyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearRelationshipDefinitions(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitions().clear();
    assertThat(tweety.getRelationshipDefinitions()).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddRelationsDefinitionRefsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getRelationshipDefinitionRefs())
        .isNotEmpty()
        .hasSize(4)
        .containsOnly(hedwigRelationship, birdoRelationship, pluckyRelationship, fiffyRelationship);

    addRelationshipDefinition(tweety, timmyRelationship);
    addRelationshipDefinition(tweety, daisyRelationship);
    tweety.getRelationshipDefinitionRefs().add(timmyRelationship);
    tweety.getRelationshipDefinitionRefs().add(daisyRelationship);

    assertThat(tweety.getRelationshipDefinitionRefs())
        .isNotEmpty()
        .hasSize(6)
        .containsOnly(
            hedwigRelationship,
            birdoRelationship,
            pluckyRelationship,
            fiffyRelationship,
            timmyRelationship,
            daisyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefsByIdByHelper(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwigRelationship.setId("child-relationship");
    pluckyRelationship.setId("friend-relationship");
    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(4)
        .containsOnly(hedwigRelationship, birdoRelationship, pluckyRelationship, fiffyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefsByIdByAttributeName(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    birdoRelationship.setAttributeValue("id", "birdo-relationship", true);
    fiffyRelationship.setAttributeValue("id", "fiffy-relationship", true);
    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(4)
        .containsOnly(hedwigRelationship, birdoRelationship, pluckyRelationship, fiffyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefsByReplaceElements(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwigRelationship.replaceWithElement(timmyRelationship);
    pluckyRelationship.replaceWithElement(daisyRelationship);
    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(4)
        .containsOnly(birdoRelationship, fiffyRelationship, timmyRelationship, daisyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefsByRemoveElements(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitions().remove(birdoRelationship);
    tweety.getRelationshipDefinitions().remove(fiffyRelationship);
    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(2)
        .containsOnly(hedwigRelationship, pluckyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefsByRemoveIdAttribute(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    birdoRelationship.removeAttribute("id");
    pluckyRelationship.removeAttribute("id");
    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(2)
        .containsOnly(hedwigRelationship, fiffyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearRelationshipDefinitionsRefs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitionRefs().clear();
    assertThat(tweety.getRelationshipDefinitionRefs()).isEmpty();
    // should not affect animal relationship definitions
    assertThat(tweety.getRelationshipDefinitions()).hasSize(4);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearRelationshipDefinitionRefsByClearRelationshipDefinitions(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getRelationshipDefinitionRefs()).isNotEmpty();
    tweety.getRelationshipDefinitions().clear();
    assertThat(tweety.getRelationshipDefinitions()).isEmpty();
    // should affect animal relationship definition refs
    assertThat(tweety.getRelationshipDefinitionRefs()).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddRelationshipDefinitionRefElementsByHelper(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getRelationshipDefinitionRefElements()).isNotEmpty().hasSize(4);

    addRelationshipDefinition(tweety, timmyRelationship);
    final RelationshipDefinitionRef timmyRelationshipDefinitionRef =
        modelInstance.newInstance(RelationshipDefinitionRef.class);
    timmyRelationshipDefinitionRef.setTextContent(timmyRelationship.getId());
    tweety.getRelationshipDefinitionRefElements().add(timmyRelationshipDefinitionRef);

    addRelationshipDefinition(tweety, daisyRelationship);
    final RelationshipDefinitionRef daisyRelationshipDefinitionRef =
        modelInstance.newInstance(RelationshipDefinitionRef.class);
    daisyRelationshipDefinitionRef.setTextContent(daisyRelationship.getId());
    tweety.getRelationshipDefinitionRefElements().add(daisyRelationshipDefinitionRef);

    assertThat(tweety.getRelationshipDefinitionRefElements())
        .isNotEmpty()
        .hasSize(6)
        .contains(timmyRelationshipDefinitionRef, daisyRelationshipDefinitionRef);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRelationshipDefinitionRefElementsByTextContent(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<RelationshipDefinitionRef> relationshipDefinitionRefElements =
        tweety.getRelationshipDefinitionRefElements();
    final Collection<String> textContents = new ArrayList<String>();
    for (RelationshipDefinitionRef relationshipDefinitionRef : relationshipDefinitionRefElements) {
      final String textContent = relationshipDefinitionRef.getTextContent();
      assertThat(textContent).isNotEmpty();
      textContents.add(textContent);
    }
    assertThat(textContents)
        .isNotEmpty()
        .hasSize(4)
        .containsOnly(
            hedwigRelationship.getId(),
            birdoRelationship.getId(),
            pluckyRelationship.getId(),
            fiffyRelationship.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefElementsByTextContent(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<RelationshipDefinitionRef> relationshipDefinitionRefs =
        new ArrayList<RelationshipDefinitionRef>(tweety.getRelationshipDefinitionRefElements());

    addRelationshipDefinition(tweety, timmyRelationship);
    relationshipDefinitionRefs.get(0).setTextContent(timmyRelationship.getId());

    addRelationshipDefinition(daisy, daisyRelationship);
    relationshipDefinitionRefs.get(2).setTextContent(daisyRelationship.getId());

    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(4)
        .containsOnly(birdoRelationship, fiffyRelationship, timmyRelationship, daisyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefElementsByTextContentWithNamespace(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<RelationshipDefinitionRef> relationshipDefinitionRefs =
        new ArrayList<RelationshipDefinitionRef>(tweety.getRelationshipDefinitionRefElements());

    addRelationshipDefinition(tweety, timmyRelationship);
    relationshipDefinitionRefs.get(0).setTextContent("tns:" + timmyRelationship.getId());

    addRelationshipDefinition(daisy, daisyRelationship);
    relationshipDefinitionRefs.get(2).setTextContent("tns:" + daisyRelationship.getId());

    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(4)
        .containsOnly(birdoRelationship, fiffyRelationship, timmyRelationship, daisyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateRelationshipDefinitionRefElementsByRemoveElements(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<RelationshipDefinitionRef> relationshipDefinitionRefs =
        new ArrayList<RelationshipDefinitionRef>(tweety.getRelationshipDefinitionRefElements());
    tweety.getRelationshipDefinitionRefElements().remove(relationshipDefinitionRefs.get(1));
    tweety.getRelationshipDefinitionRefElements().remove(relationshipDefinitionRefs.get(3));
    assertThat(tweety.getRelationshipDefinitionRefs())
        .hasSize(2)
        .containsOnly(hedwigRelationship, pluckyRelationship);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearRelationshipDefinitionRefElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitionRefElements().clear();
    assertThat(tweety.getRelationshipDefinitionRefElements()).isEmpty();
    assertThat(tweety.getRelationshipDefinitionRefs()).isEmpty();
    // should not affect animal relationship definitions
    assertThat(tweety.getRelationshipDefinitions()).isNotEmpty().hasSize(4);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearRelationshipDefinitionRefElementsByClearRelationshipDefinitionRefs(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitionRefs().clear();
    assertThat(tweety.getRelationshipDefinitionRefs()).isEmpty();
    assertThat(tweety.getRelationshipDefinitionRefElements()).isEmpty();
    // should not affect animal relationship definitions
    assertThat(tweety.getRelationshipDefinitions()).isNotEmpty().hasSize(4);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearRelationshipDefinitionRefElementsByClearRelationshipDefinitions(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getRelationshipDefinitions().clear();
    assertThat(tweety.getRelationshipDefinitionRefs()).isEmpty();
    assertThat(tweety.getRelationshipDefinitionRefElements()).isEmpty();
    // should affect animal relationship definitions
    assertThat(tweety.getRelationshipDefinitions()).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGetBestFriends(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<Animal> bestFriends = tweety.getBestFriends();

    assertThat(bestFriends).isNotEmpty().hasSize(2).containsOnly(birdo, plucky);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddBestFriend(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getBestFriends().add(daisy);

    final Collection<Animal> bestFriends = tweety.getBestFriends();

    assertThat(bestFriends).isNotEmpty().hasSize(3).containsOnly(birdo, plucky, daisy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRemoveBestFriendRef(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getBestFriends().remove(plucky);

    final Collection<Animal> bestFriends = tweety.getBestFriends();

    assertThat(bestFriends).isNotEmpty().hasSize(1).containsOnly(birdo);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearBestFriendRef(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getBestFriends().clear();

    final Collection<Animal> bestFriends = tweety.getBestFriends();

    assertThat(bestFriends).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearAndAddBestFriendRef(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getBestFriends().clear();

    final Collection<Animal> bestFriends = tweety.getBestFriends();

    assertThat(bestFriends).isEmpty();

    bestFriends.add(daisy);

    assertThat(bestFriends).hasSize(1).containsOnly(daisy);
  }
}
