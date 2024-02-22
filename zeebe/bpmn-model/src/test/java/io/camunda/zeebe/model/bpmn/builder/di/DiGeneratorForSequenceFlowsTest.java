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
package io.camunda.zeebe.model.bpmn.builder.di;

import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.END_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SEQUENCE_FLOW_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.USER_TASK_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import org.junit.After;
import org.junit.Test;

public class DiGeneratorForSequenceFlowsTest {

  private BpmnModelInstance instance;

  @After
  public void validateModel() throws IOException {
    if (instance != null) {
      Bpmn.validateModel(instance);
    }
  }

  @Test
  public void shouldGenerateEdgeForSequenceFlow() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .done();

    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    assertEquals(1, allEdges.size());

    assertBpmnEdgeExists(SEQUENCE_FLOW_ID);
  }

  @Test
  public void shouldGenerateEdgesForSequenceFlowsUsingGateway() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId("s1")
            .parallelGateway("gateway")
            .sequenceFlowId("s2")
            .endEvent("e1")
            .moveToLastGateway()
            .sequenceFlowId("s3")
            .endEvent("e2")
            .done();

    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    assertEquals(3, allEdges.size());

    assertBpmnEdgeExists("s1");
    assertBpmnEdgeExists("s2");
    assertBpmnEdgeExists("s3");
  }

  @Test
  public void shouldGenerateEdgesWhenUsingMoveToActivity() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId("s1")
            .exclusiveGateway()
            .sequenceFlowId("s2")
            .userTask(USER_TASK_ID)
            .sequenceFlowId("s3")
            .endEvent("e1")
            .moveToActivity(USER_TASK_ID)
            .sequenceFlowId("s4")
            .endEvent("e2")
            .done();

    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    assertEquals(4, allEdges.size());

    assertBpmnEdgeExists("s1");
    assertBpmnEdgeExists("s2");
    assertBpmnEdgeExists("s3");
    assertBpmnEdgeExists("s4");
  }

  @Test
  public void shouldGenerateEdgesWhenUsingMoveToNode() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId("s1")
            .exclusiveGateway()
            .sequenceFlowId("s2")
            .userTask(USER_TASK_ID)
            .sequenceFlowId("s3")
            .endEvent("e1")
            .moveToNode(USER_TASK_ID)
            .sequenceFlowId("s4")
            .endEvent("e2")
            .done();

    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    assertEquals(4, allEdges.size());

    assertBpmnEdgeExists("s1");
    assertBpmnEdgeExists("s2");
    assertBpmnEdgeExists("s3");
    assertBpmnEdgeExists("s4");
  }

  @Test
  public void shouldGenerateEdgesWhenUsingConnectTo() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId("s1")
            .exclusiveGateway("gateway")
            .sequenceFlowId("s2")
            .userTask(USER_TASK_ID)
            .sequenceFlowId("s3")
            .endEvent(END_EVENT_ID)
            .moveToNode(USER_TASK_ID)
            .sequenceFlowId("s4")
            .connectTo("gateway")
            .done();

    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    assertEquals(4, allEdges.size());

    assertBpmnEdgeExists("s1");
    assertBpmnEdgeExists("s2");
    assertBpmnEdgeExists("s3");
    assertBpmnEdgeExists("s4");
  }

  protected BpmnEdge findBpmnEdge(final String sequenceFlowId) {
    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    final Iterator<BpmnEdge> iterator = allEdges.iterator();

    while (iterator.hasNext()) {
      final BpmnEdge edge = iterator.next();
      if (edge.getBpmnElement().getId().equals(sequenceFlowId)) {
        return edge;
      }
    }
    return null;
  }

  protected void assertBpmnEdgeExists(final String id) {
    final BpmnEdge edge = findBpmnEdge(id);
    assertNotNull(edge);
  }
}
