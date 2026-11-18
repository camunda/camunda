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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.ModelInstanceImpl;
import org.camunda.bpm.model.xml.impl.parser.AbstractModelParser;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Sebastian Menski
 */
public class UnknownAnimalTest {

  private AbstractModelParser modelParser;
  private ModelInstance modelInstance;
  private ModelElementInstance wanda;
  private ModelElementInstance flipper;

  @BeforeEach
  public void parseModel() {
    modelParser = new TestModelParser();
    final String testXml = this.getClass().getSimpleName() + ".xml";
    final InputStream testXmlAsStream = this.getClass().getResourceAsStream(testXml);
    modelInstance = modelParser.parseModelFromStream(testXmlAsStream);
    wanda = modelInstance.getModelElementById("wanda");
    flipper = modelInstance.getModelElementById("flipper");
  }

  @AfterEach
  public void validateModel() {
    final DomDocument document = modelInstance.getDocument();
    modelParser.validateModel(document);
  }

  @Test
  public void testGetUnknownAnimalById() {
    assertThat(wanda).isNotNull();
    assertThat(wanda.getAttributeValue("id")).isEqualTo("wanda");
    assertThat(wanda.getAttributeValue("gender")).isEqualTo("Female");
    assertThat(wanda.getAttributeValue("species")).isEqualTo("fish");

    assertThat(flipper).isNotNull();
    assertThat(flipper.getAttributeValue("id")).isEqualTo("flipper");
    assertThat(flipper.getAttributeValue("gender")).isEqualTo("Male");
    assertThat(flipper.getAttributeValue("species")).isEqualTo("dolphin");
  }

  @Test
  public void testGetUnknownAnimalByType() {
    final ModelInstanceImpl modelInstanceImpl = (ModelInstanceImpl) modelInstance;
    final ModelElementType unknownAnimalType =
        modelInstanceImpl.registerGenericType(MODEL_NAMESPACE, "unknownAnimal");
    final List<ModelElementInstance> unknownAnimals =
        new ArrayList<ModelElementInstance>(
            modelInstance.getModelElementsByType(unknownAnimalType));
    assertThat(unknownAnimals).hasSize(2);

    final ModelElementInstance wanda = unknownAnimals.get(0);
    assertThat(wanda.getAttributeValue("id")).isEqualTo("wanda");
    assertThat(wanda.getAttributeValue("gender")).isEqualTo("Female");
    assertThat(wanda.getAttributeValue("species")).isEqualTo("fish");

    final ModelElementInstance flipper = unknownAnimals.get(1);
    assertThat(flipper.getAttributeValue("id")).isEqualTo("flipper");
    assertThat(flipper.getAttributeValue("gender")).isEqualTo("Male");
    assertThat(flipper.getAttributeValue("species")).isEqualTo("dolphin");
  }

  @Test
  public void testAddUnknownAnimal() {
    final ModelInstanceImpl modelInstanceImpl = (ModelInstanceImpl) modelInstance;
    final ModelElementType unknownAnimalType =
        modelInstanceImpl.registerGenericType(MODEL_NAMESPACE, "unknownAnimal");
    final ModelElementType animalsType = modelInstance.getModel().getType(Animals.class);
    final ModelElementType animalType = modelInstance.getModel().getType(Animal.class);

    final ModelElementInstance unknownAnimal = modelInstance.newInstance(unknownAnimalType);
    assertThat(unknownAnimal).isNotNull();
    unknownAnimal.setAttributeValue("id", "new-animal", true);
    unknownAnimal.setAttributeValue("gender", "Unknown");
    unknownAnimal.setAttributeValue("species", "unknown");

    final ModelElementInstance animals =
        modelInstance.getModelElementsByType(animalsType).iterator().next();
    final List<ModelElementInstance> childElementsByType =
        new ArrayList<ModelElementInstance>(animals.getChildElementsByType(animalType));
    animals.insertElementAfter(unknownAnimal, childElementsByType.get(2));
    assertThat(animals.getChildElementsByType(unknownAnimalType)).hasSize(3);
  }

  @Test
  public void testGetUnknownAttribute() {
    assertThat(flipper.getAttributeValue("famous")).isEqualTo("true");

    assertThat(wanda.getAttributeValue("famous")).isNotEqualTo("true");
    wanda.setAttributeValue("famous", "true");
    assertThat(wanda.getAttributeValue("famous")).isEqualTo("true");
  }

  @Test
  public void testAddRelationshipDefinitionToUnknownAnimal() {
    final RelationshipDefinition friendRelationshipDefinition =
        modelInstance.newInstance(FriendRelationshipDefinition.class);
    friendRelationshipDefinition.setId("friend-relationship");
    friendRelationshipDefinition.setAttributeValue("animalRef", flipper.getAttributeValue("id"));

    try {
      wanda.addChildElement(friendRelationshipDefinition);
      fail("Cannot add relationship definition to UnknownAnimal cause no child types are defined");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ModelException.class);
    }

    wanda.insertElementAfter(friendRelationshipDefinition, null);

    final Animal tweety = modelInstance.getModelElementById("tweety");
    final RelationshipDefinition childRelationshipDefinition =
        modelInstance.newInstance(ChildRelationshipDefinition.class);
    childRelationshipDefinition.setId("child-relationship");
    childRelationshipDefinition.setAnimal(tweety);

    wanda.insertElementAfter(childRelationshipDefinition, friendRelationshipDefinition);
  }

  @Test
  public void testAddChildToUnknownAnimal() {
    assertThat(wanda.getChildElementsByType(flipper.getElementType())).hasSize(0);
    wanda.insertElementAfter(flipper, null);
    assertThat(wanda.getChildElementsByType(flipper.getElementType())).hasSize(1);
  }

  @Test
  public void testRemoveChildOfUnknownAnimal() {
    assertThat(wanda.removeChildElement(flipper)).isFalse();
    wanda.insertElementAfter(flipper, null);
    assertThat(wanda.removeChildElement(flipper)).isTrue();
    assertThat(wanda.getChildElementsByType(flipper.getElementType())).isEmpty();
  }

  @Test
  public void testReplaceChildOfUnknownAnimal() {
    final ModelElementInstance yogi = modelInstance.newInstance(flipper.getElementType());
    yogi.setAttributeValue("id", "yogi-bear", true);
    yogi.setAttributeValue("gender", "Male");
    yogi.setAttributeValue("species", "bear");

    assertThat(wanda.getChildElementsByType(flipper.getElementType())).isEmpty();
    wanda.insertElementAfter(flipper, null);
    assertThat(wanda.getChildElementsByType(flipper.getElementType())).hasSize(1);
    wanda.replaceChildElement(flipper, yogi);
    assertThat(wanda.getChildElementsByType(flipper.getElementType())).hasSize(1);
    assertThat(wanda.getChildElementsByType(flipper.getElementType()).iterator().next())
        .isEqualTo(yogi);
  }
}
