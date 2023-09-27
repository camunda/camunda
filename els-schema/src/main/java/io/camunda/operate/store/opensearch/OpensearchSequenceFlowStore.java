/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import org.opensearch.client.opensearch._types.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSequenceFlowStore implements SequenceFlowStore {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchSequenceFlowStore.class);
  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public List<SequenceFlowEntity> getSequenceFlowsByProcessInstanceKey(Long processInstanceKey) {
    var query = constantScore(term(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));
    var searchRequestBuilder = searchRequestBuilder(sequenceFlowTemplate, RequestDSL.QueryType.ALL)
      .query(withTenantCheck(constantScore(term(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
      .sort(sortOptions(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.Asc));
    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, SequenceFlowEntity.class);
  }
}
