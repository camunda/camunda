/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.or;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.UserIdMigrationRepository;
import io.camunda.optimize.service.db.repository.script.UserIdMigrationScriptFactory;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class UserIdMigrationRepositoryOS implements UserIdMigrationRepository {

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
      org.slf4j.LoggerFactory.getLogger(UserIdMigrationRepositoryOS.class);

  private final OptimizeOpenSearchClient osClient;

  public UserIdMigrationRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public boolean hasDocumentsWithUserId(final String userId) {
    try {
      return osClient.count(ALL_ENTITY_INDICES, buildUserIdQuery(userId)) > 0;
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
    osClient.updateByQueryTask(
        "collection roles for user " + oldUserId,
        rolesScript,
        buildCollectionRolesQuery(oldUserId),
        COLLECTION_INDEX_NAME);

    final Script ownerScript =
        createDefaultScriptWithPrimitiveParams(
            UserIdMigrationScriptFactory.createMigrateOwnerScript(), params);
    osClient.updateByQueryTask(
        "owner/lastModifier for user " + oldUserId,
        ownerScript,
        buildOwnerLastModifierQuery(oldUserId),
        ALL_ENTITY_INDICES);

    LOG.info("Migration of entities for user [{}] to [{}] complete", oldUserId, newUserId);
  }

  private Query buildUserIdQuery(final String userId) {
    // data.roles is only mapped on the collection index; ignoreUnmapped keeps this nested clause
    // from failing the count on the other 6 indices
    final Query rolesQuery =
        NestedQuery.of(
                n ->
                    n.path("data.roles")
                        .ignoreUnmapped(true)
                        .scoreMode(ChildScoreMode.None)
                        .query(term("data.roles.identity.id", userId)))
            .toQuery();
    return or(rolesQuery, term("owner", userId), term("lastModifier", userId));
  }

  private Query buildCollectionRolesQuery(final String userId) {
    return nested("data.roles", term("data.roles.identity.id", userId), ChildScoreMode.None);
  }

  private Query buildOwnerLastModifierQuery(final String userId) {
    return or(term("owner", userId), term("lastModifier", userId));
  }
}
