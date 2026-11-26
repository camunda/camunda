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

    final var grants = resolveGrants(resourceAccessChecks);

    // The mapper renders the authorization predicate only from the resolved grants. If none is
    // left, it would render no predicate and return every user task of the caller's tenants.
    if (resourceAccessChecks.authorizationCheck().enabled() && grants.matchNoUserTask()) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        UserTaskDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedProcessDefinitionIds(grants.processDefinitionIds())
                    .authorizedUserTaskKeys(grants.userTaskKeys())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .authorizationProperties(grants.properties())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for users with filter {}", dbQuery);
    return executePagedQuery(
        () -> userTaskMapper.count(dbQuery),
        () -> userTaskMapper.search(dbQuery).stream().map(UserTaskEntityMapper::toEntity).toList(),
        dbPage,
        dbSort);
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
   * Resolves the caller's grants to the values a user task can match:
   *
   * <ul>
   *   <li>process definition grants match by process definition ID;
   *   <li>user task grants match by user task key, so IDs that are not numbers match no task and
   *       are dropped;
   *   <li>property grants match by the caller's username or groups, so a property without a value
   *       for this caller (e.g. no username or no groups) is dropped.
   * </ul>
   */
  private UserTaskGrants resolveGrants(final ResourceAccessChecks resourceAccessChecks) {
    final var resourceIdsByType = resourceAccessChecks.getAuthorizedResourceIdsByType();
    final var processDefinitionIds =
        resourceIdsByType.getOrDefault(PROCESS_DEFINITION.name(), List.of());
    final var userTaskIds = resourceIdsByType.getOrDefault(USER_TASK.name(), List.of());
    final var userTaskKeys = NumberParsingUtil.parseLongs(userTaskIds);

    final var propertyNames =
        resourceAccessChecks
            .getAuthorizedResourcePropertyNamesByType()
            .getOrDefault(USER_TASK.name(), Set.of());
    final var properties = resolveAuthorizationProperties(propertyNames, resourceAccessChecks);

    return new UserTaskGrants(processDefinitionIds, userTaskKeys, properties);
  }

  /** Maps the authorized property names to the caller's username and groups. */
  private UserTaskAuthorizationProperties resolveAuthorizationProperties(
      final Set<String> userTaskPropertyNames, final ResourceAccessChecks resourceAccessChecks) {

    if (userTaskPropertyNames.isEmpty()) {
      return UserTaskAuthorizationProperties.EMPTY;
    }

    final var authentication = resourceAccessChecks.authentication();
    if (authentication == null) {
      LOG.warn(
          "Property-based authorization configured but no authentication context provided; "
              + "property-based grants resolve to no access.");
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

  /** The caller's grants, resolved to the values the user task query can match. */
  private record UserTaskGrants(
      List<String> processDefinitionIds,
      List<Long> userTaskKeys,
      UserTaskAuthorizationProperties properties) {

    boolean matchNoUserTask() {
      return processDefinitionIds.isEmpty()
          && userTaskKeys.isEmpty()
          && !properties.hasAnyProperty();
    }
  }
}
