/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import java.io.IOException;
import java.util.List;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class SequenceFlowReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(SequenceFlowReader.class);

  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  public List<SequenceFlowEntity> getSequenceFlowsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);

    final ConstantScoreQueryBuilder query = constantScoreQuery(processInstanceKeyQuery);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(sequenceFlowTemplate, ElasticsearchUtil.QueryType.ALL)
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.ASC));
    try {
      return scroll(searchRequest, SequenceFlowEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining sequence flows: %s for processInstanceKey %s", e.getMessage(),processInstanceKey);
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
