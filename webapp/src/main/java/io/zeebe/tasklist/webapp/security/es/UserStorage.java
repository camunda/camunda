/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.zeebe.tasklist.entities.UserEntity;
import io.zeebe.tasklist.es.schema.indices.UserIndex;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.es.reader.AbstractReader;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import io.zeebe.tasklist.webapp.security.sso.SSOWebSecurityConfig;
import java.io.IOException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSOWebSecurityConfig.SSO_AUTH_PROFILE)
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

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }
}
