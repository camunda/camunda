/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se.store;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.descriptors.index.TasklistUserIndex;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ScriptSort;
import org.opensearch.client.opensearch._types.ScriptSortType;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE)
@DependsOn("searchEngineSchemaInitializer")
@Conditional(OpenSearchCondition.class)
public class UserStoreOpenSearch implements UserStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserStoreOpenSearch.class);

  @Autowired private TasklistUserIndex userIndex;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Override
  public UserEntity getByUserId(final String userId) {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(userIndex.getAlias())
            .query(
                q ->
                    q.term(
                        t -> t.field(TasklistUserIndex.USER_ID).value(v -> v.stringValue(userId))))
            .build();

    try {
      final SearchResponse<UserEntity> response =
          openSearchClient.search(searchRequest, UserEntity.class);
      final var totalHits = response.hits().total().value();

      if (totalHits == 1) {
        return response.hits().hits().stream().findFirst().orElseThrow().source();
      }

      throw new NotFoundApiException(
          totalHits > 1
              ? String.format("Could not find unique user with userId '%s'.", userId)
              : String.format("Could not find user with userId '%s'.", userId));
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public void create(final UserEntity user) {
    try {
      final IndexRequest<UserEntity> request =
          IndexRequest.of(
              builder ->
                  builder.index(userIndex.getFullQualifiedName()).id(user.getId()).document(user));

      openSearchClient.index(request);
    } catch (final Exception e) {
      LOGGER.error("Could not create user with user id {}", user.getUserId(), e);
    }
  }

  @Override
  public List<UserEntity> getUsersByUserIds(final List<String> userIds) {
    final ConstantScoreQueryBuilder esQuery =
        constantScoreQuery(idsQuery().addIds(userIds.toArray(String[]::new)));

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(userIndex.getAlias())
            .query(q -> q.constantScore(qs -> qs.filter(qf -> qf.ids(iq -> iq.values(userIds)))))
            .sort(s -> s.script(getScriptSort(userIds)))
            .source(
                s ->
                    s.filter(
                        sf ->
                            sf.includes(
                                TasklistUserIndex.USER_ID, TasklistUserIndex.DISPLAY_NAME)));

    try {
      return OpenSearchUtil.scroll(searchRequest, UserEntity.class, openSearchClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private ScriptSort getScriptSort(final List<String> userIds) {
    final String scriptCode =
        String.format(
            "def userIdsCount = params.userIds.size();"
                + "def userId = doc['%s'].value;"
                + "def foundIdx = params.userIds.indexOf(userId);"
                + "return foundIdx > -1 ? foundIdx: userIdsCount + 1;",
            TasklistUserIndex.USER_ID);
    return new ScriptSort.Builder()
        .type(ScriptSortType.Number)
        .script(
            s ->
                s.inline(
                    is ->
                        is.lang("painless")
                            .source(scriptCode)
                            .params("userIds", JsonData.of(userIds))))
        .order(SortOrder.Asc)
        .build();
  }
}
