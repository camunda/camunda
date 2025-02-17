/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivityResult;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.Task;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/ad-hoc-activities")
public class AdHocSubprocessActivityController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AdHocSubprocessActivityController.class);

  private final ProcessDefinitionServices processDefinitionServices;

  public AdHocSubprocessActivityController(
      final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  @CamundaPostMapping(path = "/activatable")
  public ResponseEntity<AdHocSubprocessActivitySearchQueryResult> getActivities(
      @RequestBody final AdHocSubprocessActivitySearchQuery request) {
    try {
      final var activities = findActivatableActivities(request);

      final var result = new AdHocSubprocessActivitySearchQueryResult();
      result.setItems(activities);

      return ResponseEntity.ok(result);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private List<AdHocSubprocessActivityResult> findActivatableActivities(
      final AdHocSubprocessActivitySearchQuery request) {
    final ProcessDefinitionEntity processDefinitionEntity =
        processDefinitionServices
            .withAuthentication(RequestMapper.getAuthentication())
            .getByKey(Long.valueOf(request.getProcessDefinitionKey()));

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(
                processDefinitionEntity.bpmnXml().getBytes(StandardCharsets.UTF_8)));

    final var processElement = modelInstance.getModelElementById(request.getAdHocSubprocessId());
    if (processElement instanceof final AdHocSubProcess adHocSubProcess) {
      final var tasks = adHocSubProcess.getChildElementsByType(Task.class);
      return tasks.stream()
          .filter(task -> task.getIncoming().isEmpty())
          .map(task -> toAdHocActivity(request, task))
          .toList();
    }

    if (LOGGER.isWarnEnabled()) {
      if (processElement == null) {
        LOGGER.warn(
            "Failed to find ad-hoc subprocess element with id '{}' in process definition with key '{}'",
            request.getAdHocSubprocessId(),
            request.getProcessDefinitionKey());
      } else {
        LOGGER.warn(
            "Found element with id '{}' in process definition with key '{}' but it was of type '{}', expected 'AdHocSubProcess'",
            request.getAdHocSubprocessId(),
            request.getProcessDefinitionKey(),
            processElement.getElementType());
      }
    }

    throw new NotFoundException(
        "Failed to find Ad-Hoc Subprocess with ID %s".formatted(request.getAdHocSubprocessId()));
  }

  private AdHocSubprocessActivityResult toAdHocActivity(
      final AdHocSubprocessActivitySearchQuery query, final Task task) {
    final var result = new AdHocSubprocessActivityResult();
    result.setProcessDefinitionKey(query.getProcessDefinitionKey());
    result.setAdHocSubprocessId(query.getAdHocSubprocessId());
    result.setFlowNodeId(task.getId());
    result.setFlowNodeName(task.getName());

    // TODO how to map type to enum (scriptTask vs SCRIPT_TASK)?
    // result.setType(task.getElementType());

    // TODO documentation vs description?
    result.setDocumentation(
        task.getDocumentations().stream()
            .map(ModelElementInstance::getTextContent)
            .collect(Collectors.joining("\n")));

    return result;
  }
}
