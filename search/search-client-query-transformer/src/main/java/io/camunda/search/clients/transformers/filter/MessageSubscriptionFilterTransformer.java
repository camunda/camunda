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
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.DATE_TIME;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.EVENT_SOURCE_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.EXTENSION_PROPERTIES;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_SUBSCRIPTION_STATE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_SUBSCRIPTION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_DEFINITION_NAME;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_KEY;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.util.ExtensionPropertyKeyUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageSubscriptionFilterTransformer
    extends IndexFilterTransformer<MessageSubscriptionFilter> {

  public MessageSubscriptionFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final MessageSubscriptionFilter filter) {
    final var eventSourceTypeFilter =
        List.of(
            stringTerms(
                EVENT_SOURCE_TYPE,
                List.of("PROCESS_MESSAGE_SUBSCRIPTION", "MESSAGE_START_EVENT_SUBSCRIPTION")));

    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(eventSourceTypeFilter);
    queries.addAll(longOperations(KEY, filter.messageSubscriptionKeyOperations()));
    queries.addAll(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()));
    queries.addAll(longOperations(PROCESS_KEY, filter.processDefinitionKeyOperations()));
    queries.addAll(
        stringOperations(PROCESS_DEFINITION_NAME, filter.processDefinitionNameOperations()));
    queries.addAll(
        intOperations(PROCESS_DEFINITION_VERSION, filter.processDefinitionVersionOperations()));
    queries.addAll(longOperations("processInstanceKey", filter.processInstanceKeyOperations()));
    queries.addAll(stringOperations(FLOW_NODE_ID, filter.flowNodeIdOperations()));
    queries.addAll(longOperations(FLOW_NODE_INSTANCE_KEY, filter.flowNodeInstanceKeyOperations()));
    queries.addAll(
        stringOperations(MESSAGE_SUBSCRIPTION_STATE, filter.messageSubscriptionStateOperations()));
    queries.addAll(
        stringOperations(MESSAGE_SUBSCRIPTION_TYPE, filter.messageSubscriptionTypeOperations()));
    queries.addAll(dateTimeOperations(DATE_TIME, filter.dateTimeOperations()));
    queries.addAll(stringOperations("metadata.messageName", filter.messageNameOperations()));
    queries.addAll(stringOperations("metadata.correlationKey", filter.correlationKeyOperations()));
    queries.addAll(stringOperations(TENANT_ID, filter.tenantIdOperations()));

    // extensionProperties filtering: each key supports advanced string operators
    // on extensionProperties.<key>
    if (filter.extensionProperties() != null) {
      for (final Map.Entry<String, List<Operation<String>>> entry :
          filter.extensionProperties().entrySet()) {
        queries.addAll(
            stringOperations(
                EXTENSION_PROPERTIES + "." + ExtensionPropertyKeyUtil.encode(entry.getKey()),
                entry.getValue()));
      }
    }

    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
