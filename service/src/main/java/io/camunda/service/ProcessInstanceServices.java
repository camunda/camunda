/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  private final ProcessInstanceSearchClient processInstanceSearchClient;

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityConfiguration securityConfiguration,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityConfiguration, authentication);
    this.processInstanceSearchClient = processInstanceSearchClient;
  }

  @Override
  public ProcessInstanceServices withAuthentication(final Authentication authentication) {
    return new ProcessInstanceServices(
        brokerClient, securityConfiguration, processInstanceSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    return processInstanceSearchClient.searchProcessInstances(
        query,
        SecurityContext.of(
            s ->
                s.withAuthentication(authentication)
                    .withAuthorizationIfEnabled(
                        securityConfiguration.getAuthorizations().isEnabled(),
                        a ->
                            a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                                .permissionType(PermissionType.READ_INSTANCE))));
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return search(SearchQueryBuilders.processInstanceSearchQuery(fn));
  }

  public ProcessInstanceEntity getByKey(final Long processInstanceKey) {
    final SearchQueryResult<ProcessInstanceEntity> result =
        search(
            SearchQueryBuilders.processInstanceSearchQuery()
                .filter(f -> f.processInstanceKeys(processInstanceKey))
                .build());
    if (result.total() < 1) {
      throw new NotFoundException(
          String.format("Process Instance with key %d not found", processInstanceKey));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found Process Instance with key %d more than once", processInstanceKey));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
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
            .setInstructions(request.startInstructions())
            .setFetchVariables(request.fetchVariables());

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