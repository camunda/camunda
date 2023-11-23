/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.IncidentDao;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.opensearch.OpensearchIncident;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentDao extends OpensearchKeyFilteringDao<Incident, OpensearchIncident> implements IncidentDao {
  @Autowired
  private IncidentTemplate incidentIndex;

  public OpensearchIncidentDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper, RichOpenSearchClient richOpenSearchClient, OperateProperties operateProperties) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient, operateProperties);
  }

  @Override
  protected String getKeyFieldName() {
    return IncidentTemplate.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return format("Error in reading incident for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return format("No incident found for key %s ", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return format("Found more than one incidents for key %s", key);
  }

  @Override
  public Results<Incident> search(Query<Incident> query) throws APIException {
    mapFieldsInSort(query);

    return super.search(query);
  }

  @Override
  protected String getUniqueSortKey() {
    return Incident.KEY;
  }

  @Override
  protected Class<OpensearchIncident> getModelClass() {
    return OpensearchIncident.class;
  }

  @Override
  protected String getIndexName() {
    return incidentIndex.getAlias();
  }

  @Override
  protected void buildFiltering(Query<Incident> query, SearchRequest.Builder request) {
    final Incident filter = query.getFilter();
    List<org.opensearch.client.opensearch._types.query_dsl.Query> queryBuilders = new ArrayList<>();
    if (filter != null) {
      var queryTerms = Arrays.asList(
        queryDSLWrapper.buildTermQuery(Incident.KEY, filter.getKey()),
        queryDSLWrapper.buildTermQuery(Incident.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
        queryDSLWrapper.buildTermQuery(Incident.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
        queryDSLWrapper.buildTermQuery(Incident.TYPE, filter.getType()),
        queryDSLWrapper.buildMatchQuery(Incident.MESSAGE, filter.getMessage()),
        queryDSLWrapper.buildTermQuery(Incident.STATE, filter.getState()),
        queryDSLWrapper.buildTermQuery(Incident.JOB_KEY, filter.getJobKey()),
        queryDSLWrapper.buildTermQuery(Incident.TENANT_ID, filter.getTenantId()),
        queryDSLWrapper.buildMatchDateQuery(Incident.CREATION_TIME, filter.getCreationTime(), operateProperties.getOpensearch().getDateFormat())
      );
      request.query(queryDSLWrapper.and(queryTerms));
    }
  }

  private void mapFieldsInSort(final Query<Incident> query) {
    if (query.getSort() == null) {
      return;
    }

    var rewrittenSort = query.getSort()
      .stream()
      .map(s -> s.setField(Incident.OBJECT_TO_ELASTICSEARCH.getOrDefault(s.getField(), s.getField())))
      .toList();

    query.setSort(rewrittenSort);
  }

  @Override
  protected Incident transformSourceToItem(OpensearchIncident osIncident) {
    return new Incident()
      .setKey(osIncident.key())
      .setProcessInstanceKey(osIncident.processInstanceKey())
      .setProcessDefinitionKey(osIncident.processDefinitionKey())
      .setType(osIncident.errorType())
      .setMessage(osIncident.errorMessage())
      .setCreationTime(osIncident.creationTime())
      .setState(osIncident.state())
      .setJobKey(osIncident.jobKey())
      .setTenantId(osIncident.tenantId());
  }
}
