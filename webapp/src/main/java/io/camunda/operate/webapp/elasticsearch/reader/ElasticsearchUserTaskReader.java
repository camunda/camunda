/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.UserTaskReader;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchUserTaskReader extends AbstractReader implements UserTaskReader {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchUserTaskReader.class);

  private final UserTaskTemplate userTaskTemplate;

  public ElasticsearchUserTaskReader(UserTaskTemplate userTaskTemplate) {
    this.userTaskTemplate = userTaskTemplate;
  }

  @Override
  public List<UserTaskEntity> getUserTasks() {
    logger.debug("retrieve all user tasks");
    try {
      final QueryBuilder query = matchAllQuery();
      SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(userTaskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(query)));
      SearchResponse response = tenantAwareClient.search(searchRequest);
      return ElasticsearchUtil.mapSearchHits(
          response.getHits().getHits(), objectMapper, UserTaskEntity.class);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
