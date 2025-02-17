/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.LENIENT_EXPAND_OPEN_FORBID_NO_INDICES_IGNORE_THROTTLED;
import static java.util.Arrays.asList;

import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.data.DevDataGeneratorAbstract;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.zeebe.ZeebeESConstants;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev-data")
@Conditional(ElasticSearchCondition.class)
@ConditionalOnProperty(value = "camunda.tasklist.webappEnabled", matchIfMissing = true)
public class DevDataGeneratorElasticSearch extends DevDataGeneratorAbstract
    implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Override
  public void createUser(final String username, final String firstname, final String lastname) {
    final String password = username;
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity user =
        UserEntity.from(username, passwordEncoded, List.of("USER"))
            .setDisplayName(firstname + " " + lastname)
            .setRoles(asList("OWNER"));
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XContentType.JSON);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not create demo user with user id {}", user.getUserId(), t);
    }
    LOGGER.info("Created demo user {} with password {}", username, password);
  }

  @Override
  public boolean shouldCreateData() {
    try {
      final GetIndexRequest request =
          new GetIndexRequest(
              tasklistProperties.getZeebeElasticsearch().getPrefix()
                  + "*"
                  + ZeebeESConstants.DEPLOYMENT
                  + "*");
      request.indicesOptions(LENIENT_EXPAND_OPEN_FORBID_NO_INDICES_IGNORE_THROTTLED);
      final boolean exists = zeebeEsClient.indices().exists(request, RequestOptions.DEFAULT);
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    } catch (final IOException io) {
      LOGGER.debug(
          "Error occurred while checking existance of data in Zeebe: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
    return true;
  }
}
