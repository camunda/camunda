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
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
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
    if (!(processElement instanceof final AdHocSubProcess adHocSubProcess)) {
      throw new CamundaSearchException(
          ErrorMessages.ERROR_NOT_FOUND_AD_HOC_SUBPROCESS.formatted(
              query.filter().adHocSubprocessId()),
          CamundaSearchException.Reason.NOT_FOUND);
    }

    final var activities =
        adHocSubProcess.getChildElementsByType(FlowNode.class).stream()
            .filter(element -> element.getIncoming().isEmpty())
            .map(
                element ->
                    toAdHocSubprocessActivityEntity(
                        processDefinitionEntity, adHocSubProcess, element))
            .toList();

    return new SearchQueryResult.Builder<AdHocSubprocessActivityEntity>().items(activities).build();
  }

  private AdHocSubprocessActivityEntity toAdHocSubprocessActivityEntity(
      final ProcessDefinitionEntity processDefinitionEntity,
      final AdHocSubProcess adHocSubProcess,
      final FlowNode element) {
    return new AdHocSubprocessActivityEntity(
        processDefinitionEntity.processDefinitionKey(),
        processDefinitionEntity.processDefinitionId(),
        adHocSubProcess.getId(),
        element.getId(),
        element.getName(),
        ActivityType.fromZeebeBpmnElementTypeName(element.getElementType().getTypeName()),
        documentationAsString(element),
        processDefinitionEntity.tenantId());
  }

  private String documentationAsString(final FlowNode element) {
    final var documentation =
        element.getDocumentations().stream()
            .map(ModelElementInstance::getTextContent)
            .collect(Collectors.joining("\n"));

    if (documentation.isBlank()) {
      return null;
    }

    return documentation;
  }
}
