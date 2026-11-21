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
import org.camunda.bpm.model.xml.impl.util.StringUtil;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Sebastian Menski
 */
public class BirdTest extends TestModelTest {

  private Bird tweety;
  private Bird hedwig;
  private Bird timmy;
  private Egg egg1;
  private Egg egg2;
  private Egg egg3;

  public static Collection<Object[]> models() {
    final Object[][] models = {createModel(), parseModel(BirdTest.class)};
    return Arrays.asList(models);
  }

  public static Object[] createModel() {
    final TestModelParser modelParser = new TestModelParser();
    final ModelInstance modelInstance = modelParser.getEmptyModel();

    final Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    // add a tns namespace prefix for QName testing
    animals.getDomElement().registerNamespace("tns", MODEL_NAMESPACE);

    final Bird tweety = createBird(modelInstance, "tweety", Gender.Female);
    final Bird hedwig = createBird(modelInstance, "hedwig", Gender.Female);
    final Bird timmy = createBird(modelInstance, "timmy", Gender.Female);
    final Egg egg1 = createEgg(modelInstance, "egg1");
    egg1.setMother(tweety);
    Collection<Animal> guards = egg1.getGuardians();
    guards.add(hedwig);
    guards.add(timmy);
    final Egg egg2 = createEgg(modelInstance, "egg2");
    egg2.setMother(tweety);
    guards = egg2.getGuardians();
    guards.add(hedwig);
    guards.add(timmy);
    final Egg egg3 = createEgg(modelInstance, "egg3");
    guards = egg3.getGuardians();
    guards.add(timmy);

    tweety.setSpouse(hedwig);
    tweety.getEggs().add(egg1);
    tweety.getEggs().add(egg2);
    tweety.getEggs().add(egg3);

    final Collection<Egg> guardedEggs = hedwig.getGuardedEggs();
    guardedEggs.add(egg1);
    guardedEggs.add(egg2);

    final GuardEgg guardEgg = modelInstance.newInstance(GuardEgg.class);
    guardEgg.setTextContent(egg1.getId() + " " + egg2.getId());
    timmy.getGuardedEggRefs().add(guardEgg);
    timmy.getGuardedEggs().add(egg3);

    return new Object[] {Named.of("created", modelInstance)};
  }

