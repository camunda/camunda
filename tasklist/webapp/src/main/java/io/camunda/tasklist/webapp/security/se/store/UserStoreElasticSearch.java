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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.descriptors.index.TasklistUserIndex;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
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
@Conditional(ElasticSearchCondition.class)
public class UserStoreElasticSearch implements UserStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserStoreElasticSearch.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired private TasklistUserIndex userIndex;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public UserEntity getByUserId(final String userId) {
    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(TasklistUserIndex.USER_ID, userId)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundApiException(
            String.format("Could not find unique user with userId '%s'.", userId));
      } else {
        throw new NotFoundApiException(
            String.format("Could not find user with userId '%s'.", userId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public void create(final UserEntity user) {
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (final Exception e) {
      LOGGER.error("Could not create user with user id {}", user.getUserId(), e);
    }
  }

  @Override
  public List<UserEntity> getUsersByUserIds(final List<String> userIds) {
    final ConstantScoreQueryBuilder esQuery =
        constantScoreQuery(idsQuery().addIds(userIds.toArray(String[]::new)));

    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(esQuery)
                    .sort(
                        SortBuilders.scriptSort(
                                getScript(userIds), ScriptSortBuilder.ScriptSortType.NUMBER)
                            .order(SortOrder.ASC))
                    .fetchSource(
                        new String[] {TasklistUserIndex.USER_ID, TasklistUserIndex.DISPLAY_NAME},
                        null));

    try {
      return ElasticsearchUtil.scroll(searchRequest, UserEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private Script getScript(final List<String> userIds) {
    final String scriptCode =
        String.format(
            "def userIdsCount = params.userIds.size();"
                + "def userId = doc['%s'].value;"
                + "def foundIdx = params.userIds.indexOf(userId);"
                + "return foundIdx > -1 ? foundIdx: userIdsCount + 1;",
            TasklistUserIndex.USER_ID);
    return new Script(
        ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, scriptCode, Map.of("userIds", userIds));
  }

  protected String userEntityToJSONString(final UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }
}
