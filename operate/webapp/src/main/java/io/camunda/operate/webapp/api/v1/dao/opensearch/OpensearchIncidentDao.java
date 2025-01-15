/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static java.lang.String.format;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.IncidentDao;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.opensearch.OpensearchIncident;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentDao extends OpensearchKeyFilteringDao<Incident, OpensearchIncident>
    implements IncidentDao {
  private final IncidentTemplate incidentIndex;

  private final OperateDateTimeFormatter dateTimeFormatter;

  public OpensearchIncidentDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final IncidentTemplate incidentIndex,
      final OperateDateTimeFormatter dateTimeFormatter) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.incidentIndex = incidentIndex;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  protected String getKeyFieldName() {
    return IncidentTemplate.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return format("Error in reading incident for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return format("No incident found for key %s ", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return format("Found more than one incidents for key %s", key);
  }

  @Override
  public Results<Incident> search(final Query<Incident> query) throws APIException {
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
  protected void buildFiltering(final Query<Incident> query, final SearchRequest.Builder request) {
    final Incident filter = query.getFilter();
    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(Incident.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      Incident.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
                  queryDSLWrapper.term(
                      Incident.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
                  queryDSLWrapper.term(Incident.TYPE, filter.getType()),
                  queryDSLWrapper.match(Incident.MESSAGE, filter.getMessage()),
                  queryDSLWrapper.term(Incident.STATE, filter.getState()),
                  queryDSLWrapper.term(Incident.JOB_KEY, filter.getJobKey()),
                  queryDSLWrapper.term(Incident.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.matchDateQuery(
                      Incident.CREATION_TIME,
                      filter.getCreationTime(),
                      dateTimeFormatter.getApiDateTimeFormatString()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected Incident convertInternalToApiResult(final OpensearchIncident osIncident) {
    return new Incident()
        .setKey(osIncident.key())
        .setProcessInstanceKey(osIncident.processInstanceKey())
        .setProcessDefinitionKey(osIncident.processDefinitionKey())
        .setType(osIncident.errorType())
        .setMessage(osIncident.errorMessage())
        .setCreationTime(dateTimeFormatter.convertGeneralToApiDateTime(osIncident.creationTime()))
        .setState(osIncident.state())
        .setJobKey(osIncident.jobKey())
        .setTenantId(osIncident.tenantId());
  }

  private void mapFieldsInSort(final Query<Incident> query) {
    if (query.getSort() == null) {
      return;
    }

    final var rewrittenSort =
        query.getSort().stream()
            .map(
                s ->
                    s.setField(
                        Incident.OBJECT_TO_SEARCH_MAP.getOrDefault(s.getField(), s.getField())))
            .toList();

    query.setSort(rewrittenSort);
  }
}
