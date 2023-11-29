/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OpensearchProperties;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentDao extends OpensearchKeyFilteringDao<Incident, OpensearchIncident> implements IncidentDao {
  private final OpensearchProperties opensearchProperties;
  private final IncidentTemplate incidentIndex;

  public OpensearchIncidentDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                               RichOpenSearchClient richOpenSearchClient, IncidentTemplate incidentIndex,
                               OperateProperties operateProperties) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.opensearchProperties = operateProperties.getOpensearch();
    this.incidentIndex = incidentIndex;
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
  protected Class<OpensearchIncident> getInternalDocumentModelClass() {
    return OpensearchIncident.class;
  }

  @Override
  protected String getIndexName() {
    return incidentIndex.getAlias();
  }

  @Override
  protected void buildFiltering(Query<Incident> query, SearchRequest.Builder request) {
    final Incident filter = query.getFilter();
    if (filter != null) {
      var queryTerms = Stream.of(
        queryDSLWrapper.term(Incident.KEY, filter.getKey()),
        queryDSLWrapper.term(Incident.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
        queryDSLWrapper.term(Incident.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
        queryDSLWrapper.term(Incident.TYPE, filter.getType()),
        queryDSLWrapper.match(Incident.MESSAGE, filter.getMessage()),
        queryDSLWrapper.term(Incident.STATE, filter.getState()),
        queryDSLWrapper.term(Incident.JOB_KEY, filter.getJobKey()),
        queryDSLWrapper.term(Incident.TENANT_ID, filter.getTenantId()),
        queryDSLWrapper.matchDateQuery(Incident.CREATION_TIME, filter.getCreationTime(), opensearchProperties.getDateFormat())
      ).filter(Objects::nonNull).collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  private void mapFieldsInSort(final Query<Incident> query) {
    if (query.getSort() == null) {
      return;
    }

    var rewrittenSort = query.getSort()
      .stream()
      .map(s -> s.setField(Incident.OBJECT_TO_SEARCH_MAP.getOrDefault(s.getField(), s.getField())))
      .toList();

    query.setSort(rewrittenSort);
  }

  @Override
  protected Incident convertInternalToApiResult(OpensearchIncident osIncident) {
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
