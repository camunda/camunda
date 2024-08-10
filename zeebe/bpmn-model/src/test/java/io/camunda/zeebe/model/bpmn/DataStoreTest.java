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

import io.camunda.zeebe.model.bpmn.instance.DataStore;
import io.camunda.zeebe.model.bpmn.instance.DataStoreReference;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Falko Menge
 */
public class DataStoreTest {

  private static BpmnModelInstance modelInstance;

  @BeforeClass
  public static void parseModel() {
    modelInstance =
        Bpmn.readModelFromStream(DataStoreTest.class.getResourceAsStream("DataStoreTest.bpmn"));
  }

  @Test
  public void testGetDataStore() {
    final DataStore dataStore = modelInstance.getModelElementById("myDataStore");
    assertThat(dataStore).isNotNull();
    assertThat(dataStore.getName()).isEqualTo("My Data Store");
    assertThat(dataStore.getCapacity()).isEqualTo(23);
    assertThat(dataStore.isUnlimited()).isFalse();
  }

  @Test
  public void testGetDataStoreReference() {
    final DataStoreReference dataStoreReference =
        modelInstance.getModelElementById("myDataStoreReference");
    final DataStore dataStore = modelInstance.getModelElementById("myDataStore");
    assertThat(dataStoreReference).isNotNull();
    assertThat(dataStoreReference.getName()).isEqualTo("My Data Store Reference");
    assertThat(dataStoreReference.getDataStore()).isEqualTo(dataStore);
  }
}
