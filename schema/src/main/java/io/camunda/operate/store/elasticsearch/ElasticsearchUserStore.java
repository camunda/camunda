/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.UserStore;
import io.camunda.operate.util.ElasticsearchUtil;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
@DependsOn("schemaStartup")
@Profile(
    "!"
        + OperateProfileService.LDAP_AUTH_PROFILE
        + " & !"
        + OperateProfileService.SSO_AUTH_PROFILE
        + " & !"
        + OperateProfileService.IDENTITY_AUTH_PROFILE)
public class ElasticsearchUserStore implements UserStore {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchUserStore.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired private UserIndex userIndex;

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }

  @Override
  public UserEntity getById(String id) {
    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder().query(QueryBuilders.termQuery(UserIndex.USER_ID, id)));
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void save(UserEntity user) {
    try {
      IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Exception t) {
      logger.error("Could not create user with userId {}", user.getUserId(), t);
    }
  }
}
