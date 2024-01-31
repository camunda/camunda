/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol;

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
  DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION(48),
  DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION(49),

  // signal subscription
  SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY(50),
  SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME(51),

  // distribution
  PENDING_DISTRIBUTION(52),
  COMMAND_DISTRIBUTION_RECORD(53),
  MESSAGE_STATS(54),

  PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY(55),

  MIGRATIONS_STATE(56);

  private final int value;

  ZbColumnFamilies(final int value) {
    this.value = value;
  }

  @Override
  public int getValue() {
    return value;
  }
}
