/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ACTOR_ID;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ACTOR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ANNOTATION;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.BATCH_OPERATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.BATCH_OPERATION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.CATEGORY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.DECISION_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.DECISION_EVALUATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.DECISION_REQUIREMENTS_ID;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.DECISION_REQUIREMENTS_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ENTITY_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ENTITY_TYPE;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.OPERATION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.PROCESS_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.RESULT;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.TIMESTAMP;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.USER_TASK_KEY;

public class AuditLogSortTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "auditLogKey" -> ID;
      case "actorId" -> ACTOR_ID;
      case "actorType" -> ACTOR_TYPE;
      case "annotation" -> ANNOTATION;
      case "batchOperationKey" -> BATCH_OPERATION_KEY;
      case "batchOperationType" -> BATCH_OPERATION_TYPE;
      case "category" -> CATEGORY;
      case "decisionDefinitionId" -> DECISION_DEFINITION_ID;
      case "decisionDefinitionKey" -> DECISION_DEFINITION_KEY;
      case "decisionEvaluationKey" -> DECISION_EVALUATION_KEY;
      case "decisionRequirementsId" -> DECISION_REQUIREMENTS_ID;
      case "decisionRequirementsKey" -> DECISION_REQUIREMENTS_KEY;
      case "elementInstanceKey" -> ELEMENT_INSTANCE_KEY;
      case "entityKey" -> ENTITY_KEY;
      case "entityType" -> ENTITY_TYPE;
      case "jobKey" -> JOB_KEY;
      case "operationType" -> OPERATION_TYPE;
      case "processDefinitionId" -> PROCESS_DEFINITION_ID;
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "result" -> RESULT;
      case "tenantId" -> TENANT_ID;
      case "timestamp" -> TIMESTAMP;
      case "userTaskKey" -> USER_TASK_KEY;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return TIMESTAMP;
  }
}
