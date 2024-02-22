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

import io.camunda.zeebe.model.bpmn.instance.HumanPerformer;
import io.camunda.zeebe.model.bpmn.instance.Performer;
import io.camunda.zeebe.model.bpmn.instance.PotentialOwner;
import io.camunda.zeebe.model.bpmn.instance.ResourceRole;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Dario Campagna
 */
public class ResourceRolesTest {

  private static BpmnModelInstance modelInstance;

  @BeforeClass
  public static void parseModel() {
    modelInstance =
        Bpmn.readModelFromStream(
            ResourceRolesTest.class.getResourceAsStream("ResourceRolesTest.bpmn"));
  }

  @Test
  public void testGetPerformer() {
    final UserTask userTask = modelInstance.getModelElementById("_3");
    final Collection<ResourceRole> resourceRoles = userTask.getResourceRoles();
    assertThat(resourceRoles.size()).isEqualTo(1);
    final ResourceRole resourceRole = resourceRoles.iterator().next();
    assertThat(resourceRole instanceof Performer).isTrue();
    assertThat(resourceRole.getName()).isEqualTo("Task performer");
  }

  @Test
  public void testGetHumanPerformer() {
    final UserTask userTask = modelInstance.getModelElementById("_7");
    final Collection<ResourceRole> resourceRoles = userTask.getResourceRoles();
    assertThat(resourceRoles.size()).isEqualTo(1);
    final ResourceRole resourceRole = resourceRoles.iterator().next();
    assertThat(resourceRole instanceof HumanPerformer).isTrue();
    assertThat(resourceRole.getName()).isEqualTo("Task human performer");
  }

  @Test
  public void testGetPotentialOwner() {
    final UserTask userTask = modelInstance.getModelElementById("_9");
    final Collection<ResourceRole> resourceRoles = userTask.getResourceRoles();
    assertThat(resourceRoles.size()).isEqualTo(1);
    final ResourceRole resourceRole = resourceRoles.iterator().next();
    assertThat(resourceRole instanceof PotentialOwner).isTrue();
    assertThat(resourceRole.getName()).isEqualTo("Task potential owner");
  }
}
