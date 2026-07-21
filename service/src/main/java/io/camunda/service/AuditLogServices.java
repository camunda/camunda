/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.AUDIT_LOG_READ_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.AUDIT_LOG_READ_PROCESS_INSTANCE_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.AUDIT_LOG_READ_USER_TASK_AUTHORIZATION;

import io.camunda.search.clients.AuditLogSearchClient;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.condition.AuthorizationCondition;
import io.camunda.security.core.auth.condition.AuthorizationConditions;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogServices
    extends SearchQueryService<AuditLogServices, AuditLogQuery, AuditLogEntity> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogServices.class);

  private static final AuthorizationCondition AUDIT_LOG_AUTHORIZATIONS =
      AuthorizationConditions.anyOf(
          AUDIT_LOG_READ_AUTHORIZATION.withResourceIdSupplier(al -> al.category().name()),
          AUDIT_LOG_READ_PROCESS_INSTANCE_AUTHORIZATION
              .withResourceIdSupplier(AuditLogEntity::processDefinitionId)
              .withCondition(al -> al.processDefinitionId() != null),
          AUDIT_LOG_READ_USER_TASK_AUTHORIZATION
              .withResourceIdSupplier(AuditLogEntity::processDefinitionId)
              .withCondition(
                  al ->
                      al.processDefinitionId() != null
                          && al.category() == AuditLogOperationCategory.USER_TASKS));

  private final AuditLogSearchClient auditLogSearchClient;

  public AuditLogServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuditLogSearchClient auditLogSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.auditLogSearchClient = auditLogSearchClient;
  }

  @Override
  public SearchQueryResult<AuditLogEntity> search(
      final AuditLogQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            auditLogSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AUDIT_LOG_AUTHORIZATIONS))
                .searchAuditLogs(query));
  }

  public AuditLogEntity getAuditLog(
      final String auditLogKey, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            auditLogSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AUDIT_LOG_AUTHORIZATIONS))
                .getAuditLog(auditLogKey));
  }

  public Map<String, AuditLogEntity> latestSuccessfulByEntityKeys(
      final AuditLogEntityType entityType,
      final Collection<String> entityKeys,
      final CamundaAuthentication authentication) {
    final var distinctEntityKeys = entityKeys.stream().distinct().toList();
    if (distinctEntityKeys.isEmpty()) {
      return Map.of();
    }

    final List<AuditLogEntity> result;
    try {
      result =
          executeSearchRequest(
              () ->
                  auditLogSearchClient
                      .withSecurityContext(
                          securityContextProvider.provideSecurityContext(
                              authentication, AUDIT_LOG_AUTHORIZATIONS))
                      .searchLatestSuccessfulByEntityKeys(entityType, distinctEntityKeys));
    } catch (final ServiceException e) {
      if (e.getStatus() == Status.FORBIDDEN) {
        return Map.of();
      }
      LOGGER.warn("Unable to retrieve supplemental update metadata; returning empty metadata");
      return Map.of();
    }

    final var latestByEntityKey = new LinkedHashMap<String, AuditLogEntity>();
    result.forEach(auditLog -> latestByEntityKey.put(auditLog.entityKey(), auditLog));
    return Map.copyOf(latestByEntityKey);
  }
}
