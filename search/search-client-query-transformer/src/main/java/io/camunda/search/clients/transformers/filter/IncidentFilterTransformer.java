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
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.CREATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG_HASH;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.TREE_PATH;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class IncidentFilterTransformer extends IndexFilterTransformer<IncidentFilter> {

  public IncidentFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final IncidentFilter filter) {
    return and(
        longOperations(KEY, filter.incidentKeyOperations()),
        longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()),
        stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()),
        longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()),
        stringOperations(ERROR_TYPE, filter.errorTypeOperations()),
        stringOperations(ERROR_MSG, filter.errorMessageOperations()),
        stringOperations(FLOW_NODE_ID, filter.flowNodeIdOperations()),
        longOperations(FLOW_NODE_INSTANCE_KEY, filter.flowNodeInstanceKeyOperations()),
        dateTimeOperations(CREATION_TIME, filter.creationTimeOperations()),
        stringOperations(STATE, filter.stateOperations()),
        stringOperations(TREE_PATH, filter.treePathOperations()),
        longOperations(JOB_KEY, filter.jobKeyOperations()),
        stringOperations(TENANT_ID, filter.tenantIdOperations()),
        intOperations(ERROR_MSG_HASH, filter.errorMessageHashOperations()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
