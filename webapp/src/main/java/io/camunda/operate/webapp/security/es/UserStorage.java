/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.es;

import io.camunda.operate.webapp.security.OperateProfileService;
import java.io.IOException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.webapp.es.reader.AbstractReader;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;


@Component
@Profile("!" + OperateProfileService.LDAP_AUTH_PROFILE
    + " & !" + OperateProfileService.SSO_AUTH_PROFILE
    + " & !" + OperateProfileService.IAM_AUTH_PROFILE
    + " & !" + OperateProfileService.IDENTITY_AUTH_PROFILE
)
@DependsOn("schemaStartup")
public class UserStorage extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(UserStorage.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired
  private UserIndex userIndex;

  public UserEntity getByUserId(String userId) {
    final SearchRequest searchRequest = new SearchRequest(userIndex.getAlias())
        .source(new SearchSourceBuilder()
          .query(QueryBuilders.termQuery(UserIndex.USER_ID, userId)));
      try {
        final SearchResponse response = esClient.search(searchRequest,RequestOptions.DEFAULT);
        if (response.getHits().getTotalHits().value == 1) {
          return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
        } else if (response.getHits().getTotalHits().value > 1) {
          throw new NotFoundException(String.format("Could not find unique user with userId '%s'.", userId));
        } else {
          throw new NotFoundException(String.format("Could not find user with userId '%s'.", userId));
        }
      } catch (IOException e) {
        final String message = String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
        throw new OperateRuntimeException(message, e);
      }
  }

  public void create(UserEntity user) {
    try {
      IndexRequest request = new IndexRequest(userIndex.getFullQualifiedName()).id(user.getId())
          .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request,RequestOptions.DEFAULT);
    } catch (Exception t) {
      logger.error("Could not create user with userId {}", user.getUserId(), t);
    }
  }

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }

}
