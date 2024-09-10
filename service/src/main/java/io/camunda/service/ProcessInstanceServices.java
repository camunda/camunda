/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  public ProcessInstanceServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public ProcessInstanceServices withAuthentication(final Authentication authentication) {
    return new ProcessInstanceServices(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    return executor.search(query, ProcessInstanceEntity.class);
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return search(SearchQueryBuilders.processInstanceSearchQuery(fn));
  }

  public CompletableFuture<ProcessInstanceCreationRecord> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    final var brokerRequest =
        new BrokerCreateProcessInstanceRequest()
            .setBpmnProcessId(request.bpmnProcessId())
            .setKey(request.processDefinitionKey())
            .setVersion(request.version())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setInstructions(request.startInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceResultRecord> createProcessInstanceWithResult(
      final ProcessInstanceCreateRequest request) {
    final var brokerRequest =
        new BrokerCreateProcessInstanceWithResultRequest()
            .setBpmnProcessId(request.bpmnProcessId())
            .setKey(request.processDefinitionKey())
            .setVersion(request.version())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setInstructions(request.startInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceRecord> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    final var brokerRequest =
        new BrokerCancelProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public record ProcessInstanceCreateRequest(
      Long processDefinitionKey,
      String bpmnProcessId,
      Integer version,
      Map<String, Object> variables,
      String tenantId,
      Boolean awaitCompletion,
      Long requestTimeout,
      Long operationReference,
      List<ProcessInstanceCreationStartInstruction> startInstructions) {}

  public record ProcessInstanceCancelRequest(Long processInstanceKey, Long operationReference) {}
}
