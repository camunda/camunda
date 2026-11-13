/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static io.camunda.zeebe.db.rdbms.KeyLayout.bytes;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbLong;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbString;
import static io.camunda.zeebe.db.rdbms.KeyLayout.tenantId;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * The table of each column family, found by the column family's number, as RocksDB and the cache
 * find its keys. Every live column family of {@link ZbColumnFamilies} has a table of its own.
 * Deprecated column families and numbers that aren't listed here share {@link #OTHER}, which keeps
 * the column family in its primary key.
 *
 * <p>The names are written down rather than taken from the column families, so that renaming a
 * column family never moves its rows. After the {@code ZS_} prefix, a name has at most {@value
 * #MAX_SHORT_NAME_LENGTH} characters, so that the names of the table, its primary key and its view
 * fit into 30 characters before the configured prefix.
 *
 * <p>A column family can also describe the parts of its key, which gives its table a view that
 * shows them. The engine never reads the views.
 */
final class StateTables {

  static final int MAX_SHORT_NAME_LENGTH = 24;

  /** The table that names the state of each partition. */
  static final String PARTITION = "ZS_PARTITION";

  /** The table of the column families that have no table of their own. */
  static final StateTable OTHER = new StateTable("ZS_OTHER", true, null);

  private static final StateTable[] BY_COLUMN_FAMILY = byColumnFamily();
  private static final List<StateTable> ALL = all(BY_COLUMN_FAMILY);

  private StateTables() {}

  /** Returns the table that holds the rows of the column family with this number. */
  static StateTable of(final int columnFamily) {
    if (columnFamily < 0 || columnFamily >= BY_COLUMN_FAMILY.length) {
      return OTHER;
    }
    final var table = BY_COLUMN_FAMILY[columnFamily];
    return table != null ? table : OTHER;
  }

  /** Returns every table that holds rows: {@link #OTHER} first, then by column family number. */
  static List<StateTable> all() {
    return ALL;
  }

  private static StateTable[] byColumnFamily() {
    final var tables = new TreeMap<Integer, StateTable>();
    add(tables, ZbColumnFamilies.DEFAULT, "DEFAULT");
    add(tables, ZbColumnFamilies.KEY, "KEY");
    add(
        tables,
        ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD,
        "ELEMENT_PARENT_CHILD",
        dbLong("PARENT_KEY"),
        dbLong("ELEMENT_INSTANCE_KEY"));
    add(
        tables,
        ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
        "ELEMENT_INSTANCE_KEY",
        dbLong("ELEMENT_INSTANCE_KEY"));
    add(tables, ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS, "TAKEN_SEQUENCE_FLOWS");
    add(
        tables,
        ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT,
        "ELEMENT_CHILD_PARENT",
        dbLong("CHILD_KEY"));
    add(tables, ZbColumnFamilies.VARIABLES, "VARIABLES", dbLong("SCOPE_KEY"), dbString("NAME"));
    add(
        tables,
        ZbColumnFamilies.TIMERS,
        "TIMERS",
        dbLong("ELEMENT_INSTANCE_KEY"),
        dbLong("TIMER_KEY"));
    add(
        tables,
        ZbColumnFamilies.TIMER_DUE_DATES,
        "TIMER_DUE_DATES",
        dbLong("DUE_DATE"),
        dbLong("ELEMENT_INSTANCE_KEY"),
        dbLong("TIMER_KEY"));
    add(tables, ZbColumnFamilies.PENDING_DEPLOYMENT, "PENDING_DEPLOYMENT");
    add(tables, ZbColumnFamilies.DEPLOYMENT_RAW, "DEPLOYMENT_RAW");
    add(tables, ZbColumnFamilies.JOBS, "JOBS", dbLong("JOB_KEY"));
    add(tables, ZbColumnFamilies.JOB_STATES, "JOB_STATES", dbLong("JOB_KEY"));
    add(
        tables,
        ZbColumnFamilies.JOB_DEADLINES,
        "JOB_DEADLINES",
        dbLong("DEADLINE"),
        dbLong("JOB_KEY"));
    add(tables, ZbColumnFamilies.MESSAGE_KEY, "MESSAGE_KEY", dbLong("MESSAGE_KEY"));
    add(
        tables,
        ZbColumnFamilies.MESSAGE_DEADLINES,
        "MESSAGE_DEADLINES",
        dbLong("DEADLINE"),
        dbLong("MESSAGE_KEY"));
    add(tables, ZbColumnFamilies.MESSAGE_IDS, "MESSAGE_IDS");
    add(tables, ZbColumnFamilies.MESSAGE_CORRELATED, "MESSAGE_CORRELATED");
    add(
        tables,
        ZbColumnFamilies.MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
        "MSG_ACTIVE_BY_CORR_KEY");
    add(tables, ZbColumnFamilies.MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS, "MSG_PI_CORR_KEYS");
    add(
        tables,
        ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
        "MSG_SUB_BY_KEY",
        dbLong("ELEMENT_INSTANCE_KEY"),
        dbString("MESSAGE_NAME"));
    add(tables, ZbColumnFamilies.INCIDENTS, "INCIDENTS", dbLong("INCIDENT_KEY"));
    add(
        tables,
        ZbColumnFamilies.INCIDENT_PROCESS_INSTANCES,
        "INCIDENT_PROC_INSTANCES",
        dbLong("ELEMENT_INSTANCE_KEY"));
    add(tables, ZbColumnFamilies.INCIDENT_JOBS, "INCIDENT_JOBS", dbLong("JOB_KEY"));
    add(tables, ZbColumnFamilies.EVENT_SCOPE, "EVENT_SCOPE", dbLong("EVENT_SCOPE_KEY"));
    add(
        tables,
        ZbColumnFamilies.EVENT_TRIGGER,
        "EVENT_TRIGGER",
        dbLong("EVENT_SCOPE_KEY"),
        dbLong("EVENT_KEY"));
    add(tables, ZbColumnFamilies.BANNED_INSTANCE, "BANNED_INSTANCE");
    add(tables, ZbColumnFamilies.EXPORTER, "EXPORTER");
    add(tables, ZbColumnFamilies.AWAIT_WORKLOW_RESULT, "AWAIT_WORKLOW_RESULT");
    add(tables, ZbColumnFamilies.JOB_BACKOFF, "JOB_BACKOFF", dbLong("BACKOFF"), dbLong("JOB_KEY"));
    add(tables, ZbColumnFamilies.PENDING_DISTRIBUTION, "PENDING_DISTRIBUTION");
    add(tables, ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD, "CMD_DIST_RECORD");
    add(tables, ZbColumnFamilies.MESSAGE_STATS, "MESSAGE_STATS");
    add(tables, ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY, "PI_KEY_BY_DEF_KEY");
    add(tables, ZbColumnFamilies.MIGRATIONS_STATE, "MIGRATIONS_STATE");
    add(tables, ZbColumnFamilies.PROCESS_VERSION, "PROCESS_VERSION");
    add(tables, ZbColumnFamilies.PROCESS_CACHE, "PROCESS_CACHE");
    add(tables, ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION, "PROC_CACHE_BY_ID_VER");
    add(tables, ZbColumnFamilies.PROCESS_CACHE_DIGEST_BY_ID, "PROC_CACHE_DIGEST_BY_ID");
    add(tables, ZbColumnFamilies.DMN_DECISIONS, "DMN_DECISIONS");
    add(tables, ZbColumnFamilies.DMN_DECISION_REQUIREMENTS, "DMN_DRD");
    add(tables, ZbColumnFamilies.DMN_LATEST_DECISION_BY_ID, "DMN_LATEST_DEC_BY_ID");
    add(tables, ZbColumnFamilies.DMN_LATEST_DECISION_REQUIREMENTS_BY_ID, "DMN_LATEST_DRD_BY_ID");
    add(
        tables,
        ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
        "DMN_DEC_KEY_BY_DRD_KEY");
    add(
        tables,
        ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
        "DMN_DEC_KEY_BY_ID_VER");
    add(
        tables,
        ZbColumnFamilies.DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
        "DMN_DRD_KEY_BY_ID_VER");
    add(tables, ZbColumnFamilies.FORMS, "FORMS");
    add(tables, ZbColumnFamilies.FORM_VERSION, "FORM_VERSION");
    add(tables, ZbColumnFamilies.FORM_BY_ID_AND_VERSION, "FORM_BY_ID_AND_VERSION");
    add(
        tables,
        ZbColumnFamilies.MESSAGES,
        "MESSAGES",
        tenantId(),
        dbString("MESSAGE_NAME"),
        dbString("CORRELATION_KEY"),
        dbLong("MESSAGE_KEY"));
    add(
        tables,
        ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
        "MSG_START_BY_NAME_KEY");
    add(
        tables,
        ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
        "MSG_START_BY_KEY_NAME");
    add(
        tables,
        ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
        "MSG_SUB_BY_NAME_CORR_KEY",
        tenantId(),
        dbString("MESSAGE_NAME"),
        dbString("CORRELATION_KEY"),
        dbLong("ELEMENT_INSTANCE_KEY"));
    add(
        tables,
        ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY,
        "PROC_SUB_BY_KEY",
        dbLong("ELEMENT_INSTANCE_KEY"),
        tenantId(),
        dbString("MESSAGE_NAME"));
    add(tables, ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY, "SIGNAL_SUB_BY_NAME_KEY");
    add(tables, ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME, "SIGNAL_SUB_BY_KEY_NAME");
    add(tables, ZbColumnFamilies.USER_TASKS, "USER_TASKS", dbLong("USER_TASK_KEY"));
    add(tables, ZbColumnFamilies.USER_TASK_STATES, "USER_TASK_STATES", dbLong("USER_TASK_KEY"));
    add(tables, ZbColumnFamilies.COMPENSATION_SUBSCRIPTION, "COMPENSATION_SUB");
    add(
        tables,
        ZbColumnFamilies.PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_DEPLOYMENT_KEY,
        "PROC_KEY_BY_ID_DEPL");
    add(
        tables,
        ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_DEPLOYMENT_KEY,
        "DMN_DEC_KEY_BY_ID_DEPL");
    add(tables, ZbColumnFamilies.FORM_KEY_BY_FORM_ID_AND_DEPLOYMENT_KEY, "FORM_KEY_BY_ID_DEPL");
    add(tables, ZbColumnFamilies.MESSAGE_CORRELATION, "MESSAGE_CORRELATION");
    add(tables, ZbColumnFamilies.USERS, "USERS");
    add(tables, ZbColumnFamilies.CLOCK, "CLOCK");
    add(tables, ZbColumnFamilies.AUTHORIZATIONS, "AUTHORIZATIONS");
    add(tables, ZbColumnFamilies.PERMISSIONS, "PERMISSIONS");
    add(
        tables,
        ZbColumnFamilies.PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_VERSION_TAG,
        "PROC_KEY_BY_ID_VTAG");
    add(
        tables,
        ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION_TAG,
        "DMN_DEC_KEY_BY_ID_VTAG");
    add(tables, ZbColumnFamilies.FORM_KEY_BY_FORM_ID_AND_VERSION_TAG, "FORM_KEY_BY_ID_VTAG");
    add(tables, ZbColumnFamilies.ROUTING, "ROUTING");
    add(tables, ZbColumnFamilies.QUEUED_DISTRIBUTION, "QUEUED_DISTRIBUTION");
    add(tables, ZbColumnFamilies.RETRIABLE_DISTRIBUTION, "RETRIABLE_DISTRIBUTION");
    add(tables, ZbColumnFamilies.DISTRIBUTION_CONTINUATION, "DISTRIB_CONTINUATION");
    add(tables, ZbColumnFamilies.RESOURCES, "RESOURCES");
    add(tables, ZbColumnFamilies.RESOURCE_VERSION, "RESOURCE_VERSION");
    add(tables, ZbColumnFamilies.RESOURCE_BY_ID_AND_VERSION, "RESOURCE_BY_ID_VER");
    add(
        tables,
        ZbColumnFamilies.RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG,
        "RESOURCE_KEY_BY_ID_VTAG");
    add(
        tables,
        ZbColumnFamilies.RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY,
        "RESOURCE_KEY_BY_ID_DEPL");
    add(tables, ZbColumnFamilies.ROLES, "ROLES");
    add(tables, ZbColumnFamilies.TENANTS, "TENANTS");
    add(tables, ZbColumnFamilies.USER_TASK_INTERMEDIATE_STATES, "USER_TASK_INTERM_STATES");
    add(tables, ZbColumnFamilies.MAPPING_RULES, "MAPPING_RULES");
    add(tables, ZbColumnFamilies.ASYNC_REQUEST_METADATA, "ASYNC_REQUEST_METADATA");
    add(tables, ZbColumnFamilies.GROUPS, "GROUPS");
    add(tables, ZbColumnFamilies.USERNAME_BY_USER_KEY, "USERNAME_BY_USER_KEY");
    add(tables, ZbColumnFamilies.CLAIM_BY_ID, "CLAIM_BY_ID");
    add(tables, ZbColumnFamilies.AUTHORIZATION_KEYS_BY_OWNER, "AUTH_KEYS_BY_OWNER");
    add(tables, ZbColumnFamilies.BATCH_OPERATION, "BATCH_OPERATION");
    add(tables, ZbColumnFamilies.PENDING_BATCH_OPERATION, "PENDING_BATCH_OPERATION");
    add(tables, ZbColumnFamilies.BATCH_OPERATION_CHUNKS, "BATCH_OPERATION_CHUNKS");
    add(tables, ZbColumnFamilies.VARIABLE_DOCUMENT_STATE_BY_SCOPE_KEY, "VAR_DOC_BY_SCOPE_KEY");
    add(tables, ZbColumnFamilies.RELATIONS_BY_ENTITY, "RELATIONS_BY_ENTITY");
    add(tables, ZbColumnFamilies.ENTITIES_BY_RELATION, "ENTITIES_BY_RELATION");
    add(tables, ZbColumnFamilies.USER_TASK_INITIAL_ASSIGNEE, "USER_TASK_INIT_ASSIGNEE");
    add(tables, ZbColumnFamilies.USAGE_METRICS, "USAGE_METRICS");
    add(tables, ZbColumnFamilies.SCALING_STARTED_AT, "SCALING_STARTED_AT");
    add(tables, ZbColumnFamilies.RUNTIME_INSTRUCTIONS, "RUNTIME_INSTRUCTIONS");
    add(tables, ZbColumnFamilies.MULTI_INSTANCE_INPUT_COLLECTION, "MI_INPUT_COLLECTION");
    add(tables, ZbColumnFamilies.CLUSTER_VARIABLES, "CLUSTER_VARIABLES");
    add(tables, ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SUBSCRIPTION_KEY, "COND_SUB_BY_KEY");
    add(tables, ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY, "COND_SUB_BY_SCOPE_KEY");
    add(
        tables,
        ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_PROCESS_DEFINITION_KEY,
        "COND_SUB_BY_PROC_KEY");
    add(tables, ZbColumnFamilies.GLOBAL_LISTENERS, "GLOBAL_LISTENERS");
    add(tables, ZbColumnFamilies.GLOBAL_LISTENER_CURRENT_CONFIG, "LISTENER_CURRENT_CFG");
    add(tables, ZbColumnFamilies.GLOBAL_LISTENER_VERSIONED_CONFIG, "LISTENER_VERSIONED_CFG");
    add(tables, ZbColumnFamilies.GLOBAL_LISTENER_PINNED_CONFIG, "LISTENER_PINNED_CFG");
    add(
        tables,
        ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_PROCESS_INSTANCE_COUNT,
        "COND_SUB_PI_COUNT");
    add(tables, ZbColumnFamilies.JOB_METRICS, "JOB_METRICS");
    add(tables, ZbColumnFamilies.JOB_METRICS_STRING_ENCODING, "JOB_METRICS_STRINGS");
    add(tables, ZbColumnFamilies.JOB_METRICS_META, "JOB_METRICS_META");
    add(tables, ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_TENANT_ID, "COND_SUB_BY_TENANT_ID");
    add(tables, ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_BUSINESS_ID, "PI_KEY_BY_BUSINESS_ID");
    add(tables, ZbColumnFamilies.CHECKPOINTS, "CHECKPOINTS");
    add(tables, ZbColumnFamilies.BACKUP_RANGES, "BACKUP_RANGES");
    add(tables, ZbColumnFamilies.ACTIVE_PROCESS_INSTANCE_COUNT, "ACTIVE_PI_COUNT");
    add(tables, ZbColumnFamilies.AGENT_INSTANCES, "AGENT_INSTANCES");
    add(tables, ZbColumnFamilies.COMMAND_DISTRIBUTION_METADATA, "CMD_DIST_METADATA");
    add(tables, ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_DEDUP, "XP_MSG_START_DEDUP");
    add(tables, ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_ASK, "XP_MSG_START_ASK");
    add(tables, ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_LOCK, "XP_MSG_START_LOCK");
    add(
        tables,
        ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY,
        "JOB_ACTIVATABLE_BY_PRIO",
        dbString("JOB_TYPE"),
        bytes("INVERTED_PRIORITY", Integer.BYTES),
        dbLong("JOB_KEY"),
        tenantId());
    add(tables, ZbColumnFamilies.MESSAGE_BY_BUSINESS_ID, "MESSAGE_BY_BUSINESS_ID");
    add(tables, ZbColumnFamilies.AGENT_HISTORY, "AGENT_HISTORY");
    add(tables, ZbColumnFamilies.AGENT_HISTORY_BY_JOB_KEY, "AGENT_HISTORY_BY_JOB_KEY");
    add(tables, ZbColumnFamilies.AGENT_INSTANCES_BY_PROCESS_INSTANCE_KEY, "AGENT_INSTANCES_BY_PI");
    add(tables, ZbColumnFamilies.PENDING_SECRET_REFERENCES, "PENDING_SECRET_REFS");
    add(tables, ZbColumnFamilies.SECRET_REFERENCES_BY_JOB, "SECRET_REFERENCES_BY_JOB");
    add(tables, ZbColumnFamilies.JOBS_BY_SECRET_REFERENCE, "JOBS_BY_SECRET_REFERENCE");
    add(tables, ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES, "SUSPENDED_PROC_INSTANCES");
    add(
        tables,
        ZbColumnFamilies.BUFFERED_COMMANDS_BY_PROCESS_INSTANCE_KEY,
        "BUFFERED_CMDS_BY_PI_KEY");
    add(tables, ZbColumnFamilies.PENDING_PROCESS_DELETIONS_PER_PARTITION, "PENDING_PROC_DELETIONS");
    add(
        tables,
        ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_HOLDER_ORIGIN,
        "XP_MSG_START_HOLDER_ORIG");
    add(
        tables,
        ZbColumnFamilies.AGENT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID,
        "AGENT_DEF_KEY_BY_ELEMENT");
    add(tables, ZbColumnFamilies.AGENT_DEFINITION_BY_KEY, "AGENT_DEFINITION_BY_KEY");
    add(
        tables,
        ZbColumnFamilies.JOBS_BY_PROCESS_INSTANCE,
        "JOBS_BY_PROCESS_INSTANCE",
        dbLong("PROCESS_INSTANCE_KEY"),
        dbLong("JOB_KEY"));
    add(tables, ZbColumnFamilies.AGENT_HISTORY_COMMITTED_IDS, "AGENT_HIST_COMMITTED_IDS");
    add(tables, ZbColumnFamilies.AGENT_HISTORY_METRICS_ACCUMULATED_IDS, "AGENT_HIST_METRICS_IDS");
    add(tables, ZbColumnFamilies.USER_TASKS_SUSPENDED_BY_PROCESS_INSTANCE, "USER_TASKS_SUSP_BY_PI");
    add(tables, ZbColumnFamilies.PROBES, "PROBES");
    add(tables, ZbColumnFamilies.PROBES_ACTIVATABLE, "PROBES_ACTIVATABLE");
    add(tables, ZbColumnFamilies.PROBE_DEADLINES, "PROBE_DEADLINES");

    final var byColumnFamily = new StateTable[tables.lastKey() + 1];
    tables.forEach((columnFamily, table) -> byColumnFamily[columnFamily] = table);
    return byColumnFamily;
  }

  private static void add(
      final TreeMap<Integer, StateTable> tables,
      final ZbColumnFamilies columnFamily,
      final String shortName,
      final KeyLayout.Part... key) {
    final var keyLayout = key.length == 0 ? null : new KeyLayout(List.of(key));
    tables.put(columnFamily.getValue(), new StateTable("ZS_" + shortName, false, keyLayout));
  }

  private static List<StateTable> all(final StateTable[] byColumnFamily) {
    final var all = new ArrayList<StateTable>();
    all.add(OTHER);
    for (final var table : byColumnFamily) {
      if (table != null) {
        all.add(table);
      }
    }
    return Collections.unmodifiableList(all);
  }
}
