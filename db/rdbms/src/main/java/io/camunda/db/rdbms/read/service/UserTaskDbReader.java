/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.USER_TASK;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.db.rdbms.read.domain.UserTaskDbQuery.UserTaskAuthorizationProperties;
import io.camunda.db.rdbms.read.mapper.UserTaskEntityMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.columns.UserTaskSearchColumn;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.util.NumberParsingUtil;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskDbReader extends AbstractEntityReader<UserTaskEntity>
    implements UserTaskReader {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskDbReader.class);

  private final UserTaskMapper userTaskMapper;

  public UserTaskDbReader(
      final UserTaskMapper userTaskMapper, final RdbmsReaderConfig readerConfig) {
    super(UserTaskSearchColumn.values(), readerConfig);
    this.userTaskMapper = userTaskMapper;
  }

  @Override
  public UserTaskEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), UserTaskSearchColumn.USER_TASK_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    // ID-based authorization
    final var resourceIdsByType = resourceAccessChecks.getAuthorizedResourceIdsByType();
    final var processDefinitionIds =
        resourceIdsByType.getOrDefault(PROCESS_DEFINITION.name(), List.of());
    final var userTaskKeys = resourceIdsByType.getOrDefault(USER_TASK.name(), List.of());

    // Property-based authorization
    final var authorizationProperties = resolveAuthorizationProperties(resourceAccessChecks);

    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        UserTaskDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedProcessDefinitionIds(processDefinitionIds)
                    .authorizedUserTaskKeys(NumberParsingUtil.parseLongs(userTaskKeys))
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .authorizationProperties(authorizationProperties)
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for users with filter {}", dbQuery);
    final var totalHits = userTaskMapper.count(dbQuery);
    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        userTaskMapper.search(dbQuery).stream().map(UserTaskEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<UserTaskEntity> findOne(final long userTaskKey) {
    final var result =
        search(UserTaskQuery.of(b -> b.filter(f -> f.userTaskKeys(List.of(userTaskKey)))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  /**
   * Resolves property-based authorization by extracting authorized property names and mapping them
   * to user identity values from authentication context.
   */
  private UserTaskAuthorizationProperties resolveAuthorizationProperties(
      final ResourceAccessChecks resourceAccessChecks) {

    final var propertyNamesByType = resourceAccessChecks.getAuthorizedResourcePropertyNamesByType();
    final var userTaskPropertyNames = propertyNamesByType.getOrDefault(USER_TASK.name(), Set.of());

    if (userTaskPropertyNames.isEmpty()) {
      return UserTaskAuthorizationProperties.EMPTY;
    }

    final var authentication = resourceAccessChecks.authentication();
    if (authentication == null) {
      LOG.warn(
          "Property-based authorization configured but no authentication context provided; "
              + "property-based checks will not be applied.");
      return UserTaskAuthorizationProperties.EMPTY;
    }

    return buildAuthorizationProperties(userTaskPropertyNames, authentication);
  }

  /** Builds authorization properties by mapping property names to values from authentication. */
  private UserTaskAuthorizationProperties buildAuthorizationProperties(
      final Set<String> propertyNames, final CamundaAuthentication authentication) {

    final var username = authentication.authenticatedUsername();
    final var groups = authentication.authenticatedGroupIds();

    final var builder = UserTaskAuthorizationProperties.builder();

    for (final var propertyName : propertyNames) {
      switch (propertyName) {
        case Authorization.PROP_ASSIGNEE -> {
          if (username != null && !username.isEmpty()) {
            builder.assignee(username);
          }
        }
        case Authorization.PROP_CANDIDATE_USERS -> {
          if (username != null && !username.isEmpty()) {
            builder.candidateUsers(List.of(username));
          }
        }
        case Authorization.PROP_CANDIDATE_GROUPS -> {
          if (groups != null && !groups.isEmpty()) {
            builder.candidateGroups(groups);
          }
        }
        default ->
            LOG.warn(
                "Unknown property name '{}' for USER_TASK property-based authorization; ignoring.",
                propertyName);
      }
    }

    return builder.build();
  }
}
