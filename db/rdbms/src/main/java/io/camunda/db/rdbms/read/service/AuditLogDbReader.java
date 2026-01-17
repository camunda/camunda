/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUDIT_LOG;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;

import io.camunda.db.rdbms.read.domain.AuditLogAuthorizationFilter;
import io.camunda.db.rdbms.read.domain.AuditLogDbQuery;
import io.camunda.db.rdbms.read.mapper.AuditLogEntityMapper;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.columns.AuditLogSearchColumn;
import io.camunda.search.clients.reader.AuditLogReader;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogDbReader extends AbstractEntityReader<AuditLogEntity>
    implements AuditLogReader {

  private static final Logger LOG = LoggerFactory.getLogger(AuditLogDbReader.class);

  private final AuditLogMapper auditLogMapper;

  public AuditLogDbReader(final AuditLogMapper auditLogMapper) {
    super(AuditLogSearchColumn.values());
    this.auditLogMapper = auditLogMapper;
  }

  @Override
  public AuditLogEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    final var result = search(AuditLogQuery.of(b -> b.filter(f -> f.auditLogKeys(id))));
    return Optional.ofNullable(result.items())
        .flatMap(items -> items.stream().findFirst())
        .orElse(null);
  }

  @Override
  public SearchQueryResult<AuditLogEntity> search(
      final AuditLogQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), AuditLogSearchColumn.TIMESTAMP);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizationFilter = buildAuthorizationFilter(resourceAccessChecks);
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        AuditLogDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizationFilter(authorizationFilter)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for audit logs with filter {}", dbQuery);
    final var totalHits = auditLogMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        auditLogMapper.search(dbQuery).stream().map(AuditLogEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  /**
   * Builds the authorization filter for audit log queries by extracting the composite authorization
   * structure from ResourceAccessChecks.
   *
   * <p>This mirrors the Elasticsearch implementation in {@code
   * AuditLogFilterTransformer.toAuthorizationCheckSearchQuery}, supporting:
   *
   * <ul>
   *   <li>AUDIT_LOG resource type: authorized categories
   *   <li>PROCESS_DEFINITION + READ_PROCESS_INSTANCE: authorized process definition IDs or wildcard
   *       access
   *   <li>PROCESS_DEFINITION + READ_USER_TASK: authorized process definition IDs for user tasks or
   *       wildcard access
   * </ul>
   */
  private AuditLogAuthorizationFilter buildAuthorizationFilter(
      final ResourceAccessChecks resourceAccessChecks) {
    final var authorizations = extractAuthorizations(resourceAccessChecks);
    if (authorizations.isEmpty()) {
      return AuditLogAuthorizationFilter.disabled();
    }

    final var builder = new AuthorizationFilterBuilder();
    authorizations.forEach(builder::processAuthorization);

    return builder.build();
  }

  private List<Authorization<?>> extractAuthorizations(
      final ResourceAccessChecks resourceAccessChecks) {
    final var authCheck = resourceAccessChecks.authorizationCheck();
    if (!authCheck.enabled() || authCheck.authorizationCondition() == null) {
      return List.of();
    }

    final var authorizations = authCheck.authorizationCondition().authorizations();
    if (authorizations == null || authorizations.isEmpty()) {
      return List.of();
    }

    return authorizations;
  }

  public SearchQueryResult<AuditLogEntity> search(final AuditLogQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  private static final class AuthorizationFilterBuilder {
    private final List<String> authorizedCategories = new ArrayList<>();
    private final List<String> processDefIdsForProcessInstance = new ArrayList<>();
    private boolean hasProcessInstanceWildcard = false;
    private final List<String> processDefIdsForUserTask = new ArrayList<>();
    private boolean hasUserTaskWildcard = false;

    void processAuthorization(final Authorization<?> auth) {
      if (auth == null || auth.resourceType() == null) {
        return;
      }

      if (AUDIT_LOG.equals(auth.resourceType())) {
        processAuditLogAuthorization(auth);
      } else if (PROCESS_DEFINITION.equals(auth.resourceType())) {
        processProcessDefinitionAuthorization(auth);
      }
    }

    private void processAuditLogAuthorization(final Authorization<?> auth) {
      if (auth.hasAnyResourceIds()) {
        authorizedCategories.addAll(auth.resourceIds());
      }
    }

    private void processProcessDefinitionAuthorization(final Authorization<?> auth) {
      if (READ_PROCESS_INSTANCE.equals(auth.permissionType())) {
        processReadProcessInstancePermission(auth);
      } else if (READ_USER_TASK.equals(auth.permissionType())) {
        processReadUserTaskPermission(auth);
      }
    }

    private void processReadProcessInstancePermission(final Authorization<?> auth) {
      if (auth.isWildcard()) {
        hasProcessInstanceWildcard = true;
      } else if (auth.hasAnyResourceIds()) {
        processDefIdsForProcessInstance.addAll(auth.resourceIds());
      }
    }

    private void processReadUserTaskPermission(final Authorization<?> auth) {
      if (auth.isWildcard()) {
        hasUserTaskWildcard = true;
      } else if (auth.hasAnyResourceIds()) {
        processDefIdsForUserTask.addAll(auth.resourceIds());
      }
    }

    AuditLogAuthorizationFilter build() {
      return new AuditLogAuthorizationFilter(
          List.copyOf(authorizedCategories),
          List.copyOf(processDefIdsForProcessInstance),
          hasProcessInstanceWildcard,
          List.copyOf(processDefIdsForUserTask),
          hasUserTaskWildcard);
    }
  }
}
