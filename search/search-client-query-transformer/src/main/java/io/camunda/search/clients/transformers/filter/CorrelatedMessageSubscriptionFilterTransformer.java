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
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate.*;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class CorrelatedMessageSubscriptionFilterTransformer
    extends IndexFilterTransformer<CorrelatedMessageSubscriptionFilter> {

  public CorrelatedMessageSubscriptionFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final CorrelatedMessageSubscriptionFilter filter) {
    return and(
        stringOperations(CORRELATION_KEY, filter.correlationKeyOperations()),
        dateTimeOperations(CORRELATION_TIME, filter.correlationTimeOperations()),
        stringOperations(FLOW_NODE_ID, filter.flowNodeIdOperations()),
        longOperations(FLOW_NODE_INSTANCE_KEY, filter.flowNodeInstanceKeyOperations()),
        stringOperations(MESSAGE_NAME, filter.messageNameOperations()),
        longOperations(MESSAGE_KEY, filter.messageKeyOperations()),
        intOperations(PARTITION_ID, filter.partitionIdOperations()),
        stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()),
        longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()),
        longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()),
        longOperations(SUBSCRIPTION_KEY, filter.subscriptionKeyOperations()),
        stringOperations(TENANT_ID, filter.tenantIdOperations()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
