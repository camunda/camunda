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
package org.camunda.bpm.model.xml.testmodel;

import java.io.InputStream;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.parser.AbstractModelParser;
import org.camunda.bpm.model.xml.testmodel.instance.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;

/**
 * @author Sebastian Menski
 */
public abstract class TestModelTest {

  private static final AbstractModelParser MODEL_PARSER = new TestModelParser();

  // cloned model instance for every test method (see subclasses)
  protected ModelInstance modelInstance;

  protected static Object[] parseModel(Class<?> test) {
    final TestModelParser modelParser = new TestModelParser();
    final String testXml = test.getSimpleName() + ".xml";
    final InputStream testXmlAsStream = test.getResourceAsStream(testXml);
    final ModelInstance modelInstance = modelParser.parseModelFromStream(testXmlAsStream);
    return new Object[] {Named.of("parsed", modelInstance)};
  }

  public static Bird createBird(ModelInstance modelInstance, String id, Gender gender) {
    final Bird bird = modelInstance.newInstance(Bird.class, id);
    bird.setGender(gender);
    final Animals animals = (Animals) modelInstance.getDocumentElement();
    animals.getAnimals().add(bird);
    return bird;
  }

  protected static RelationshipDefinition createRelationshipDefinition(
      ModelInstance modelInstance,
      Animal animalInRelationshipWith,
      Class<? extends RelationshipDefinition> relationshipDefinitionClass) {
    final RelationshipDefinition relationshipDefinition =
        modelInstance.newInstance(
            relationshipDefinitionClass, "relationship-" + animalInRelationshipWith.getId());
    relationshipDefinition.setAnimal(animalInRelationshipWith);
    return relationshipDefinition;
  }

  public static void addRelationshipDefinition(
      Animal animalWithRelationship, RelationshipDefinition relationshipDefinition) {
    final Animal animalInRelationshipWith = relationshipDefinition.getAnimal();
    relationshipDefinition.setId(
        animalWithRelationship.getId() + "-" + animalInRelationshipWith.getId());
    animalWithRelationship.getRelationshipDefinitions().add(relationshipDefinition);
  }

  public static Egg createEgg(ModelInstance modelInstance, String id) {
    final Egg egg = modelInstance.newInstance(Egg.class, id);
    return egg;
  }

  @AfterEach
  public void validateModel() {
    MODEL_PARSER.validateModel(modelInstance.getDocument());
  }
}
