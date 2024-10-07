/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.operate.template.UserTaskTemplate;
import io.camunda.webapps.schema.entities.operate.UserTaskEntity;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchUserTaskReader extends AbstractReader implements UserTaskReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUserTaskReader.class);

  private final UserTaskTemplate userTaskTemplate;

  public ElasticsearchUserTaskReader(final UserTaskTemplate userTaskTemplate) {
    this.userTaskTemplate = userTaskTemplate;
  }

  @Override
  public List<UserTaskEntity> getUserTasks() {
    LOGGER.debug("retrieve all user tasks");
    try {
      final QueryBuilder query = matchAllQuery();
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(userTaskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(query)));
      return scroll(searchRequest, UserTaskEntity.class);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Optional<UserTaskEntity> getUserTaskByFlowNodeInstanceKey(final long flowNodeInstanceKey) {
    LOGGER.debug("Get UserTask by flowNodeInstanceKey {}", flowNodeInstanceKey);
    try {
      final QueryBuilder query =
          termQuery(UserTaskTemplate.ELEMENT_INSTANCE_KEY, flowNodeInstanceKey);
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(userTaskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(query)));
      final var hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value == 1) {
        return Optional.of(
            ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, UserTaskEntity.class)
                .get(0));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return Optional.empty();
  }
}