  private void copyModelInstance(final ModelInstance testModelInstance) {
    modelInstance = testModelInstance.clone();
    tweety = modelInstance.getModelElementById("tweety");
    hedwig = modelInstance.getModelElementById("hedwig");
    timmy = modelInstance.getModelElementById("timmy");
    egg1 = modelInstance.getModelElementById("egg1");
    egg2 = modelInstance.getModelElementById("egg2");
    egg3 = modelInstance.getModelElementById("egg3");
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddEggsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(tweety.getEggs()).isNotEmpty().hasSize(3).containsOnly(egg1, egg2, egg3);

    final Egg egg4 = createEgg(modelInstance, "egg4");
    tweety.getEggs().add(egg4);
    final Egg egg5 = createEgg(modelInstance, "egg5");
    tweety.getEggs().add(egg5);

    assertThat(tweety.getEggs()).hasSize(5).containsOnly(egg1, egg2, egg3, egg4, egg5);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateEggsByIdByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    egg1.setId("new-" + egg1.getId());
    egg2.setId("new-" + egg2.getId());
    assertThat(tweety.getEggs()).hasSize(3).containsOnly(egg1, egg2, egg3);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateEggsByIdByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    egg1.setAttributeValue("id", "new-" + egg1.getId(), true);
    egg2.setAttributeValue("id", "new-" + egg2.getId(), true);
    assertThat(tweety.getEggs()).hasSize(3).containsOnly(egg1, egg2, egg3);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateEggsByReplaceElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Egg egg4 = createEgg(modelInstance, "egg4");
    final Egg egg5 = createEgg(modelInstance, "egg5");
    egg1.replaceWithElement(egg4);
    egg2.replaceWithElement(egg5);
    assertThat(tweety.getEggs()).hasSize(3).containsOnly(egg3, egg4, egg5);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateEggsByRemoveElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getEggs().remove(egg1);
    assertThat(tweety.getEggs()).hasSize(2).containsOnly(egg2, egg3);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearEggs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.getEggs().clear();
    assertThat(tweety.getEggs()).isEmpty();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetSpouseRefByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setSpouse(timmy);
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateSpouseByIdHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.setId("new-" + hedwig.getId());
    assertThat(tweety.getSpouse()).isEqualTo(hedwig);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateSpouseByIdByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.setAttributeValue("id", "new-" + hedwig.getId(), true);
    assertThat(tweety.getSpouse()).isEqualTo(hedwig);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateSpouseByReplaceElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    hedwig.replaceWithElement(timmy);
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateSpouseByRemoveElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Animals animals = (Animals) modelInstance.getDocumentElement();
    animals.getAnimals().remove(hedwig);
    assertThat(tweety.getSpouse()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearSpouse(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.removeSpouse();
    assertThat(tweety.getSpouse()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetSpouseRefsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final SpouseRef spouseRef = modelInstance.newInstance(SpouseRef.class);
    spouseRef.setTextContent(timmy.getId());
    tweety.getSpouseRef().replaceWithElement(spouseRef);
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSpouseRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final SpouseRef spouseRef = tweety.getSpouseRef();
    assertThat(spouseRef.getTextContent()).isEqualTo(hedwig.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateSpouseRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final SpouseRef spouseRef = tweety.getSpouseRef();
    spouseRef.setTextContent(timmy.getId());
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateSpouseRefsByTextContentWithNamespace(
      final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final SpouseRef spouseRef = tweety.getSpouseRef();
    spouseRef.setTextContent("tns:" + timmy.getId());
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGetMother(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    Animal mother = egg1.getMother();
    assertThat(mother).isEqualTo(tweety);

    mother = egg2.getMother();
    assertThat(mother).isEqualTo(tweety);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetMotherRefByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    egg1.setMother(timmy);
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateMotherByIdHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setId("new-" + tweety.getId());
    assertThat(egg1.getMother()).isEqualTo(tweety);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateMotherByIdByAttributeName(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.setAttributeValue("id", "new-" + tweety.getId(), true);
    assertThat(egg1.getMother()).isEqualTo(tweety);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateMotherByReplaceElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    tweety.replaceWithElement(timmy);
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateMotherByRemoveElement(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    egg1.setMother(hedwig);
    final Animals animals = (Animals) modelInstance.getDocumentElement();
    animals.getAnimals().remove(hedwig);
    assertThat(egg1.getMother()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearMother(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    egg1.removeMother();
    assertThat(egg1.getMother()).isNull();
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testSetMotherRefsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Mother mother = modelInstance.newInstance(Mother.class);
    mother.setHref("#" + timmy.getId());
    egg1.getMotherRef().replaceWithElement(mother);
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testMotherRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Mother mother = egg1.getMotherRef();
    assertThat(mother.getHref()).isEqualTo("#" + tweety.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateMotherRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Mother mother = egg1.getMotherRef();
    mother.setHref("#" + timmy.getId());
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGetGuards(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    Collection<Animal> guards = egg1.getGuardians();
    assertThat(guards).isNotEmpty().hasSize(2);
    assertThat(guards).contains(hedwig, timmy);

    guards = egg2.getGuardians();
    assertThat(guards).isNotEmpty().hasSize(2);
    assertThat(guards).contains(hedwig, timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddGuardianRefsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(egg1.getGuardianRefs()).isNotEmpty().hasSize(2);

    final Guardian tweetyGuardian = modelInstance.newInstance(Guardian.class);
    tweetyGuardian.setHref("#" + tweety.getId());
    egg1.getGuardianRefs().add(tweetyGuardian);

    assertThat(egg1.getGuardianRefs()).isNotEmpty().hasSize(3).contains(tweetyGuardian);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGuardianRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<Guardian> guardianRefs = egg1.getGuardianRefs();
    final Collection<String> hrefs = new ArrayList<String>();
    for (Guardian guardianRef : guardianRefs) {
      final String href = guardianRef.getHref();
      assertThat(href).isNotEmpty();
      hrefs.add(href);
    }
    assertThat(hrefs)
        .isNotEmpty()
        .hasSize(2)
        .containsOnly("#" + hedwig.getId(), "#" + timmy.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateGuardianRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<Guardian> guardianRefs = new ArrayList<Guardian>(egg1.getGuardianRefs());

    guardianRefs.get(0).setHref("#" + tweety.getId());

    assertThat(egg1.getGuardians()).hasSize(2).containsOnly(tweety, timmy);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateGuardianRefsByRemoveElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<Guardian> guardianRefs = new ArrayList<Guardian>(egg1.getGuardianRefs());
    egg1.getGuardianRefs().remove(guardianRefs.get(1));
    assertThat(egg1.getGuardians()).hasSize(1).containsOnly(hedwig);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearGuardianRefs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    egg1.getGuardianRefs().clear();
    assertThat(egg1.getGuardianRefs()).isEmpty();

    // should not affect animals collection
    final Animals animals = (Animals) modelInstance.getDocumentElement();
    assertThat(animals.getAnimals()).isNotEmpty().hasSize(3);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGetGuardedEggs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    Collection<Egg> guardedEggs = hedwig.getGuardedEggs();
    assertThat(guardedEggs).isNotEmpty().hasSize(2).contains(egg1, egg2);

    guardedEggs = timmy.getGuardedEggs();
    assertThat(guardedEggs).isNotEmpty().hasSize(3).contains(egg1, egg2);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testAddGuardedEggRefsByHelper(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    assertThat(hedwig.getGuardedEggRefs()).isNotEmpty().hasSize(2);

    final GuardEgg egg3GuardedEgg = modelInstance.newInstance(GuardEgg.class);
    egg3GuardedEgg.setTextContent(egg3.getId());
    hedwig.getGuardedEggRefs().add(egg3GuardedEgg);

    assertThat(hedwig.getGuardedEggRefs()).isNotEmpty().hasSize(3).contains(egg3GuardedEgg);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testGuardedEggRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final Collection<GuardEgg> guardianRefs = timmy.getGuardedEggRefs();
    final Collection<String> textContents = new ArrayList<String>();
    for (GuardEgg guardianRef : guardianRefs) {
      final String textContent = guardianRef.getTextContent();
      assertThat(textContent).isNotEmpty();
      textContents.addAll(StringUtil.splitListBySeparator(textContent, " "));
    }
    assertThat(textContents)
        .isNotEmpty()
        .hasSize(3)
        .containsOnly(egg1.getId(), egg2.getId(), egg3.getId());
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateGuardedEggRefsByTextContent(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<GuardEgg> guardianRefs = new ArrayList<GuardEgg>(hedwig.getGuardedEggRefs());

    guardianRefs.get(0).setTextContent(egg1.getId() + " " + egg3.getId());

    assertThat(hedwig.getGuardedEggs()).hasSize(3).containsOnly(egg1, egg2, egg3);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testUpdateGuardedEggRefsByRemoveElements(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    final List<GuardEgg> guardianRefs = new ArrayList<GuardEgg>(timmy.getGuardedEggRefs());
    timmy.getGuardedEggRefs().remove(guardianRefs.get(0));
    assertThat(timmy.getGuardedEggs()).hasSize(1).containsOnly(egg3);
  }

  @ParameterizedTest(name = "Model {0}")
  @MethodSource("models")
  public void testClearGuardedEggRefs(final ModelInstance testModelInstance) {
    copyModelInstance(testModelInstance);

    timmy.getGuardedEggRefs().clear();
    assertThat(timmy.getGuardedEggRefs()).isEmpty();

    // should not affect animals collection
    final Animals animals = (Animals) modelInstance.getDocumentElement();
    assertThat(animals.getAnimals()).isNotEmpty().hasSize(3);
  }
}
