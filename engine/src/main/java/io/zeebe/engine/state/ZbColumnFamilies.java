/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

public enum ZbColumnFamilies {
  DEFAULT,

  // util
  KEY,

  // workflow
  WORKFLOW_VERSION,

  // workflow cache
  WORKFLOW_CACHE,
  WORKFLOW_CACHE_BY_ID_AND_VERSION,
  WORKFLOW_CACHE_LATEST_KEY,
  WORKFLOW_CACHE_DIGEST_BY_ID,

  // element instance
  ELEMENT_INSTANCE_PARENT_CHILD,
  ELEMENT_INSTANCE_KEY,
  STORED_INSTANCE_EVENTS,
  STORED_INSTANCE_EVENTS_PARENT_CHILD,

  // variable state
  ELEMENT_INSTANCE_CHILD_PARENT,
  VARIABLES,
  TEMPORARY_VARIABLE_STORE,

  // timer state
  TIMERS,
  TIMER_DUE_DATES,

  // pending deployments
  PENDING_DEPLOYMENT,
  DEPLOYMENT_RAW,

  // jobs
  JOBS,
  JOB_STATES,
  JOB_DEADLINES,
  JOB_ACTIVATABLE,

  // message
  MESSAGE_KEY,
  MESSAGES,
  MESSAGE_DEADLINES,
  MESSAGE_IDS,
  MESSAGE_CORRELATED,
  MESSAGE_WORKFLOWS_ACTIVE_BY_CORRELATION_KEY,
  MESSAGE_WORKFLOW_INSTANCE_CORRELATION_KEYS,

  // message subscription
  MESSAGE_SUBSCRIPTION_BY_KEY,
  MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
  MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,

  // message start event subscription
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,

  // workflow instance subscription
  WORKFLOW_SUBSCRIPTION_BY_KEY,
  WORKFLOW_SUBSCRIPTION_BY_SENT_TIME,

  // incident
  INCIDENTS,
  INCIDENT_WORKFLOW_INSTANCES,
  INCIDENT_JOBS,

  // event
  EVENT_SCOPE,
  EVENT_TRIGGER,

  BLACKLIST,

  EXPORTER,

  AWAIT_WORKLOW_RESULT
}
