/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.UserStore;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.index.OperateUserIndex;
import java.io.IOException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
@DependsOn("searchEngineSchemaInitializer")
@Profile(
    "!"
        + OperateProfileService.LDAP_AUTH_PROFILE
        + " & !"
        + OperateProfileService.SSO_AUTH_PROFILE
        + " & !"
        + OperateProfileService.IDENTITY_AUTH_PROFILE)
public class ElasticsearchUserStore implements UserStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUserStore.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired private OperateUserIndex operateUserIndex;

  protected String userEntityToJSONString(final UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }

  @Override
  public UserEntity getById(final String id) {
    final SearchRequest searchRequest =
        new SearchRequest(operateUserIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(OperateUserIndex.USER_ID, id)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique user with userId '%s'.", id));
      } else {
        throw new NotFoundException(String.format("Could not find user with userId '%s'.", id));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void save(final UserEntity user) {
    try {
      final IndexRequest request =
          new IndexRequest(operateUserIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not create user with userId {}", user.getUserId(), t);
    }
  }
}
