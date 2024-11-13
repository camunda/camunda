/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.util;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.Query;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessModelUtil {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessModelUtil.class);
  private static final String PUBLIC_ACCESS = "publicAccess";

  public static Optional<StartEvent> processStartEvent(
      final byte[] resource, final String bpmnProcessId) {
    try {
      LOG.debug("Parsing BPMN XML Data for process: {}", bpmnProcessId);
      final var is = new ByteArrayInputStream(resource);
      final BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(is);
      final BpmnModelElementInstance processModelInstance =
          bpmnModelInstance.getModelElementById(bpmnProcessId);

      if (processModelInstance instanceof final Process process) {
        final Optional<FlowElement> startEvent =
            process.getFlowElements().stream().filter(StartEvent.class::isInstance).findFirst();
        if (startEvent.isPresent()) {
          return Optional.of((StartEvent) startEvent.get());
        }
      }
    } catch (final Exception e) {
      LOG.error("Error extracting form data from BPMN", e);
    }
    return Optional.empty();
  }

  public static Optional<String> extractFormKey(final StartEvent startEvent) {
    return getModelElementInstanceQuery(startEvent)
        .map(e -> e.filterByType(ZeebeFormDefinition.class))
        .map(Query::findSingleResult)
        .flatMap(e -> e.map(ZeebeFormDefinition::getFormKey));
  }

  public static Optional<String> extractFormId(final StartEvent startEvent) {
    return getModelElementInstanceQuery(startEvent)
        .map(e -> e.filterByType(ZeebeFormDefinition.class))
        .map(Query::findSingleResult)
        .flatMap(e -> e.map(ZeebeFormDefinition::getFormId));
  }

  public static Optional<Boolean> extractIsPublic(final StartEvent startEvent) {
    return getModelElementInstanceQuery(startEvent)
        .map(e -> e.filterByType(ZeebeProperties.class))
        .map(Query::list)
        .stream()
        .flatMap(
            f ->
                f.stream()
                    .flatMap(
                        fm ->
                            fm.getProperties().stream()
                                .filter(zp -> zp.getName().equals(PUBLIC_ACCESS))))
        .findFirst()
        .map(zp -> zp.getValue().equals(Boolean.TRUE.toString()));
  }

  private static Optional<Query<ModelElementInstance>> getModelElementInstanceQuery(
      final StartEvent startEvent) {
    return Optional.ofNullable(startEvent.getExtensionElements())
        .map(ExtensionElements::getElementsQuery);
  }
}
