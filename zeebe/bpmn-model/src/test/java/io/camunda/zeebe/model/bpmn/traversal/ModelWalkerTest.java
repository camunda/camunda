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
package io.camunda.zeebe.model.bpmn.traversal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.instance.CollaborationImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.bpmndi.BpmnDiagramImpl;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.Task;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnPlane;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.Test;

public class ModelWalkerTest {

  @Test
  public void shouldVisitModelTopDownDepthFirst() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start-1-1")
            .subProcess("sub-1-2")
            .embeddedSubProcess()
            .startEvent("start-2-1")
            .subProcessDone()
            .subProcess("sub-1-3")
            .embeddedSubProcess()
            .startEvent("start-3-1")
            .subProcessDone()
            .endEvent("end-1-4")
            .done();

    final List<BpmnModelElementInstance> visitedElements = new ArrayList<>();

    final ModelWalker walker = new ModelWalker(modelInstance);

    // when
    walker.walk(visitedElements::add);

    // then
    final List<BaseElement> visitedBaseElements =
        visitedElements.stream()
            .filter(e -> e instanceof BaseElement)
            .map(e -> (BaseElement) e)
            .collect(Collectors.toList());

    final List<String> subprocessVisitingOrder =
        visitedBaseElements.stream()
            .filter(e -> e instanceof SubProcess)
            .map(e -> e.getId())
            .collect(Collectors.toList());
    assertThat(subprocessVisitingOrder).hasSize(2);

    final String firstSubprocess = subprocessVisitingOrder.get(0);
    final String secondSubprocess = subprocessVisitingOrder.get(1);

    final String firstSubprocessStart =
        "sub-1-2".equals(firstSubprocess) ? "start-2-1" : "start-3-1";
    final String secondSubprocessStart =
        "sub-1-2".equals(secondSubprocess) ? "start-2-1" : "start-3-1";

    assertThat(visitedBaseElements)
        .extracting(e -> e.getId())
        .containsSubsequence("process", "start-1-1")
        .containsSubsequence("process", "sub-1-2")
        .containsSubsequence("process", "sub-1-3")
        .containsSubsequence("process", "end-1-4")
        .containsSubsequence(firstSubprocess, secondSubprocess)
        .containsSubsequence(firstSubprocess, firstSubprocessStart)
        .containsSubsequence(secondSubprocess, secondSubprocessStart)
        .containsSubsequence(firstSubprocessStart, secondSubprocessStart);
  }

  @Test
  public void shouldInvokeTypedVisitors() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start-1")
            .userTask("user-1")
            .endEvent("end-1")
            .done();

    final List<FlowNode> flowNodes = new ArrayList<>();
    final List<UserTask> userTasks = new ArrayList<>();

    final TypeHierarchyVisitor compositeVisitor =
        new TypeHierarchyVisitor() {

          @Override
          protected void visit(
              final ModelElementType implementedType, final BpmnModelElementInstance instance) {
            if (implementedType.getInstanceType() == UserTask.class) {
              userTasks.add((UserTask) instance);
            }
            if (implementedType.getInstanceType() == FlowNode.class) {
              flowNodes.add((FlowNode) instance);
            }
          }
        };

    final ModelWalker walker = new ModelWalker(modelInstance);

    // when
    walker.walk(compositeVisitor);

    // then
    assertThat(flowNodes).extracting(f -> f.getId()).containsOnly("start-1", "user-1", "end-1");
    assertThat(userTasks).extracting(f -> f.getId()).containsOnly("user-1");
  }

  @Test
  public void shouldVisitTypeHiearchyInOrder() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start-1")
            .userTask("user-1")
            .endEvent("end-1")
            .done();

    final ModelWalker walker = new ModelWalker(modelInstance);
    final List<Class<?>> visitedUserTaskTypes = new ArrayList<>();

    final TypeHierarchyVisitor visitor =
        new TypeHierarchyVisitor() {

          @Override
          protected void visit(
              final ModelElementType implementedType, final BpmnModelElementInstance instance) {
            if (instance instanceof UserTask) {
              visitedUserTaskTypes.add(implementedType.getInstanceType());
            }
          }
        };

    // when
    walker.walk(visitor);

    // then
    assertThat(visitedUserTaskTypes)
        .containsExactly(
            BaseElement.class,
            FlowElement.class,
            FlowNode.class,
            Activity.class,
            Task.class,
            UserTask.class);
  }

  @Test
  public void shouldIgnoreUnknownElementsAndAttributes() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(ModelWalkerTest.class.getResourceAsStream("ModelWalkerTest.bpmn"));

    final List<BpmnModelElementInstance> visitedElements = new ArrayList<>();

    final ModelWalker walker = new ModelWalker(modelInstance);

    // when
    walker.walk(visitedElements::add);

    // then

    assertThat(visitedElements)
        .anyMatch(Definitions.class::isInstance)
        .anyMatch(StartEvent.class::isInstance)
        .anyMatch(ExclusiveGateway.class::isInstance)
        .anyMatch(ServiceTask.class::isInstance)
        .anyMatch(IntermediateCatchEvent.class::isInstance)
        .anyMatch(TimerEventDefinition.class::isInstance)
        .anyMatch(EndEvent.class::isInstance)
        .anyMatch(BpmnDiagram.class::isInstance)
        .anyMatch(BpmnPlane.class::isInstance);
  }

  @Test
  public void shouldIgnoreNonExecutableProceses() {
    // given
    // a BPMN model containing one executable and one non-executable process
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            ModelWalkerTest.class.getResourceAsStream("CollaborationModelWalkerTest.bpmn"));

    final List<BpmnModelElementInstance> visitedElements = new ArrayList<>();

    final ModelWalker walker = new ModelWalker(modelInstance);

    // when
    walker.walk(visitedElements::add);

    // then
    // assert that all the direct executable child elements of DefinitionImpl have been
    // walked through, including only the executable process
    assertThat(visitedElements)
        .extracting("class")
        .containsOnlyOnce(ProcessImpl.class)
        .containsOnlyOnce(BpmnDiagramImpl.class)
        .containsOnlyOnce(CollaborationImpl.class);
  }
}
