/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package io.camunda.zeebe.model.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.instance.DataInputAssociation;
import io.camunda.zeebe.model.bpmn.instance.DataObject;
import io.camunda.zeebe.model.bpmn.instance.DataObjectReference;
import io.camunda.zeebe.model.bpmn.instance.DataOutputAssociation;
import io.camunda.zeebe.model.bpmn.instance.ItemAwareElement;
import io.camunda.zeebe.model.bpmn.instance.ItemDefinition;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Dario Campagna
 */
public class DataObjectsTest {

  private static BpmnModelInstance modelInstance;

  @BeforeClass
  public static void parseModel() {
    modelInstance =
        Bpmn.readModelFromStream(DataObjectsTest.class.getResourceAsStream("DataObjectTest.bpmn"));
  }

  @Test
  public void testGetDataObject() {
    final DataObject dataObject = modelInstance.getModelElementById("_21");
    final ItemDefinition itemDefinition = modelInstance.getModelElementById("_100");
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getName()).isEqualTo("DataObject _21");
    assertThat(dataObject.isCollection()).isFalse();
    assertThat(dataObject.getItemSubject()).isEqualTo(itemDefinition);
  }

  @Test
  public void testGetDataObjectReference() {
    final DataObjectReference dataObjectReference = modelInstance.getModelElementById("_dataRef_7");
    final DataObject dataObject = modelInstance.getModelElementById("_7");
    assertThat(dataObjectReference).isNotNull();
    assertThat(dataObjectReference.getName()).isNull();
    assertThat(dataObjectReference.getDataObject()).isEqualTo(dataObject);
  }

  @Test
  public void testDataObjectReferenceAsDataAssociationSource() {
    final ScriptTask scriptTask = modelInstance.getModelElementById("_3");
    final DataObjectReference dataObjectReference =
        modelInstance.getModelElementById("_dataRef_11");
    final DataInputAssociation dataInputAssociation =
        scriptTask.getDataInputAssociations().iterator().next();
    final Collection<ItemAwareElement> sources = dataInputAssociation.getSources();
    assertThat(sources.size()).isEqualTo(1);
    assertThat(sources.iterator().next()).isEqualTo(dataObjectReference);
  }

  @Test
  public void testDataObjectReferenceAsDataAssociationTarget() {
    final ScriptTask scriptTask = modelInstance.getModelElementById("_3");
    final DataObjectReference dataObjectReference = modelInstance.getModelElementById("_dataRef_7");
    final DataOutputAssociation dataOutputAssociation =
        scriptTask.getDataOutputAssociations().iterator().next();
    assertThat(dataOutputAssociation.getTarget()).isEqualTo(dataObjectReference);
  }
}
