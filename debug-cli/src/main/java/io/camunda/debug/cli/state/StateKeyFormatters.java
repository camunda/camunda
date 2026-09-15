/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance
 * with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.protocol.ZbColumnFamilies;

final class StateKeyFormatters {

  private StateKeyFormatters() {}

  static StateKeyFormatter forColumnFamily(
      final ZbColumnFamilies columnFamily, final String keyFormat) {
    if (keyFormat == null || keyFormat.isBlank() || "default".equalsIgnoreCase(keyFormat)) {
      return formatterFor(columnFamily);
    }
    if ("hex".equalsIgnoreCase(keyFormat)) {
      return StateKeyFormatter.hexadecimal();
    }
    return StateKeyFormatter.databaseValues(keyFormat);
  }

  private static StateKeyFormatter formatterFor(final ZbColumnFamilies columnFamily) {
    return switch (columnFamily) {
      case DEFAULT, KEY, EXPORTER -> StateKeyFormatter.databaseValues("s");
      case ELEMENT_INSTANCE_PARENT_CHILD,
          TIMERS,
          JOB_DEADLINES,
          EVENT_TRIGGER,
          JOB_BACKOFF,
          PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY ->
          StateKeyFormatter.databaseValues("ll");
      case NUMBER_OF_TAKEN_SEQUENCE_FLOWS -> StateKeyFormatter.databaseValues("lss");
      case ELEMENT_INSTANCE_KEY,
          ELEMENT_INSTANCE_CHILD_PARENT,
          DEPLOYMENT_RAW,
          JOBS,
          JOB_STATES,
          MESSAGE_KEY,
          INCIDENTS,
          INCIDENT_PROCESS_INSTANCES,
          INCIDENT_JOBS,
          EVENT_SCOPE,
          BANNED_INSTANCE,
          MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,
          AWAIT_WORKLOW_RESULT,
          COMMAND_DISTRIBUTION_RECORD,
          USER_TASKS,
          USER_TASK_STATES,
          AUTHORIZATIONS ->
          StateKeyFormatter.databaseValues("l");
      case VARIABLES, MESSAGE_CORRELATED, MESSAGE_SUBSCRIPTION_BY_KEY ->
          StateKeyFormatter.databaseValues("ls");
      case MESSAGE_DEADLINES -> StateKeyFormatter.databaseValues("ll");
      case TIMER_DUE_DATES -> StateKeyFormatter.databaseValues("lll");
      case PENDING_DEPLOYMENT, PENDING_DISTRIBUTION -> StateKeyFormatter.databaseValues("li");
      case MESSAGE_IDS -> StateKeyFormatter.databaseValues("ssss");
      case MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
          PROCESS_VERSION,
          PROCESS_CACHE_DIGEST_BY_ID,
          FORM_VERSION,
          MAPPING_RULES ->
          StateKeyFormatter.databaseValues("ss");
      case PROCESS_CACHE, FORMS, DMN_DECISIONS ->
          StateKeyFormatter.databaseValues("sl");
      case DMN_DECISION_REQUIREMENTS -> StateKeyFormatter.databaseValues("sl");
      case DMN_LATEST_DECISION_BY_ID,
          DMN_LATEST_DECISION_REQUIREMENTS_BY_ID ->
          StateKeyFormatter.databaseValues("ss");
      case DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY ->
          StateKeyFormatter.databaseValues("slsl");
      case DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
          DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION ->
          StateKeyFormatter.databaseValues("ssi");
      case PROCESS_CACHE_BY_ID_AND_VERSION, FORM_BY_ID_AND_VERSION ->
          StateKeyFormatter.databaseValues("ssl");
      case MESSAGES -> StateKeyFormatter.databaseValues("sssl");
      case MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
          SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
          JOB_ACTIVATABLE ->
          StateKeyFormatter.databaseValues("ssl");
      case MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
          SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME ->
          StateKeyFormatter.databaseValues("lss");
      case MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY ->
          StateKeyFormatter.databaseValues("sssl");
      case PROCESS_SUBSCRIPTION_BY_KEY -> StateKeyFormatter.databaseValues("lss");
      case USAGE_METRICS -> StateKeyFormatter.databaseValues("b");
      case MIGRATIONS_STATE, MESSAGE_STATS -> StateKeyFormatter.databaseValues("s");
      case COMPENSATION_SUBSCRIPTION -> StateKeyFormatter.databaseValues("sll");
      case ENTITIES_BY_RELATION, RELATIONS_BY_ENTITY ->
          StateKeyFormatter.databaseValues("bsbs");
      case ROLES, CLAIM_BY_ID -> StateKeyFormatter.databaseValues("s");
      case PERMISSIONS -> StateKeyFormatter.databaseValues("sss");
      case AUTHORIZATION_KEYS_BY_OWNER -> StateKeyFormatter.databaseValues("ss");
      case DEPRECATED_PROCESS_VERSION,
          DEPRECATED_PROCESS_CACHE,
          DEPRECATED_PROCESS_CACHE_BY_ID_AND_VERSION,
          DEPRECATED_PROCESS_CACHE_DIGEST_BY_ID,
          TEMPORARY_VARIABLE_STORE,
          DEPRECATED_JOB_ACTIVATABLE,
          DEPRECATED_MESSAGES,
          MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
          DEPRECATED_MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
          DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
          DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
          DEPRECATED_PROCESS_SUBSCRIPTION_BY_KEY,
          PROCESS_SUBSCRIPTION_BY_SENT_TIME,
          DEPRECATED_DMN_DECISIONS,
          DEPRECATED_DMN_DECISION_REQUIREMENTS,
          DEPRECATED_DMN_LATEST_DECISION_BY_ID,
          DEPRECATED_DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
          DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
          DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
          DEPRECATED_DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
          DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
          DEPRECATED_SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
          PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_DEPLOYMENT_KEY,
          DMN_DECISION_KEY_BY_DECISION_ID_AND_DEPLOYMENT_KEY,
          FORM_KEY_BY_FORM_ID_AND_DEPLOYMENT_KEY,
          MESSAGE_CORRELATION,
          USERS,
          USER_KEY_BY_USERNAME,
          CLOCK,
          PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_VERSION_TAG,
          DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION_TAG,
          FORM_KEY_BY_FORM_ID_AND_VERSION_TAG,
          AUTHORIZATION_KEY_BY_RESOURCE_ID,
          OWNER_TYPE_BY_OWNER_KEY,
          ROUTING,
          QUEUED_DISTRIBUTION,
          RETRIABLE_DISTRIBUTION,
          DISTRIBUTION_CONTINUATION,
          RESOURCES,
          RESOURCE_VERSION,
          RESOURCE_BY_ID_AND_VERSION,
          RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG,
          RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY,
          TENANTS,
          USER_TASK_INTERMEDIATE_STATES,
          ASYNC_REQUEST_METADATA,
          GROUPS,
          REDISTRIBUTION,
          USERNAME_BY_USER_KEY,
          BATCH_OPERATION,
          PENDING_BATCH_OPERATION,
          BATCH_OPERATION_CHUNKS,
          VARIABLE_DOCUMENT_STATE_BY_SCOPE_KEY,
          USER_TASK_INITIAL_ASSIGNEE,
          SCALING_STARTED_AT,
          RUNTIME_INSTRUCTIONS,
          MULTI_INSTANCE_INPUT_COLLECTION,
          CLUSTER_VARIABLES,
          CONDITIONAL_SUBSCRIPTION_BY_SUBSCRIPTION_KEY,
          CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY,
          CONDITIONAL_SUBSCRIPTION_BY_PROCESS_DEFINITION_KEY,
          GLOBAL_LISTENERS,
          GLOBAL_LISTENER_CURRENT_CONFIG,
          GLOBAL_LISTENER_VERSIONED_CONFIG,
          GLOBAL_LISTENER_PINNED_CONFIG,
          CONDITIONAL_SUBSCRIPTION_PROCESS_INSTANCE_COUNT,
          JOB_METRICS,
          JOB_METRICS_STRING_ENCODING,
          JOB_METRICS_META,
          CONDITIONAL_SUBSCRIPTION_BY_TENANT_ID,
          PROCESS_INSTANCE_KEY_BY_BUSINESS_ID,
          CHECKPOINTS,
          BACKUP_RANGES,
          ACTIVE_PROCESS_INSTANCE_COUNT,
          AGENT_INSTANCES,
          COMMAND_DISTRIBUTION_METADATA,
          CROSS_PARTITION_MESSAGE_START_DEDUP,
          CROSS_PARTITION_MESSAGE_START_ASK,
          CROSS_PARTITION_MESSAGE_START_LOCK,
          JOB_ACTIVATABLE_BY_PRIORITY,
          MESSAGE_BY_BUSINESS_ID,
          AGENT_HISTORY,
          AGENT_HISTORY_BY_JOB_KEY,
          AGENT_INSTANCES_BY_PROCESS_INSTANCE_KEY,
          PENDING_SECRET_REFERENCES,
          SECRET_REFERENCES_BY_JOB,
          JOBS_BY_SECRET_REFERENCE,
          SUSPENDED_PROCESS_INSTANCES,
          BUFFERED_PROCESS_INSTANCE_COMMANDS,
          BUFFERED_PROCESS_INSTANCE_COMMANDS_BY_PROCESS_INSTANCE_KEY,
          PENDING_PROCESS_DELETIONS_PER_PARTITION,
          CROSS_PARTITION_MESSAGE_START_HOLDER_ORIGIN,
          AGENT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID,
          AGENT_DEFINITION_BY_KEY,
          JOBS_BY_PROCESS_INSTANCE,
          AGENT_HISTORY_COMMITTED_IDS,
          AGENT_HISTORY_METRICS_ACCUMULATED_IDS ->
          StateKeyFormatter.hexadecimal();
    };
  }
}
