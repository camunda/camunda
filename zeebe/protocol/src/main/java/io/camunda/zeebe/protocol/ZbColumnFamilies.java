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

public enum ZbColumnFamilies implements EnumValue, ScopedColumnFamily {
  DEFAULT(0),

  // util
  KEY(1),

  // process
  @Deprecated
  DEPRECATED_PROCESS_VERSION(2, ColumnFamilyScope.GLOBAL),

  // process cache
  @Deprecated
  DEPRECATED_PROCESS_CACHE(3, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_PROCESS_CACHE_BY_ID_AND_VERSION(4, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_PROCESS_CACHE_DIGEST_BY_ID(5, ColumnFamilyScope.GLOBAL),

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
  @Deprecated
  DEPRECATED_JOB_ACTIVATABLE(19),

  // message
  MESSAGE_KEY(20),
  @Deprecated
  DEPRECATED_MESSAGES(21),
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
  @Deprecated
  DEPRECATED_MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY(29),

  // message start event subscription
  @Deprecated
  DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY(30),
  @Deprecated
  DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME(31),

  // process message subscription
  @Deprecated
  DEPRECATED_PROCESS_SUBSCRIPTION_BY_KEY(32),
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

  @Deprecated
  DEPRECATED_DMN_DECISIONS(43, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_DMN_DECISION_REQUIREMENTS(44, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_DMN_LATEST_DECISION_BY_ID(45, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_DMN_LATEST_DECISION_REQUIREMENTS_BY_ID(46, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY(47, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION(48, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION(
      49, ColumnFamilyScope.GLOBAL),

  // signal subscription
  @Deprecated
  DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY(50, ColumnFamilyScope.GLOBAL),
  @Deprecated
  DEPRECATED_SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME(51, ColumnFamilyScope.GLOBAL),

  // distribution
  PENDING_DISTRIBUTION(52),
  COMMAND_DISTRIBUTION_RECORD(53),
  MESSAGE_STATS(54),

  PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY(55),

  MIGRATIONS_STATE(56, ColumnFamilyScope.GLOBAL),

  PROCESS_VERSION(57, ColumnFamilyScope.GLOBAL),
  PROCESS_CACHE(58, ColumnFamilyScope.GLOBAL),
  PROCESS_CACHE_BY_ID_AND_VERSION(59, ColumnFamilyScope.GLOBAL),
  PROCESS_CACHE_DIGEST_BY_ID(60, ColumnFamilyScope.GLOBAL),

  DMN_DECISIONS(61, ColumnFamilyScope.GLOBAL),
  DMN_DECISION_REQUIREMENTS(62, ColumnFamilyScope.GLOBAL),
  DMN_LATEST_DECISION_BY_ID(63, ColumnFamilyScope.GLOBAL),
  DMN_LATEST_DECISION_REQUIREMENTS_BY_ID(64, ColumnFamilyScope.GLOBAL),
  DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY(65, ColumnFamilyScope.GLOBAL),
  DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION(66, ColumnFamilyScope.GLOBAL),
  DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION(
      67, ColumnFamilyScope.GLOBAL),

  FORMS(68, ColumnFamilyScope.GLOBAL),
  FORM_VERSION(69, ColumnFamilyScope.GLOBAL),
  FORM_BY_ID_AND_VERSION(70, ColumnFamilyScope.GLOBAL),

  MESSAGES(71),
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY(72),
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME(73),
  MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY(74),
  PROCESS_SUBSCRIPTION_BY_KEY(75),

  JOB_ACTIVATABLE(76),

  SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY(77),
  SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME(78),

  USER_TASKS(79),
  USER_TASK_STATES(80),
  COMPENSATION_SUBSCRIPTION(81),

  PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_DEPLOYMENT_KEY(82, ColumnFamilyScope.GLOBAL),
  DMN_DECISION_KEY_BY_DECISION_ID_AND_DEPLOYMENT_KEY(83, ColumnFamilyScope.GLOBAL),
  FORM_KEY_BY_FORM_ID_AND_DEPLOYMENT_KEY(84, ColumnFamilyScope.GLOBAL),

  MESSAGE_CORRELATION(85),

  USERS(86, ColumnFamilyScope.GLOBAL),
  @Deprecated
  USER_KEY_BY_USERNAME(87, ColumnFamilyScope.GLOBAL),

  CLOCK(88, ColumnFamilyScope.GLOBAL),

  AUTHORIZATIONS(89, ColumnFamilyScope.GLOBAL),
  PERMISSIONS(90, ColumnFamilyScope.GLOBAL),

  PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_VERSION_TAG(91, ColumnFamilyScope.GLOBAL),
  DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION_TAG(92, ColumnFamilyScope.GLOBAL),
  FORM_KEY_BY_FORM_ID_AND_VERSION_TAG(93, ColumnFamilyScope.GLOBAL),

  @Deprecated
  AUTHORIZATION_KEY_BY_RESOURCE_ID(94, ColumnFamilyScope.GLOBAL),
  @Deprecated
  OWNER_TYPE_BY_OWNER_KEY(95, ColumnFamilyScope.GLOBAL),

  ROUTING(96),

  QUEUED_DISTRIBUTION(97),
  RETRIABLE_DISTRIBUTION(98),
  DISTRIBUTION_CONTINUATION(99),

  RESOURCES(100, ColumnFamilyScope.GLOBAL),
  RESOURCE_VERSION(101, ColumnFamilyScope.GLOBAL),
  RESOURCE_BY_ID_AND_VERSION(102, ColumnFamilyScope.GLOBAL),
  RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG(103, ColumnFamilyScope.GLOBAL),
  RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY(104, ColumnFamilyScope.GLOBAL),

  ROLES(105, ColumnFamilyScope.GLOBAL),

  TENANTS(106, ColumnFamilyScope.GLOBAL),

  USER_TASK_INTERMEDIATE_STATES(107),

  MAPPINGS(108, ColumnFamilyScope.GLOBAL),

  ASYNC_REQUEST_METADATA(109),

  GROUPS(110, ColumnFamilyScope.GLOBAL),

  @Deprecated
  REDISTRIBUTION(111),

  USERNAME_BY_USER_KEY(112, ColumnFamilyScope.GLOBAL),
  CLAIM_BY_ID(113, ColumnFamilyScope.GLOBAL),
  AUTHORIZATION_KEYS_BY_OWNER(114, ColumnFamilyScope.GLOBAL),

  BATCH_OPERATION(115),
  PENDING_BATCH_OPERATION(116),
  BATCH_OPERATION_CHUNKS(117),

  VARIABLE_DOCUMENT_STATE_BY_SCOPE_KEY(118),

  RELATIONS_BY_ENTITY(119, ColumnFamilyScope.GLOBAL),
  ENTITIES_BY_RELATION(120, ColumnFamilyScope.GLOBAL),

  USER_TASK_INITIAL_ASSIGNEE(121),

  RPI_USAGE_METRICS(122),
  BOOTSTRAPPED_AT(123);

  private final int value;
  private final ColumnFamilyScope columnFamilyScope;

  ZbColumnFamilies(final int value) {
    this.value = value;
    columnFamilyScope = ColumnFamilyScope.PARTITION_LOCAL;
  }

  ZbColumnFamilies(final int value, final ColumnFamilyScope columnFamilyScope) {
    this.value = value;
    this.columnFamilyScope = columnFamilyScope;
  }

  @Override
  public int getValue() {
    return value;
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return columnFamilyScope;
  }
}
