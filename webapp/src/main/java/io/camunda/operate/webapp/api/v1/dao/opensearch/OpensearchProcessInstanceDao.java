/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessInstanceDao extends OpensearchKeyFilteringDao<ProcessInstance, ProcessInstance> implements ProcessInstanceDao {

  private final ListViewTemplate processInstanceIndex;

  private final ProcessInstanceWriter processInstanceWriter;

  public OpensearchProcessInstanceDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                      ListViewTemplate processInstanceIndex, RichOpenSearchClient richOpenSearchClient,
                                      ProcessInstanceWriter processInstanceWriter, OperateProperties operateProperties) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient, operateProperties);
    this.processInstanceIndex = processInstanceIndex;
    this.processInstanceWriter = processInstanceWriter;
  }

  @Override
  protected String getUniqueSortKey() {
    // This probably should be ProcessInstance.KEY to be consistent with how we always pull sort keys
    // from the constants in the index. Since the ElasticsearchDao uses this field too, leaving it as-is.

    // While the `processInstanceKey` field is not in the ProcessInstance model object, it can still be
    // used as a sort key (and has the same value as `key` which is what is referred to here with ListViewTemplate.KEY.
    return ListViewTemplate.KEY;
  }

  @Override
  protected String getKeyFieldName() {
    return ProcessInstance.KEY;
  }

  @Override
  protected Class<ProcessInstance> getModelClass() {
    return ProcessInstance.class;
  }

  @Override
  protected String getIndexName() {
    return processInstanceIndex.getAlias();
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading process instance for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No process instances found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one process instances for key %s", key);
  }

  @Override
  @PreAuthorize("hasPermission('write')")
  public ChangeStatus delete(Long key) throws APIException {
    // Check for not exists
    byKey(key);
    try {
      processInstanceWriter.deleteInstanceById(key);
      return new ChangeStatus().setDeleted(1)
          .setMessage(
              String.format( "Process instance and dependant data deleted for key '%s'", key));
    } catch(IllegalArgumentException iae){
      throw new ClientException(iae.getMessage(), iae);
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in deleting process instance and dependant data for key '%s'", key),e);
    }
  }

  @Override
  protected List<ProcessInstance> searchByKey(Long key) {
    List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();
    queryTerms.add(queryDSLWrapper.term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
    queryTerms.add(queryDSLWrapper.term(getKeyFieldName(), key));

    SearchRequest.Builder request = requestDSLWrapper.searchRequestBuilder(getIndexName())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.and(queryTerms)));

    return richOpenSearchClient.doc().searchValues(request, getModelClass());
  }

  @Override
  protected void buildFiltering(Query<ProcessInstance> query, SearchRequest.Builder request) {
    List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();
    queryTerms.add(queryDSLWrapper.term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));

    ProcessInstance filter = query.getFilter();

    if (filter != null) {
      if (filter.getKey() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.KEY, filter.getKey()));
      }
      if (filter.getProcessDefinitionKey() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      }
      if (filter.getParentKey() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.PARENT_KEY, filter.getParentKey()));
      }
      if (filter.getParentFlowNodeInstanceKey() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY, filter.getParentFlowNodeInstanceKey()));
      }
      if (filter.getProcessVersion() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.VERSION, filter.getProcessVersion()));
      }
      if (filter.getBpmnProcessId() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      }
      if (filter.getState() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.STATE, filter.getState()));
      }
      if (filter.getTenantId() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.TENANT_ID, filter.getTenantId()));
      }
      if (filter.getStartDate() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.START_DATE, filter.getStartDate()));
      }
      if (filter.getEndDate() != null) {
        queryTerms.add(queryDSLWrapper.term(ProcessInstance.END_DATE, filter.getEndDate()));
      }
    }

    request.query(queryDSLWrapper.and(queryTerms));
  }
}
