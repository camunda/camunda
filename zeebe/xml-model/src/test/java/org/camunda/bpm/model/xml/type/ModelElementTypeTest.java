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
package org.camunda.bpm.model.xml.type;

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.util.ModelTypeException;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.instance.*;
import org.junit.Before;
import org.junit.Test;

import static org.camunda.bpm.model.xml.test.assertions.ModelAssertions.assertThat;
import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.MODEL_NAMESPACE;
import static org.junit.Assert.fail;

/**
 * @author Sebastian Menski
 */
public class ModelElementTypeTest {

  private ModelInstance modelInstance;
  private Model model;
  private ModelElementType animalsType;
  private ModelElementType animalType;
  private ModelElementType flyingAnimalType;
  private ModelElementType birdType;

  @Before
  public void getTypes() {
    TestModelParser modelParser = new TestModelParser();
    modelInstance = modelParser.getEmptyModel();
    model = modelInstance.getModel();
    animalsType = model.getType(Animals.class);
    animalType = model.getType(Animal.class);
    flyingAnimalType = model.getType(FlyingAnimal.class);
    birdType = model.getType(Bird.class);
  }

  @Test
  public void testTypeName() {
    assertThat(animalsType).hasTypeName("animals");
    assertThat(animalType).hasTypeName("animal");
    assertThat(flyingAnimalType).hasTypeName("flyingAnimal");
    assertThat(birdType).hasTypeName("bird");
  }

  @Test
  public void testTypeNamespace() {
    assertThat(animalsType).hasTypeNamespace(MODEL_NAMESPACE);
    assertThat(animalType).hasTypeNamespace(MODEL_NAMESPACE);
    assertThat(flyingAnimalType).hasTypeNamespace(MODEL_NAMESPACE);
    assertThat(birdType).hasTypeNamespace(MODEL_NAMESPACE);
  }

  @Test
  public void testInstanceType() {
    assertThat(animalsType).hasInstanceType(Animals.class);
    assertThat(animalType).hasInstanceType(Animal.class);
    assertThat(flyingAnimalType).hasInstanceType(FlyingAnimal.class);
    assertThat(birdType).hasInstanceType(Bird.class);
  }

  @Test
  public void testAttributes() {
    assertThat(animalsType).hasNoAttributes();
    assertThat(animalType).hasAttributes("id", "name", "father", "mother", "isEndangered", "gender", "age");
    assertThat(flyingAnimalType).hasAttributes("wingspan");
    assertThat(birdType).hasAttributes("canHazExtendedWings");
  }

  @Test
  public void testBaseType() {
    assertThat(animalsType).extendsNoType();
    assertThat(animalType).extendsNoType();
    assertThat(flyingAnimalType).extendsType(animalType);
    assertThat(birdType).extendsType(flyingAnimalType);
  }

  @Test
  public void testAbstractType() {
    assertThat(animalsType).isNotAbstract();
    assertThat(animalType).isAbstract();
    assertThat(flyingAnimalType).isAbstract();
    assertThat(birdType).isNotAbstract();
  }

  @Test
  public void testExtendingTypes() {
    assertThat(animalsType).isNotExtended();
    assertThat(animalType)
      .isExtendedBy(flyingAnimalType)
      .isNotExtendedBy(birdType);
    assertThat(flyingAnimalType).isExtendedBy(birdType);
    assertThat(birdType).isNotExtended();
  }

  @Test
  public void testModel() {
    assertThat(animalsType).isPartOfModel(model);
    assertThat(animalType).isPartOfModel(model);
    assertThat(flyingAnimalType).isPartOfModel(model);
    assertThat(birdType).isPartOfModel(model);
  }

  @Test
  public void testInstances() {
    assertThat(animalsType.getInstances(modelInstance)).isEmpty();
    assertThat(animalType.getInstances(modelInstance)).isEmpty();
    assertThat(flyingAnimalType.getInstances(modelInstance)).isEmpty();
    assertThat(birdType.getInstances(modelInstance)).isEmpty();

    Animals animals = (Animals) animalsType.newInstance(modelInstance);
    modelInstance.setDocumentElement(animals);

    try {
      animalType.newInstance(modelInstance);
      fail("Animal is a abstract type and not instance can be created.");
    }
    catch (Exception e) {
      assertThat(e).isInstanceOf(ModelTypeException.class);
    }

    try {
      flyingAnimalType.newInstance(modelInstance);
      fail("Flying animal is a abstract type and not instance can be created.");
    }
    catch (Exception e) {
      assertThat(e).isInstanceOf(ModelTypeException.class);
    }

    animals.getAnimals().add((Animal) birdType.newInstance(modelInstance));
    animals.getAnimals().add((Animal) birdType.newInstance(modelInstance));
    animals.getAnimals().add((Animal) birdType.newInstance(modelInstance));

    assertThat(animalsType.getInstances(modelInstance)).hasSize(1);
    assertThat(animalType.getInstances(modelInstance)).isEmpty();
    assertThat(flyingAnimalType.getInstances(modelInstance)).isEmpty();
    assertThat(birdType.getInstances(modelInstance)).hasSize(3);
  }

  @Test
  public void testChildElementTypes() {
    ModelElementType relationshipDefinitionType = model.getType(RelationshipDefinition.class);
    ModelElementType relationshipDefinitionRefType = model.getType(RelationshipDefinitionRef.class);
    ModelElementType flightPartnerRefType = model.getType(FlightPartnerRef.class);
    ModelElementType eggType = model.getType(Egg.class);
    ModelElementType spouseRefType = model.getType(SpouseRef.class);

    assertThat(animalsType).hasChildElements(animalType);
    assertThat(animalType).hasChildElements(relationshipDefinitionType, relationshipDefinitionRefType);
    assertThat(flyingAnimalType).hasChildElements(flightPartnerRefType);
    assertThat(birdType).hasChildElements(eggType, spouseRefType);
  }

}
