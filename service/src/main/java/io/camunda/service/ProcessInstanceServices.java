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
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.RequestRetryHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerModifyProcessInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  private final RequestRetryHandler requestRetryHandler;

  public ProcessInstanceServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    this(
        brokerClient,
        searchClient,
        transformers,
        authentication,
        new RequestRetryHandler(brokerClient, brokerClient.getTopologyManager()));
  }

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication,
      final RequestRetryHandler requestRetryHandler) {
    super(brokerClient, searchClient, transformers, authentication);
    this.requestRetryHandler = requestRetryHandler;
  }

  @Override
  public ProcessInstanceServices withAuthentication(final Authentication authentication) {
    return new ProcessInstanceServices(
        brokerClient, searchClient, transformers, authentication, requestRetryHandler);
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
    return sendRequestWithRetryPartitions(brokerRequest);
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
            .setInstructions(request.startInstructions())
            .setFetchVariables(request.fetchVariables());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }

    // Use custom request timeout if provided, otherwise use default
    if (request.requestTimeout() != null && request.requestTimeout() > 0) {
      return sendRequestWithRetryPartitions(
          brokerRequest, Duration.ofMillis(request.requestTimeout()));
    }
    return sendRequestWithRetryPartitions(brokerRequest);
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

  public CompletableFuture<ProcessInstanceMigrationRecord> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    final var brokerRequest =
        new BrokerMigrateProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey())
            .setTargetProcessDefinitionKey(request.targetProcessDefinitionKey())
            .setMappingInstructions(request.mappingInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceModificationRecord> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    final var brokerRequest =
        new BrokerModifyProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey())
            .addActivationInstructions(request.activateInstructions())
            .addTerminationInstructions(request.terminateInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  private <R> CompletableFuture<R> sendRequestWithRetryPartitions(
      final BrokerRequest<R> brokerRequest) {
    return sendRequestWithRetryPartitions(brokerRequest, null);
  }

  private <R> CompletableFuture<R> sendRequestWithRetryPartitions(
      final BrokerRequest<R> brokerRequest, final Duration requestTimeout) {
    brokerRequest.setAuthorization(authentication.token());
    final CompletableFuture<R> responseFuture = new CompletableFuture<>();
    if (requestTimeout != null) {
      requestRetryHandler.sendRequest(
          brokerRequest,
          (key, response) -> responseFuture.complete(response),
          responseFuture::completeExceptionally,
          requestTimeout);
    } else {
      requestRetryHandler.sendRequest(
          brokerRequest,
          (key, response) -> responseFuture.complete(response),
          responseFuture::completeExceptionally);
    }
    return responseFuture.handleAsync(
        (response, error) -> {
          if (error != null) {
            throw new CamundaServiceException(error);
          }
          return response;
        });
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
      List<ProcessInstanceCreationStartInstruction> startInstructions,
      List<String> fetchVariables) {}

  public record ProcessInstanceCancelRequest(Long processInstanceKey, Long operationReference) {}

  public record ProcessInstanceMigrateRequest(
      Long processInstanceKey,
      Long targetProcessDefinitionKey,
      List<ProcessInstanceMigrationMappingInstruction> mappingInstructions,
      Long operationReference) {}

  public record ProcessInstanceModifyRequest(
      Long processInstanceKey,
      List<ProcessInstanceModificationActivateInstruction> activateInstructions,
      List<ProcessInstanceModificationTerminateInstruction> terminateInstructions,
      Long operationReference) {}
}
