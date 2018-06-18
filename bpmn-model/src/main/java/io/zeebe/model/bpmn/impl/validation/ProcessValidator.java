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
package io.zeebe.model.bpmn.impl.validation;

import io.zeebe.model.bpmn.impl.ZeebeConstraints;
import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.impl.validation.nodes.EndEventValidator;
import io.zeebe.model.bpmn.impl.validation.nodes.ExclusiveGatewayValidator;
import io.zeebe.model.bpmn.impl.validation.nodes.task.ServiceTaskValidator;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class ProcessValidator {
  private final ServiceTaskValidator taskValidator = new ServiceTaskValidator();
  private final EndEventValidator endEventValidator = new EndEventValidator();
  private final ExclusiveGatewayValidator exclusiveGatewayValidator =
      new ExclusiveGatewayValidator();

  public void validate(ErrorCollector validationResult, ProcessImpl process) {
    final DirectBuffer bpmnProcessId = process.getBpmnProcessId();
    if (bpmnProcessId == null || bpmnProcessId.capacity() == 0) {
      validationResult.addError(process, "BPMN process id is required.");
    } else if (bpmnProcessId.capacity() > ZeebeConstraints.ID_MAX_LENGTH) {
      validationResult.addError(
          process,
          String.format(
              "BPMN process id must not be longer than %d.", ZeebeConstraints.ID_MAX_LENGTH));
    }

    if (process.getStartEvents().isEmpty()) {
      validationResult.addError(process, "The process must contain at least one none start event.");
    }

    validateFlowNodes(validationResult, process);
  }

  private void validateFlowNodes(ErrorCollector validationResult, ProcessImpl process) {
    final List<FlowNodeImpl> flowNodes = new ArrayList<>();
    flowNodes.addAll(process.getStartEvents());
    flowNodes.addAll(process.getEndEvents());
    flowNodes.addAll(process.getExclusiveGateways());
    flowNodes.addAll(process.getServiceTasks());

    validateStartEvent(validationResult, process, flowNodes);
    validateSequenceFlows(validationResult, process, flowNodes);
    validateExclusiveGateways(validationResult, process, flowNodes);
    validateServiceTasks(validationResult, process, flowNodes);
    validateEndEvents(validationResult, process, flowNodes);
  }

  private void validateStartEvent(
      ErrorCollector validationResult, ProcessImpl process, List<FlowNodeImpl> existingNodes) {
    final List<StartEventImpl> startEvents = process.getStartEvents();
    for (StartEventImpl startEvent : startEvents) {
      validateGeneralFlowElement(validationResult, startEvent);
      validateGeneralFlowNode(validationResult, existingNodes, startEvent);
    }
  }

  private void validateSequenceFlows(
      ErrorCollector validationResult, ProcessImpl process, List<FlowNodeImpl> existingNodes) {
    final List<SequenceFlowImpl> sequenceFlows = process.getSequenceFlows();
    final List<Tuple<String, SequenceFlowImpl>> sourceNodes = new ArrayList<>();
    final List<Tuple<String, SequenceFlowImpl>> targetNodes = new ArrayList<>();

    for (SequenceFlowImpl sequenceFlow : sequenceFlows) {
      validateGeneralFlowElement(validationResult, sequenceFlow);
      sourceNodes.add(new Tuple<>(sequenceFlow.getSourceRef(), sequenceFlow));
      targetNodes.add(new Tuple<>(sequenceFlow.getTargetRef(), sequenceFlow));
    }

    validateNodeExistence(validationResult, existingNodes, sourceNodes, "source");
    validateNodeExistence(validationResult, existingNodes, targetNodes, "target");
  }

  private void validateGeneralFlowElement(
      ErrorCollector validationResult, FlowElementImpl flowElement) {
    final DirectBuffer id = flowElement.getIdAsBuffer();
    if (id == null || id.capacity() == 0) {
      validationResult.addError(flowElement, "Activity id is required.");
    } else if (id.capacity() > ZeebeConstraints.ID_MAX_LENGTH) {
      validationResult.addError(
          flowElement,
          String.format("Activity id must not be longer than %d.", ZeebeConstraints.ID_MAX_LENGTH));
    }
  }

  private void validateGeneralFlowNode(
      ErrorCollector validationResult, List<FlowNodeImpl> existingNodes, FlowNodeImpl flowNode) {
    if (!(flowNode instanceof ExclusiveGateway)) {
      if (flowNode.getOutgoingSequenceFlows().size() > 1) {
        validationResult.addError(
            flowNode, "The flow element must not have more than one outgoing sequence flow.");
      }
    }

    final List<Tuple<String, SequenceFlowImpl>> sourceNodes =
        flowNode
            .getIncoming()
            .stream()
            .map(flow -> new Tuple<>(((SequenceFlowImpl) flow).getSourceRef(), flow))
            .collect(Collectors.toList());

    validateNodeExistence(validationResult, existingNodes, sourceNodes, "source");

    final List<Tuple<String, SequenceFlowImpl>> targetNodes =
        flowNode
            .getOutgoing()
            .stream()
            .map(flow -> new Tuple<>(((SequenceFlowImpl) flow).getTargetRef(), flow))
            .collect(Collectors.toList());
    validateNodeExistence(validationResult, existingNodes, targetNodes, "target");
  }

  private void validateNodeExistence(
      ErrorCollector validationResult,
      List<FlowNodeImpl> existingNodes,
      List<Tuple<String, SequenceFlowImpl>> nodes,
      String nodeType) {
    for (Tuple<String, SequenceFlowImpl> tuple : nodes) {
      boolean exist = false;
      for (FlowNodeImpl existingNode : existingNodes) {
        if (existingNode.getId().equalsIgnoreCase(tuple.getLeft())) {
          exist = true;
          break;
        }
      }
      if (!exist) {
        validationResult.addError(
            tuple.getRight(),
            String.format("Cannot find %s as %s of sequence flow.", tuple.getLeft(), nodeType));
      }
    }
  }

  private void validateExclusiveGateways(
      ErrorCollector validationResult, ProcessImpl process, List<FlowNodeImpl> existingNodes) {
    final List<ExclusiveGatewayImpl> exclusiveGateways = process.getExclusiveGateways();
    for (ExclusiveGatewayImpl exclusiveGateway : exclusiveGateways) {
      validateGeneralFlowElement(validationResult, exclusiveGateway);
      validateGeneralFlowNode(validationResult, existingNodes, exclusiveGateway);
      exclusiveGatewayValidator.validate(validationResult, exclusiveGateway);
    }
  }

  private void validateEndEvents(
      ErrorCollector validationResult, ProcessImpl process, List<FlowNodeImpl> existingNodes) {
    final List<EndEventImpl> endEvents = process.getEndEvents();
    for (EndEventImpl endEvent : endEvents) {
      validateGeneralFlowElement(validationResult, endEvent);
      validateGeneralFlowNode(validationResult, existingNodes, endEvent);
      endEventValidator.validate(validationResult, endEvent);
    }
  }

  private void validateServiceTasks(
      ErrorCollector validationResult, ProcessImpl process, List<FlowNodeImpl> existingNodes) {
    final List<ServiceTaskImpl> serviceTasks = process.getServiceTasks();
    for (ServiceTaskImpl task : serviceTasks) {
      validateGeneralFlowElement(validationResult, task);
      validateGeneralFlowNode(validationResult, existingNodes, task);
      taskValidator.validate(validationResult, task);
    }
  }
}
