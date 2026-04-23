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
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.INBOUND_CONNECTOR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_SUBSCRIPTION_STATE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_SUBSCRIPTION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_DEFINITION_NAME;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.TOOL_NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class MessageSubscriptionFilterTransformer
    extends IndexFilterTransformer<MessageSubscriptionFilter> {

  public MessageSubscriptionFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final MessageSubscriptionFilter filter) {
    final var messageSubscriptionTerm =
        stringTerms(
            EVENT_SOURCE_TYPE,
            List.of("PROCESS_MESSAGE_SUBSCRIPTION", "MESSAGE_START_EVENT_SUBSCRIPTION"));
    return and(
        List.of(messageSubscriptionTerm),
        longOperations(KEY, filter.messageSubscriptionKeyOperations()),
        stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()),
        longOperations(PROCESS_KEY, filter.processDefinitionKeyOperations()),
        longOperations("processInstanceKey", filter.processInstanceKeyOperations()),
        stringOperations(FLOW_NODE_ID, filter.flowNodeIdOperations()),
        longOperations(FLOW_NODE_INSTANCE_KEY, filter.flowNodeInstanceKeyOperations()),
        stringOperations(MESSAGE_SUBSCRIPTION_STATE, filter.messageSubscriptionStateOperations()),
        stringOperations(MESSAGE_SUBSCRIPTION_TYPE, filter.messageSubscriptionTypeOperations()),
        dateTimeOperations(DATE_TIME, filter.dateTimeOperations()),
        stringOperations("metadata.messageName", filter.messageNameOperations()),
        stringOperations("metadata.correlationKey", filter.correlationKeyOperations()),
        stringOperations(TENANT_ID, filter.tenantIdOperations()),
        stringOperations(PROCESS_DEFINITION_NAME, filter.processDefinitionNameOperations()),
        intOperations(PROCESS_DEFINITION_VERSION, filter.processDefinitionVersionOperations()),
        stringOperations(TOOL_NAME, filter.toolNameOperations()),
        stringOperations(INBOUND_CONNECTOR_TYPE, filter.inboundConnectorTypeOperations()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
