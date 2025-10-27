/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.SequenceFlowDao;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSequenceFlowDao extends OpensearchSearchableDao<SequenceFlow, SequenceFlow>
    implements SequenceFlowDao {

  private final SequenceFlowTemplate sequenceFlowIndex;

  public OpensearchSequenceFlowDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final SequenceFlowTemplate sequenceFlowIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.sequenceFlowIndex = sequenceFlowIndex;
  }

  @Override
  protected String getUniqueSortKey() {
    return SequenceFlow.ID;
  }

  @Override
  protected Class<SequenceFlow> getInternalDocumentModelClass() {
    return SequenceFlow.class;
  }

  @Override
  protected String getIndexName() {
    return sequenceFlowIndex.getAlias();
  }

  @Override
  protected Stream<Query> collectFilterQueryTerms(@NonNull final SequenceFlow filter) {
    return Stream.of(
        queryDSLWrapper.term(SequenceFlow.ID, filter.getId()),
        queryDSLWrapper.term(SequenceFlow.ACTIVITY_ID, filter.getActivityId()),
        queryDSLWrapper.term(SequenceFlow.TENANT_ID, filter.getTenantId()),
        queryDSLWrapper.term(SequenceFlow.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
  }

  @Override
  protected SequenceFlow convertInternalToApiResult(final SequenceFlow internalResult) {
    return internalResult;
  }
}
