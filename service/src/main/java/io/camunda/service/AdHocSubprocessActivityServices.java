/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.entities.AdHocSubprocessActivityEntity;
import io.camunda.search.entities.AdHocSubprocessActivityEntity.ActivityType;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.AdHocSubprocessActivityQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class AdHocSubprocessActivityServices extends ApiServices<AdHocSubprocessActivityServices> {

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

  public SearchQueryResult<AdHocSubprocessActivityEntity> search(
      final AdHocSubprocessActivityQuery query) {
    final ProcessDefinitionEntity processDefinitionEntity =
        processDefinitionServices.getByKey(query.filter().processDefinitionKey());

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(
                processDefinitionEntity.bpmnXml().getBytes(StandardCharsets.UTF_8)));

    final var processElement =
        modelInstance.getModelElementById(query.filter().adHocSubprocessId());
    if (processElement instanceof final AdHocSubProcess adHocSubProcess) {
      final var flowNodes = adHocSubProcess.getChildElementsByType(FlowNode.class);
      final var rootActivities =
          flowNodes.stream()
              .filter(flowNode -> flowNode.getIncoming().isEmpty())
              .map(
                  flowNode ->
                      toAdHocSubprocessActivityEntity(
                          processDefinitionEntity, adHocSubProcess, flowNode))
              .toList();

      return new SearchQueryResult.Builder<AdHocSubprocessActivityEntity>()
          .items(rootActivities)
          .build();
    }

    throw new NotFoundException(
        "Failed to find Ad-Hoc Subprocess with ID '%s'"
            .formatted(query.filter().adHocSubprocessId()));
  }

  private AdHocSubprocessActivityEntity toAdHocSubprocessActivityEntity(
      final ProcessDefinitionEntity processDefinitionEntity,
      final AdHocSubProcess adHocSubProcess,
      final FlowNode flowNode) {
    return new AdHocSubprocessActivityEntity(
        processDefinitionEntity.processDefinitionKey(),
        processDefinitionEntity.processDefinitionId(),
        adHocSubProcess.getId(),
        flowNode.getId(),
        flowNode.getName(),
        ActivityType.fromZeebeBpmnElementTypeName(flowNode.getElementType().getTypeName()),
        documentationAsString(flowNode),
        processDefinitionEntity.tenantId());
  }

  private String documentationAsString(final FlowNode flowNode) {
    final var documentation =
        flowNode.getDocumentations().stream()
            .map(ModelElementInstance::getTextContent)
            .collect(Collectors.joining("\n"));

    if (documentation.isBlank()) {
      return null;
    }

    return documentation;
  }
}
