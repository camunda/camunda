/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.es;

import static io.zeebe.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.zeebe.tasklist.entities.UserEntity;
import io.zeebe.tasklist.es.schema.indices.UserIndex;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.es.reader.AbstractReader;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE)
@DependsOn("schemaManager")
public class UserStorage extends AbstractReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserStorage.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired private UserIndex userIndex;

  public UserEntity getByName(String username) {
    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(UserIndex.USERNAME, username)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
      } else if (response.getHits().totalHits > 1) {
        throw new NotFoundException(
            String.format("Could not find unique user with username '%s'.", username));
      } else {
        throw new NotFoundException(
            String.format("Could not find user with username '%s'.", username));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  public void create(UserEntity user) {
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, user.getId())
              .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Throwable t) {
      LOGGER.error("Could not create user with username {}", user.getUsername(), t);
    }
  }

  public List<UserEntity> getUsersByUsernames(List<String> usernames) {

    // TODO query only needed fields
    final ConstantScoreQueryBuilder esQuery =
        constantScoreQuery(idsQuery().addIds(usernames.toArray(String[]::new)));

    // TODO #47 we need the results in same order as list of ids: script exapmles:
    // https://gist.github.com/darklow/7132077
    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(esQuery)
                    .fetchSource(
                        new String[] {UserIndex.USERNAME, UserIndex.FIRSTNAME, UserIndex.LASTNAME},
                        null));

    try {
      final List<UserEntity> userEntities =
          ElasticsearchUtil.scroll(searchRequest, UserEntity.class, objectMapper, esClient);
      return userEntities;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }
}
