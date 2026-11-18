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

import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.parser.AbstractModelParser;
import org.camunda.bpm.model.xml.impl.util.StringUtil;
import org.camunda.bpm.model.xml.testmodel.Gender;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.TestModelTest;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.MODEL_NAMESPACE;
import static org.junit.runners.Parameterized.Parameters;

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

  public BirdTest(String testName, ModelInstance testModelInstance, AbstractModelParser modelParser) {
    super(testName, testModelInstance, modelParser);
  }

  @Parameters(name="Model {0}")
  public static Collection<Object[]> models() {
    Object[][] models = {createModel(), parseModel(BirdTest.class)};
    return Arrays.asList(models);
  }

  public static Object[] createModel() {
    TestModelParser modelParser = new TestModelParser();
    ModelInstance modelInstance = modelParser.getEmptyModel();

    Animals animals = modelInstance.newInstance(Animals.class);
    modelInstance.setDocumentElement(animals);

    // add a tns namespace prefix for QName testing
    animals.getDomElement().registerNamespace("tns", MODEL_NAMESPACE);

    Bird tweety = createBird(modelInstance, "tweety", Gender.Female);
    Bird hedwig = createBird(modelInstance, "hedwig", Gender.Female);
    Bird timmy = createBird(modelInstance, "timmy", Gender.Female);
    Egg egg1 = createEgg(modelInstance, "egg1");
    egg1.setMother(tweety);
    Collection<Animal> guards = egg1.getGuardians();
    guards.add(hedwig);
    guards.add(timmy);
    Egg egg2 = createEgg(modelInstance, "egg2");
    egg2.setMother(tweety);
    guards = egg2.getGuardians();
    guards.add(hedwig);
    guards.add(timmy);
    Egg egg3 = createEgg(modelInstance, "egg3");
    guards = egg3.getGuardians();
    guards.add(timmy);

    tweety.setSpouse(hedwig);
    tweety.getEggs().add(egg1);
    tweety.getEggs().add(egg2);
    tweety.getEggs().add(egg3);

    Collection<Egg> guardedEggs = hedwig.getGuardedEggs();
    guardedEggs.add(egg1);
    guardedEggs.add(egg2);

    GuardEgg guardEgg = modelInstance.newInstance(GuardEgg.class);
    guardEgg.setTextContent(egg1.getId() + " " + egg2.getId());
    timmy.getGuardedEggRefs().add(guardEgg);
    timmy.getGuardedEggs().add(egg3);

    return new Object[]{"created", modelInstance, modelParser};
  }

  @Before
  public void copyModelInstance() {
    modelInstance = cloneModelInstance();
    tweety = modelInstance.getModelElementById("tweety");
    hedwig = modelInstance.getModelElementById("hedwig");
    timmy = modelInstance.getModelElementById("timmy");
    egg1 = modelInstance.getModelElementById("egg1");
    egg2 = modelInstance.getModelElementById("egg2");
    egg3 = modelInstance.getModelElementById("egg3");
  }

  @Test
  public void testAddEggsByHelper() {
    assertThat(tweety.getEggs())
      .isNotEmpty()
      .hasSize(3)
      .containsOnly(egg1, egg2, egg3);

    Egg egg4 = createEgg(modelInstance, "egg4");
    tweety.getEggs().add(egg4);
    Egg egg5 = createEgg(modelInstance, "egg5");
    tweety.getEggs().add(egg5);

    assertThat(tweety.getEggs())
      .hasSize(5)
      .containsOnly(egg1, egg2, egg3, egg4, egg5);
  }

  @Test
  public void testUpdateEggsByIdByHelper() {
    egg1.setId("new-" + egg1.getId());
    egg2.setId("new-" + egg2.getId());
    assertThat(tweety.getEggs())
      .hasSize(3)
      .containsOnly(egg1, egg2, egg3);
  }

  @Test
  public void testUpdateEggsByIdByAttributeName() {
    egg1.setAttributeValue("id", "new-" + egg1.getId(), true);
    egg2.setAttributeValue("id", "new-" + egg2.getId(), true);
    assertThat(tweety.getEggs())
      .hasSize(3)
      .containsOnly(egg1, egg2, egg3);
  }

  @Test
  public void testUpdateEggsByReplaceElements() {
    Egg egg4 = createEgg(modelInstance, "egg4");
    Egg egg5 = createEgg(modelInstance, "egg5");
    egg1.replaceWithElement(egg4);
    egg2.replaceWithElement(egg5);
    assertThat(tweety.getEggs())
      .hasSize(3)
      .containsOnly(egg3, egg4, egg5);
  }

  @Test
  public void testUpdateEggsByRemoveElement() {
    tweety.getEggs().remove(egg1);
    assertThat(tweety.getEggs())
      .hasSize(2)
      .containsOnly(egg2, egg3);
  }

  @Test
  public void testClearEggs() {
    tweety.getEggs().clear();
    assertThat(tweety.getEggs())
      .isEmpty();
  }

  @Test
  public void testSetSpouseRefByHelper() {
    tweety.setSpouse(timmy);
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @Test
  public void testUpdateSpouseByIdHelper() {
    hedwig.setId("new-" + hedwig.getId());
    assertThat(tweety.getSpouse()).isEqualTo(hedwig);
  }

  @Test
  public void testUpdateSpouseByIdByAttributeName() {
    hedwig.setAttributeValue("id", "new-" + hedwig.getId(), true);
    assertThat(tweety.getSpouse()).isEqualTo(hedwig);
  }

  @Test
  public void testUpdateSpouseByReplaceElement() {
    hedwig.replaceWithElement(timmy);
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @Test
  public void testUpdateSpouseByRemoveElement() {
    Animals animals = (Animals) modelInstance.getDocumentElement();
    animals.getAnimals().remove(hedwig);
    assertThat(tweety.getSpouse()).isNull();
  }

  @Test
  public void testClearSpouse() {
    tweety.removeSpouse();
    assertThat(tweety.getSpouse()).isNull();
  }

  @Test
  public void testSetSpouseRefsByHelper() {
    SpouseRef spouseRef = modelInstance.newInstance(SpouseRef.class);
    spouseRef.setTextContent(timmy.getId());
    tweety.getSpouseRef().replaceWithElement(spouseRef);
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @Test
  public void testSpouseRefsByTextContent() {
    SpouseRef spouseRef = tweety.getSpouseRef();
    assertThat(spouseRef.getTextContent()).isEqualTo(hedwig.getId());
  }

  @Test
  public void testUpdateSpouseRefsByTextContent() {
    SpouseRef spouseRef = tweety.getSpouseRef();
    spouseRef.setTextContent(timmy.getId());
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @Test
  public void testUpdateSpouseRefsByTextContentWithNamespace() {
    SpouseRef spouseRef = tweety.getSpouseRef();
    spouseRef.setTextContent("tns:" + timmy.getId());
    assertThat(tweety.getSpouse()).isEqualTo(timmy);
  }

  @Test
  public void testGetMother() {
    Animal mother = egg1.getMother();
    assertThat(mother).isEqualTo(tweety);

    mother = egg2.getMother();
    assertThat(mother).isEqualTo(tweety);
  }

  @Test
  public void testSetMotherRefByHelper() {
    egg1.setMother(timmy);
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @Test
  public void testUpdateMotherByIdHelper() {
    tweety.setId("new-" + tweety.getId());
    assertThat(egg1.getMother()).isEqualTo(tweety);
  }

  @Test
  public void testUpdateMotherByIdByAttributeName() {
    tweety.setAttributeValue("id", "new-" + tweety.getId(), true);
    assertThat(egg1.getMother()).isEqualTo(tweety);
  }

  @Test
  public void testUpdateMotherByReplaceElement() {
    tweety.replaceWithElement(timmy);
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @Test
  public void testUpdateMotherByRemoveElement() {
    egg1.setMother(hedwig);
    Animals animals = (Animals) modelInstance.getDocumentElement();
    animals.getAnimals().remove(hedwig);
    assertThat(egg1.getMother()).isNull();
  }

  @Test
  public void testClearMother() {
    egg1.removeMother();
    assertThat(egg1.getMother()).isNull();
  }

  @Test
  public void testSetMotherRefsByHelper() {
    Mother mother = modelInstance.newInstance(Mother.class);
    mother.setHref("#" + timmy.getId());
    egg1.getMotherRef().replaceWithElement(mother);
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @Test
  public void testMotherRefsByTextContent() {
    Mother mother = egg1.getMotherRef();
    assertThat(mother.getHref()).isEqualTo("#" + tweety.getId());
  }

  @Test
  public void testUpdateMotherRefsByTextContent() {
    Mother mother = egg1.getMotherRef();
    mother.setHref("#" + timmy.getId());
    assertThat(egg1.getMother()).isEqualTo(timmy);
  }

  @Test
  public void testGetGuards() {
    Collection<Animal> guards = egg1.getGuardians();
    assertThat(guards).isNotEmpty().hasSize(2);
    assertThat(guards).contains(hedwig, timmy);

    guards = egg2.getGuardians();
    assertThat(guards).isNotEmpty().hasSize(2);
    assertThat(guards).contains(hedwig, timmy);
  }

  @Test
  public void testAddGuardianRefsByHelper() {
    assertThat(egg1.getGuardianRefs())
      .isNotEmpty()
      .hasSize(2);

    Guardian tweetyGuardian = modelInstance.newInstance(Guardian.class);
    tweetyGuardian.setHref("#" + tweety.getId());
    egg1.getGuardianRefs().add(tweetyGuardian);

    assertThat(egg1.getGuardianRefs())
      .isNotEmpty()
      .hasSize(3)
      .contains(tweetyGuardian);
  }

  @Test
  public void testGuardianRefsByTextContent() {
    Collection<Guardian> guardianRefs = egg1.getGuardianRefs();
    Collection<String> hrefs = new ArrayList<String>();
    for (Guardian guardianRef : guardianRefs) {
      String href = guardianRef.getHref();
      assertThat(href).isNotEmpty();
      hrefs.add(href);
    }
    assertThat(hrefs)
      .isNotEmpty()
      .hasSize(2)
      .containsOnly("#" + hedwig.getId(), "#" + timmy.getId());
  }

  @Test
  public void testUpdateGuardianRefsByTextContent() {
    List<Guardian> guardianRefs = new ArrayList<Guardian>(egg1.getGuardianRefs());

    guardianRefs.get(0).setHref("#" + tweety.getId());

    assertThat(egg1.getGuardians())
      .hasSize(2)
      .containsOnly(tweety, timmy);
  }

  @Test
  public void testUpdateGuardianRefsByRemoveElements() {
    List<Guardian> guardianRefs = new ArrayList<Guardian>(egg1.getGuardianRefs());
    egg1.getGuardianRefs().remove(guardianRefs.get(1));
    assertThat(egg1.getGuardians())
      .hasSize(1)
      .containsOnly(hedwig);
  }

  @Test
  public void testClearGuardianRefs() {
    egg1.getGuardianRefs().clear();
    assertThat(egg1.getGuardianRefs()).isEmpty();

    // should not affect animals collection
    Animals animals = (Animals) modelInstance.getDocumentElement();
    assertThat(animals.getAnimals())
      .isNotEmpty()
      .hasSize(3);
  }

  @Test
  public void testGetGuardedEggs() {
    Collection<Egg> guardedEggs = hedwig.getGuardedEggs();
    assertThat(guardedEggs)
      .isNotEmpty()
      .hasSize(2)
      .contains(egg1, egg2);

    guardedEggs = timmy.getGuardedEggs();
    assertThat(guardedEggs)
      .isNotEmpty()
      .hasSize(3)
      .contains(egg1, egg2);
  }

  @Test
  public void testAddGuardedEggRefsByHelper() {
    assertThat(hedwig.getGuardedEggRefs())
      .isNotEmpty()
      .hasSize(2);

    GuardEgg egg3GuardedEgg = modelInstance.newInstance(GuardEgg.class);
    egg3GuardedEgg.setTextContent(egg3.getId());
    hedwig.getGuardedEggRefs().add(egg3GuardedEgg);

    assertThat(hedwig.getGuardedEggRefs())
      .isNotEmpty()
      .hasSize(3)
      .contains(egg3GuardedEgg);
  }

  @Test
  public void testGuardedEggRefsByTextContent() {
    Collection<GuardEgg> guardianRefs = timmy.getGuardedEggRefs();
    Collection<String> textContents = new ArrayList<String>();
    for (GuardEgg guardianRef : guardianRefs) {
      String textContent = guardianRef.getTextContent();
      assertThat(textContent).isNotEmpty();
      textContents.addAll(StringUtil.splitListBySeparator(textContent, " "));
    }
    assertThat(textContents)
      .isNotEmpty()
      .hasSize(3)
      .containsOnly(egg1.getId(), egg2.getId(), egg3.getId());
  }

  @Test
  public void testUpdateGuardedEggRefsByTextContent() {
    List<GuardEgg> guardianRefs = new ArrayList<GuardEgg>(hedwig.getGuardedEggRefs());

    guardianRefs.get(0).setTextContent(egg1.getId() + " " + egg3.getId());

    assertThat(hedwig.getGuardedEggs())
      .hasSize(3)
      .containsOnly(egg1, egg2, egg3);
  }

  @Test
  public void testUpdateGuardedEggRefsByRemoveElements() {
    List<GuardEgg> guardianRefs = new ArrayList<GuardEgg>(timmy.getGuardedEggRefs());
    timmy.getGuardedEggRefs().remove(guardianRefs.get(0));
    assertThat(timmy.getGuardedEggs())
      .hasSize(1)
      .containsOnly(egg3);
  }

  @Test
  public void testClearGuardedEggRefs() {
    timmy.getGuardedEggRefs().clear();
    assertThat(timmy.getGuardedEggRefs()).isEmpty();

    // should not affect animals collection
    Animals animals = (Animals) modelInstance.getDocumentElement();
    assertThat(animals.getAnimals())
      .isNotEmpty()
      .hasSize(3);
  }

}
