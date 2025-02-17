/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.Task;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdHocSubprocessActivityServices extends ApiServices<AdHocSubprocessActivityServices> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AdHocSubprocessActivityServices.class);

  private final ProcessDefinitionServices processDefinitionServices;

  public AdHocSubprocessActivityServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessDefinitionServices processDefinitionServices,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.processDefinitionServices = processDefinitionServices;
  }

  @Override
  public AdHocSubprocessActivityServices withAuthentication(final Authentication authentication) {
    return new AdHocSubprocessActivityServices(
        brokerClient,
        securityContextProvider,
        processDefinitionServices.withAuthentication(authentication),
        authentication);
  }

  public List<AdHocSubprocessActivity> findActivatableActivities(
      final Long processDefinitionKey, final String adHocSubprocessId) {
    final ProcessDefinitionEntity processDefinitionEntity =
        processDefinitionServices.getByKey(processDefinitionKey);

    // TODO should we cache this lookup somewhere? There are multiple ProcessCache implementations
    // in operate, tasklist, gateway-rest, but none in this service layer. Implement caching later
    // or introduce a process definition cache at this level?
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(
                processDefinitionEntity.bpmnXml().getBytes(StandardCharsets.UTF_8)));

    final var processElement = modelInstance.getModelElementById(adHocSubprocessId);
    if (processElement instanceof final AdHocSubProcess adHocSubProcess) {
      final var tasks = adHocSubProcess.getChildElementsByType(Task.class);
      return tasks.stream()
          .filter(task -> task.getIncoming().isEmpty())
          .map(
              task ->
                  new AdHocSubprocessActivity(
                      processDefinitionKey,
                      adHocSubprocessId,
                      task.getId(),
                      task.getName(),
                      BpmnElementType.bpmnElementTypeFor(task.getElementType().getTypeName()),
                      extractDocumentation(task)))
          .toList();
    }

    if (LOGGER.isWarnEnabled()) {
      if (processElement == null) {
        LOGGER.warn(
            "Failed to find ad-hoc subprocess element with id '{}' in process definition with key '{}'",
            adHocSubprocessId,
            processDefinitionKey);
      } else {
        LOGGER.warn(
            "Found element with id '{}' in process definition with key '{}' but it was of type '{}', expected 'AdHocSubProcess'",
            adHocSubprocessId,
            processDefinitionKey,
            processElement.getElementType());
      }
    }

    throw new NotFoundException(
        "Failed to find Ad-Hoc Subprocess with ID '%s'".formatted(adHocSubprocessId));
  }

  private String extractDocumentation(final Task task) {
    return task.getDocumentations().stream()
        .map(ModelElementInstance::getTextContent)
        .collect(Collectors.joining("\n"));
  }

  public record AdHocSubprocessActivity(
      Long processDefinitionKey,
      String adHocSubprocessId,
      String flowNodeId,
      String flowNodeName,
      BpmnElementType type,
      String documentation) {}
}
