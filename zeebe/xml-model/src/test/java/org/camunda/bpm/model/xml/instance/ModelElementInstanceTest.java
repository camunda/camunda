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
package org.camunda.bpm.model.xml.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.MODEL_NAMESPACE;

import java.util.Arrays;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.camunda.bpm.model.xml.testmodel.instance.Animals;
import org.camunda.bpm.model.xml.testmodel.instance.Bird;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class ModelElementInstanceTest extends TestModelTest {

  private Animals animals;
  private Bird tweety;
  private Bird donald;
  private Bird daisy;
  private Bird hedwig;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(ModelElementInstanceTest.class)};
    return Arrays.asList(models);
  }

  private static Object[] createModel() {
    final TestModelParser modelParser = new TestModelParser();
    final ModelInstance modelInstance = modelParser.getEmptyModel();

    final Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    createBird(modelInstance, "tweety", Gender.Female);
    final Bird donald = createBird(modelInstance, "donald", Gender.Male);
    final Bird daisy = createBird(modelInstance, "daisy", Gender.Female);
    final Bird hedwig = createBird(modelInstance, "hedwig", Gender.Male);

    donald.setTextContent("some text content");
    daisy.setTextContent("\n        some text content with outer line breaks\n    ");
    hedwig.setTextContent("\n        some text content with inner\n        line breaks\n    ");

    return new Object[] {Named.of("created", modelInstance)};
  }

  private void copyModelInstance(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();

    animals = (Animals) modelInstance.getDocumentElement();
    tweety = modelInstance.getModelElementById("tweety");
    donald = modelInstance.getModelElementById("donald");
    daisy = modelInstance.getModelElementById("daisy");
    hedwig = modelInstance.getModelElementById("hedwig");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAttribute(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String tweetyName = tweety.getId() + "-name";
    tweety.setAttributeValue("name", tweetyName);
    assertThat(tweety.getAttributeValue("name")).isEqualTo(tweetyName);
    tweety.removeAttribute("name");
    assertThat(tweety.getAttributeValue("name")).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAttributeWithNamespace(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final String tweetyName = tweety.getId() + "-name";
    tweety.setAttributeValueNs(MODEL_NAMESPACE, "name", tweetyName);
    assertThat(tweety.getAttributeValue("name")).isEqualTo(tweetyName);
    assertThat(tweety.getAttributeValueNs(MODEL_NAMESPACE, "name")).isEqualTo(tweetyName);
    tweety.removeAttributeNs(MODEL_NAMESPACE, "name");
    assertThat(tweety.getAttributeValue("name")).isNull();
    assertThat(tweety.getAttributeValueNs(MODEL_NAMESPACE, "name")).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testElementType(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final ModelElementType birdType = modelInstance.getModel().getType(Bird.class);
    assertThat(tweety.getElementType()).isEqualTo(birdType);
    assertThat(donald.getElementType()).isEqualTo(birdType);
    assertThat(daisy.getElementType()).isEqualTo(birdType);
    assertThat(hedwig.getElementType()).isEqualTo(birdType);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testParentElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getParentElement()).isEqualTo(animals);
    assertThat(donald.getParentElement()).isEqualTo(animals);
    assertThat(daisy.getParentElement()).isEqualTo(animals);
    assertThat(hedwig.getParentElement()).isEqualTo(animals);

    final Bird timmy = modelInstance.newInstance(Bird.class);
    timmy.setId("timmy");
    timmy.setGender(Gender.Male);
    assertThat(timmy.getParentElement()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testModelInstance(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getModelInstance()).isEqualTo(modelInstance);
    assertThat(donald.getModelInstance()).isEqualTo(modelInstance);
    assertThat(daisy.getModelInstance()).isEqualTo(modelInstance);
    assertThat(hedwig.getModelInstance()).isEqualTo(modelInstance);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReplaceWithElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Bird timmy = modelInstance.newInstance(Bird.class);
    timmy.setId("timmy");
    timmy.setGender(Gender.Male);

    assertThat(animals.getAnimals()).contains(tweety).doesNotContain(timmy);

    tweety.replaceWithElement(timmy);

    assertThat(animals.getAnimals()).contains(timmy).doesNotContain(tweety);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testReplaceRootElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(((Animals) modelInstance.getDocumentElement()).getAnimals()).isNotEmpty();
    final Animals newAnimals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(newAnimals);
    assertThat(((Animals) modelInstance.getDocumentElement()).getAnimals()).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getTextContent()).isEqualTo("");
    assertThat(donald.getTextContent()).isEqualTo("some text content");
    assertThat(daisy.getTextContent()).isEqualTo("some text content with outer line breaks");
    assertThat(hedwig.getTextContent())
        .isEqualTo("some text content with inner\n        line breaks");

    final String testContent = "\n test content \n \n \t camunda.org \t    \n   ";
    tweety.setTextContent(testContent);
    assertThat(tweety.getTextContent()).isEqualTo(testContent.trim());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testRawTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getRawTextContent()).isEqualTo("");
    assertThat(donald.getRawTextContent()).isEqualTo("some text content");
    assertThat(daisy.getRawTextContent())
        .isEqualTo("\n        some text content with outer line breaks\n    ");
    assertThat(hedwig.getRawTextContent())
        .isEqualTo("\n        some text content with inner\n        line breaks\n    ");

    final String testContent = "\n test content \n \n \t camunda.org \t    \n   ";
    tweety.setTextContent(testContent);
    assertThat(tweety.getRawTextContent()).isEqualTo(testContent);
  }
}
