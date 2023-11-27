/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.SequenceFlowDao;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSequenceFlowDao extends OpensearchSearchableDao<SequenceFlow, SequenceFlow> implements SequenceFlowDao {

  private final SequenceFlowTemplate sequenceFlowIndex;

  public OpensearchSequenceFlowDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                   SequenceFlowTemplate sequenceFlowIndex, RichOpenSearchClient richOpenSearchClient) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.sequenceFlowIndex = sequenceFlowIndex;
  }

  @Override
  protected SequenceFlow convertInternalToApiResult(SequenceFlow internalResult) {
    return internalResult;
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
  protected void buildFiltering(Query<SequenceFlow> query, SearchRequest.Builder request) {
    SequenceFlow filter = query.getFilter();

    if (filter != null) {
      List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();

      if (filter.getId() != null) {
        queryTerms.add(queryDSLWrapper.term(SequenceFlow.ID, filter.getId()));
      }
      if (filter.getActivityId() != null) {
        queryTerms.add(queryDSLWrapper.term(SequenceFlow.ACTIVITY_ID, filter.getActivityId()));
      }
      if (filter.getTenantId() != null) {
        queryTerms.add(queryDSLWrapper.term(SequenceFlow.TENANT_ID, filter.getTenantId()));
      }
      if (filter.getProcessInstanceKey() != null) {
        queryTerms.add(queryDSLWrapper.term(SequenceFlow.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      }

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }
}
