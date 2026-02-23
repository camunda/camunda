/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.BatchOperationDependant;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.DecisionInstanceDependant;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogJoinRelationshipType;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.EntityJoinRelationFactory;
import java.util.Map;
import java.util.Optional;

public class AuditLogTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant,
        DecisionInstanceDependant,
        BatchOperationDependant,
        Prio5Backup {

  public static final String INDEX_NAME = "audit-log";
  public static final String INDEX_VERSION = "8.9.0";

  public static final String ID = "id";
  public static final String ACTOR_ID = "actorId";
  public static final String ACTOR_TYPE = "actorType";
  public static final String AGENT_ELEMENT_ID = "agentElementId";
  public static final String ANNOTATION = "annotation";
  public static final String AUDIT_LOG_KEY = "auditLogKey";
  public static final String BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String BATCH_OPERATION_TYPE = "batchOperationType";
  public static final String CATEGORY = "category";
  public static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  public static final String DECISION_DEFINITION_KEY = "decisionDefinitionKey";
  public static final String DECISION_EVALUATION_KEY = "decisionEvaluationKey";
  public static final String DECISION_REQUIREMENTS_ID = "decisionRequirementsId";
  public static final String DECISION_REQUIREMENTS_KEY = "decisionRequirementsKey";
  public static final String DEPLOYMENT_KEY = "deploymentKey";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String ENTITY_KEY = "entityKey";
  public static final String ENTITY_TYPE = "entityType";
  public static final String FORM_KEY = "formKey";
  public static final String JOB_KEY = "jobKey";
  public static final String OPERATION_TYPE = "operationType";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String RESOURCE_KEY = "resourceKey";
  public static final String RESULT = "result";
  public static final String TENANT_ID = "tenantId";
  public static final String TENANT_SCOPE = "tenantScope";
  public static final String TIMESTAMP = "timestamp";
  public static final String USER_TASK_KEY = "userTaskKey";
  public static final String ROOT_PROCESS_INSTANCE_KEY = "rootProcessInstanceKey";
  public static final String RELATED_ENTITY_TYPE = "relatedEntityType";
  public static final String RELATED_ENTITY_KEY = "relatedEntityKey";
  public static final String ENTITY_DESCRIPTION = "entityDescription";

  public static final EntityJoinRelationFactory<AuditLogJoinRelationshipType>
      JOIN_RELATION_FACTORY =
          new EntityJoinRelationFactory<>(
              AuditLogJoinRelationshipType.BATCH_OPERATION,
              AuditLogJoinRelationshipType.BATCH_ITEM);

  public AuditLogTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
  }

  @Override
  public String getBatchOperationDependantField() {
    return BATCH_OPERATION_KEY;
  }

  @Override
  public Map<String, String> getBatchOperationDependantFilters() {
    return Map.of(AuditLogTemplate.ENTITY_TYPE, AuditLogEntityType.BATCH.toString());
  }
}
