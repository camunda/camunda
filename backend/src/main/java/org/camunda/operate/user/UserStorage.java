/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;

import java.io.IOException;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.es.reader.AbstractReader;
import org.camunda.operate.es.schema.templates.UserTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class UserStorage extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(UserStorage.class);
  private static final RefreshPolicy REFRESH_POLICY = RefreshPolicy.IMMEDIATE;
  private static final RequestOptions REQUEST_OPTIONS = RequestOptions.DEFAULT;
  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired
  private UserTemplate userTemplate;

  public UserEntity getUserByName(String username) {
    final SearchRequest searchRequest = new SearchRequest(userTemplate.getAlias())
        .source(new SearchSourceBuilder()
          .query(QueryBuilders.termQuery(UserTemplate.USERNAME, username)));
      try {
        final SearchResponse response = esClient.search(searchRequest,REQUEST_OPTIONS);
        if (response.getHits().totalHits == 1) {
          return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
        } else if (response.getHits().totalHits > 1) {
          throw new NotFoundException(String.format("Could not find unique user with username '%s'.", username));
        } else {
          throw new NotFoundException(String.format("Could not find user with username '%s'.", username));
        }
      } catch (IOException e) {
        final String message = String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
        logger.error(message, e);
        throw new OperateRuntimeException(message, e);
      }
  }

  public void createUser(UserEntity user) {
    try {
      IndexRequest request = new IndexRequest(userTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE,user.getId())
          .source(userEntityToJSONString(user), XCONTENT_TYPE)
          .setRefreshPolicy(REFRESH_POLICY);
      esClient.index(request,REQUEST_OPTIONS);
    } catch (Throwable t) {
      logger.error("Could not create user with username {}", user.getUsername(), t);
    }    
  }

  public void saveUser(UserEntity user) {
    try {
      UpdateRequest request = new UpdateRequest(userTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, user.getId())
          .setRefreshPolicy(REFRESH_POLICY)
          .doc(userEntityToJSONString(user),XCONTENT_TYPE);
      esClient.update(request,REQUEST_OPTIONS);
    }catch (Throwable t) {
      logger.error("Could not save user with username {}", user.getUsername(), t);
    }
  }

  public void deleteUserById(String id) {
    try {
      DeleteRequest request = new DeleteRequest(userTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE,id)
          .setRefreshPolicy(REFRESH_POLICY); 
      esClient.delete(request,REQUEST_OPTIONS);
    } catch (Throwable t) {
      logger.error("Could not delete user by id {}", id, t);
    }    
  }

  public boolean usersExists() {
    try {
      final SearchRequest request = new SearchRequest(userTemplate.getAlias()).source(new SearchSourceBuilder());
      final SearchResponse response = esClient.search(request,REQUEST_OPTIONS);
      return response.getHits().totalHits > 0;
    } catch (Throwable t) {
      logger.info("Could not request existing users. ", t);
      return false;
    }
  }
 
  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }
 
}
