/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformation;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCase;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCase.ExecutableStage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCase;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeStage;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the {@link ExecutableCase} of processes marked with {@code zeebe:case} from the BPMN
 * model, as a pass after the regular transformation. POC shortcut: deliberately not a versioned
 * transformer slot.
 */
public final class CaseDefinitionTransformer {

  private CaseDefinitionTransformer() {}

  public static void transform(
      final BpmnModelInstance model, final List<ExecutableProcess> processes) {
    for (final Process process : model.getModelElementsByType(Process.class)) {
      final ZeebeCase zeebeCase = process.getSingleExtensionElement(ZeebeCase.class);
      if (zeebeCase == null) {
        continue;
      }
      processes.stream()
          .filter(p -> BufferUtil.bufferAsString(p.getId()).equals(process.getId()))
          .findFirst()
          .ifPresent(p -> p.setCaseDefinition(transform(process, zeebeCase)));
    }
  }

  private static ExecutableCase transform(final Process process, final ZeebeCase zeebeCase) {
    final Map<String, SubProcess> stageElements = new LinkedHashMap<>();
    for (final SubProcess subProcess : process.getChildElementsByType(SubProcess.class)) {
      if (subProcess.getSingleExtensionElement(ZeebeStage.class) != null) {
        stageElements.put(subProcess.getId(), subProcess);
      }
    }

    final Map<String, List<BoundaryEvent>> boundaryEvents = new HashMap<>();
    for (final BoundaryEvent boundaryEvent : process.getChildElementsByType(BoundaryEvent.class)) {
      boundaryEvents
          .computeIfAbsent(boundaryEvent.getAttachedTo().getId(), id -> new ArrayList<>())
          .add(boundaryEvent);
    }

    final Set<String> startElementIds = new LinkedHashSet<>();
    for (final StartEvent startEvent : process.getChildElementsByType(StartEvent.class)) {
      startElementIds.addAll(reach(startEvent, stageElements.keySet(), boundaryEvents));
    }

    final Map<String, ExecutableStage> stages = new LinkedHashMap<>();
    for (final SubProcess subProcess : stageElements.values()) {
      final boolean adHoc = subProcess.triggeredByEvent();
      final List<String> next =
          adHoc
              ? List.of()
              : new ArrayList<>(reach(subProcess, stageElements.keySet(), boundaryEvents));
      final String name = subProcess.getName() == null ? "" : subProcess.getName();
      final boolean alternative =
          subProcess.getSingleExtensionElement(ZeebeStage.class).isAlternative();
      stages.put(
          subProcess.getId(),
          new ExecutableStage(subProcess.getId(), name, alternative, adHoc, next));
    }

    final List<ExecutableStage> ordered = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    final ArrayDeque<String> queue = new ArrayDeque<>(startElementIds);
    while (!queue.isEmpty()) {
      final String id = queue.poll();
      final ExecutableStage stage = stages.get(id);
      if (stage != null && seen.add(id)) {
        ordered.add(stage);
        queue.addAll(stage.nextElementIds());
      }
    }
    stages.values().stream().filter(s -> !seen.contains(s.elementId())).forEach(ordered::add);

    final String title = zeebeCase.getTitle();
    return new ExecutableCase(
        title == null || title.isBlank() ? null : title, List.copyOf(startElementIds), ordered);
  }

  private static Set<String> reach(
      final FlowNode from,
      final Set<String> stageIds,
      final Map<String, List<BoundaryEvent>> boundaryEvents) {
    final Set<String> result = new LinkedHashSet<>();
    final Set<String> visited = new HashSet<>();
    final ArrayDeque<FlowNode> queue = new ArrayDeque<>();
    queue.add(from);
    while (!queue.isEmpty()) {
      final FlowNode node = queue.poll();
      for (final SequenceFlow flow : outgoingOf(node, boundaryEvents)) {
        final FlowNode target = flow.getTarget();
        if (stageIds.contains(target.getId())) {
          result.add(target.getId());
        } else if (visited.add(target.getId())) {
          queue.add(target);
        }
      }
    }
    return result;
  }

  private static List<SequenceFlow> outgoingOf(
      final FlowNode node, final Map<String, List<BoundaryEvent>> boundaryEvents) {
    final List<SequenceFlow> flows = new ArrayList<>(node.getOutgoing());
    for (final BoundaryEvent boundaryEvent : boundaryEvents.getOrDefault(node.getId(), List.of())) {
      flows.addAll(boundaryEvent.getOutgoing());
    }
    return flows;
  }
}
