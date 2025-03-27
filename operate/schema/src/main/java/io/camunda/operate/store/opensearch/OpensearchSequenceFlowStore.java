/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import java.util.List;
import org.opensearch.client.opensearch._types.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSequenceFlowStore implements SequenceFlowStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchSequenceFlowStore.class);
  @Autowired private SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<SequenceFlowEntity> getSequenceFlowsByProcessInstanceKey(
      final Long processInstanceKey) {
    final var query =
        constantScore(term(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));
    final var searchRequestBuilder =
        searchRequestBuilder(sequenceFlowTemplate, RequestDSL.QueryType.ALL)
            .query(
                withTenantCheck(
                    constantScore(
                        term(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
            .sort(sortOptions(SequenceFlowTemplate.ACTIVITY_ID, SortOrder.Asc));
    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, SequenceFlowEntity.class);
  }
}
