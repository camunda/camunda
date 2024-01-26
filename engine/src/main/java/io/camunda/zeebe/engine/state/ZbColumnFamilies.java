/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.protocol.EnumValue;

public enum ZbColumnFamilies implements EnumValue {
  DEFAULT(0),

  // util
  KEY(1),

  // process
  PROCESS_VERSION(2),

  // process cache
  PROCESS_CACHE(3),
  PROCESS_CACHE_BY_ID_AND_VERSION(4),
  PROCESS_CACHE_DIGEST_BY_ID(5),

  // element instance
  ELEMENT_INSTANCE_PARENT_CHILD(6),
  ELEMENT_INSTANCE_KEY(7),

  NUMBER_OF_TAKEN_SEQUENCE_FLOWS(8),

  // variable state
  ELEMENT_INSTANCE_CHILD_PARENT(9),
  VARIABLES(10),
  @Deprecated
  TEMPORARY_VARIABLE_STORE(11),

  // timer state
  TIMERS(12),
  TIMER_DUE_DATES(13),

  // pending deployments
  PENDING_DEPLOYMENT(14),
  DEPLOYMENT_RAW(15),

  // jobs
  JOBS(16),
  JOB_STATES(17),
  JOB_DEADLINES(18),
  JOB_ACTIVATABLE(19),

  // message
  MESSAGE_KEY(20),
  MESSAGES(21),
  MESSAGE_DEADLINES(22),
  MESSAGE_IDS(23),
  MESSAGE_CORRELATED(24),
  MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY(25),
  MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS(26),

  // message subscription
  MESSAGE_SUBSCRIPTION_BY_KEY(27),
  @Deprecated // only used for migration logic
  MESSAGE_SUBSCRIPTION_BY_SENT_TIME(28),
  // migration end
  MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY(29),

  // message start event subscription
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY(30),
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME(31),

  // process message subscription
  PROCESS_SUBSCRIPTION_BY_KEY(32),
  // migration start
  @Deprecated // only used for migration logic
  PROCESS_SUBSCRIPTION_BY_SENT_TIME(33),
  // migration end

  // incident
  INCIDENTS(34),
  INCIDENT_PROCESS_INSTANCES(35),
  INCIDENT_JOBS(36),

  // event
  EVENT_SCOPE(37),
  EVENT_TRIGGER(38),

  BANNED_INSTANCE(39),

  EXPORTER(40),

  AWAIT_WORKLOW_RESULT(41),

  JOB_BACKOFF(42),

  DMN_DECISIONS(43),
  DMN_DECISION_REQUIREMENTS(44),
  DMN_LATEST_DECISION_BY_ID(45),
  DMN_LATEST_DECISION_REQUIREMENTS_BY_ID(46),
  DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY(47),

  // has value 54 on other versions, it received a wrong ordinal on 8.1 by accident
  MESSAGE_STATS(48),

  // has value 55 on other versions, it received a wrong ordinal on 8.1 by accident
  PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY(49),

  // has value 56 on other versions, it received a wrong ordinal on 8.1 by accident
  MIGRATIONS_STATE(50);

  private final int value;

  ZbColumnFamilies(final int value) {
    this.value = value;
  }

  @Override
  public int getValue() {
    return value;
  }
}
