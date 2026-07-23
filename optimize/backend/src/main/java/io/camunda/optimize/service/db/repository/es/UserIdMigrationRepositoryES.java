/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.UserIdMigrationRepository;
import io.camunda.optimize.service.db.repository.script.UserIdMigrationScriptFactory;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class UserIdMigrationRepositoryES implements UserIdMigrationRepository {

  private static final String[] ALL_ENTITY_INDICES = {
    COLLECTION_INDEX_NAME,
    SINGLE_PROCESS_REPORT_INDEX_NAME,
    SINGLE_DECISION_REPORT_INDEX_NAME,
    COMBINED_REPORT_INDEX_NAME,
    DASHBOARD_INDEX_NAME,
    ALERT_INDEX_NAME,
    PROCESS_OVERVIEW_INDEX_NAME
  };

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(UserIdMigrationRepositoryES.class);

  private final OptimizeElasticsearchClient esClient;
  private final TaskRepositoryES taskRepository;

  public UserIdMigrationRepositoryES(
      final OptimizeElasticsearchClient esClient, final TaskRepositoryES taskRepository) {
    this.esClient = esClient;
    this.taskRepository = taskRepository;
  }

  @Override
  public boolean hasDocumentsWithUserId(final String userId) {
    try {
      return esClient.count(ALL_ENTITY_INDICES, buildUserIdBoolQuery(userId)) > 0;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Failed to check for documents with user ID: " + userId, e);
    }
  }

  @Override
  public void migrateUserId(final String oldUserId, final String newUserId) {
    LOG.info("Migrating Optimize entities from user ID [{}] to [{}]", oldUserId, newUserId);
    final Map<String, JsonData> params =
        Map.of("oldId", JsonData.of(oldUserId), "newId", JsonData.of(newUserId));

    final Script rolesScript =
        createDefaultScriptWithPrimitiveParams(
            UserIdMigrationScriptFactory.createMigrateCollectionRolesScript(), params);
    final Query rolesQuery = buildCollectionRolesQuery(oldUserId);
    taskRepository.tryUpdateByQueryRequest(
        "collection roles for user " + oldUserId, rolesScript, rolesQuery, COLLECTION_INDEX_NAME);

    final Script ownerScript =
        createDefaultScriptWithPrimitiveParams(
            UserIdMigrationScriptFactory.createMigrateOwnerScript(), params);
    final Query ownerQuery = buildOwnerLastModifierQuery(oldUserId);
    taskRepository.tryUpdateByQueryRequest(
        "owner/lastModifier for user " + oldUserId, ownerScript, ownerQuery, ALL_ENTITY_INDICES);

    LOG.info("Migration of entities for user [{}] to [{}] complete", oldUserId, newUserId);
  }

  private BoolQuery.Builder buildUserIdBoolQuery(final String userId) {
    return new BoolQuery.Builder()
        .should(
            s ->
                s.nested(
                    n ->
                        // data.roles is only mapped on the collection index; ignoreUnmapped keeps
                        // this nested clause from failing the count on the other 6 indices
                        n.path("data.roles")
                            .ignoreUnmapped(true)
                            .scoreMode(ChildScoreMode.None)
                            .query(
                                qq ->
                                    qq.term(t -> t.field("data.roles.identity.id").value(userId)))))
        .should(s -> s.term(t -> t.field("owner").value(userId)))
        .should(s -> s.term(t -> t.field("lastModifier").value(userId)))
        .minimumShouldMatch("1");
  }

  private Query buildCollectionRolesQuery(final String userId) {
    return Query.of(
        q ->
            q.nested(
                n ->
                    n.path("data.roles")
                        .scoreMode(ChildScoreMode.None)
                        .query(
                            qq -> qq.term(t -> t.field("data.roles.identity.id").value(userId)))));
  }

  private Query buildOwnerLastModifierQuery(final String userId) {
    return Query.of(
        q ->
            q.bool(
                b ->
                    b.should(s -> s.term(t -> t.field("owner").value(userId)))
                        .should(s -> s.term(t -> t.field("lastModifier").value(userId)))
                        .minimumShouldMatch("1")));
  }
}
