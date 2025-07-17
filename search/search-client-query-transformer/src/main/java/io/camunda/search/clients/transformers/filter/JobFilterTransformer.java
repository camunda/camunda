/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.ERROR_CODE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.ERROR_MESSAGE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.FLOW_NODE_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_DEADLINE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_DENIED;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_DENIED_REASON;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_FAILED_WITH_RETRIES_LEFT;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_KIND;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_STATE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_TYPE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_WORKER;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.LISTENER_EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.RETRIES;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.TIME;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.JobFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class JobFilterTransformer extends IndexFilterTransformer<JobFilter> {

  public JobFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final JobFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    of(dateTimeOperations(JOB_DEADLINE, filter.deadlineOperations())).ifPresent(queries::addAll);
    of(stringOperations(JOB_DENIED_REASON, filter.deniedReasonOperations()))
        .ifPresent(queries::addAll);
    of(stringOperations(FLOW_NODE_ID, filter.elementIdOperations())).ifPresent(queries::addAll);
    of(longOperations(FLOW_NODE_INSTANCE_ID, filter.elementInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    of(dateTimeOperations(TIME, filter.endTimeOperations())).ifPresent(queries::addAll);
    of(stringOperations(ERROR_CODE, filter.errorCodeOperations())).ifPresent(queries::addAll);
    of(stringOperations(ERROR_MESSAGE, filter.errorMessageOperations())).ifPresent(queries::addAll);
    of(longOperations(JOB_KEY, filter.jobKeyOperations())).ifPresent(queries::addAll);
    of(stringOperations(JOB_KIND, filter.kindOperations())).ifPresent(queries::addAll);
    of(stringOperations(LISTENER_EVENT_TYPE, filter.listenerEventTypeOperations()))
        .ifPresent(queries::addAll);
    of(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()))
        .ifPresent(queries::addAll);
    of(longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()))
        .ifPresent(queries::addAll);
    of(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    of(intOperations(RETRIES, filter.retriesOperations())).ifPresent(queries::addAll);
    of(stringOperations(JOB_STATE, filter.stateOperations())).ifPresent(queries::addAll);
    of(stringOperations(TENANT_ID, filter.tenantIdOperations())).ifPresent(queries::addAll);
    of(stringOperations(JOB_TYPE, filter.typeOperations())).ifPresent(queries::addAll);
    of(stringOperations(JOB_WORKER, filter.workerOperations())).ifPresent(queries::addAll);
    ofNullable(filter.hasFailedWithRetriesLeft())
        .ifPresent(f -> queries.add(term(JOB_FAILED_WITH_RETRIES_LEFT, f)));
    ofNullable(filter.isDenied()).ifPresent(f -> queries.add(term(JOB_DENIED, f)));

    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
