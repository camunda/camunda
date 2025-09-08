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
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.CORRELATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.MESSAGE_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.MESSAGE_NAME;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.PARTITION_ID;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.SUBSCRIPTION_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.TENANT_ID;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.CorrelatedMessagesFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class CorrelatedMessagesFilterTransformer
    extends IndexFilterTransformer<CorrelatedMessagesFilter> {

  public CorrelatedMessagesFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final CorrelatedMessagesFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(stringOperations(CORRELATION_KEY, filter.correlationKeyOperations()));
    queries.addAll(dateTimeOperations(CORRELATION_TIME, filter.correlationTimeOperations()));
    queries.addAll(stringOperations(FLOW_NODE_ID, filter.elementIdOperations()));
    queries.addAll(longOperations(FLOW_NODE_INSTANCE_KEY, filter.elementInstanceKeyOperations()));
    queries.addAll(longOperations(MESSAGE_KEY, filter.messageKeyOperations()));
    queries.addAll(stringOperations(MESSAGE_NAME, filter.messageNameOperations()));
    queries.addAll(intOperations(PARTITION_ID, filter.partitionIdOperations()));
    queries.addAll(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()));
    queries.addAll(longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()));
    queries.addAll(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()));
    queries.addAll(longOperations(SUBSCRIPTION_KEY, filter.subscriptionKeyOperations()));
    queries.addAll(stringOperations(TENANT_ID, filter.tenantIdOperations()));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}