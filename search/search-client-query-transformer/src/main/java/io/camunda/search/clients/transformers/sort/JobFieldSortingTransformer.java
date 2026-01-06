/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.JobTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.CREATION_TIME;
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
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.LAST_UPDATE_TIME;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.LISTENER_EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.RETRIES;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.TIME;

public class JobFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "elementInstanceKey" -> FLOW_NODE_INSTANCE_ID;
      case "elementId" -> FLOW_NODE_ID;
      case "jobKey" -> JOB_KEY;
      case "type" -> JOB_TYPE;
      case "worker" -> JOB_WORKER;
      case "state" -> JOB_STATE;
      case "kind" -> JOB_KIND;
      case "listenerEventType" -> LISTENER_EVENT_TYPE;
      case "endTime" -> TIME;
      case "tenantId" -> TENANT_ID;
      case "retries" -> RETRIES;
      case "isDenied" -> JOB_DENIED;
      case "deniedReason" -> JOB_DENIED_REASON;
      case "hasFailedWithRetriesLeft" -> JOB_FAILED_WITH_RETRIES_LEFT;
      case "errorCode" -> ERROR_CODE;
      case "errorMessage" -> ERROR_MESSAGE;
      case "deadline" -> JOB_DEADLINE;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "creationTime" -> CREATION_TIME;
      case "lastUpdateTime" -> LAST_UPDATE_TIME;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
