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

public enum ZbColumnFamilies {
  DEFAULT,

  // util
  KEY,

  // process
  PROCESS_VERSION,

  // process cache
  PROCESS_CACHE,
  PROCESS_CACHE_BY_ID_AND_VERSION,
  PROCESS_CACHE_DIGEST_BY_ID,

  // element instance
  ELEMENT_INSTANCE_PARENT_CHILD,
  ELEMENT_INSTANCE_KEY,

  NUMBER_OF_TAKEN_SEQUENCE_FLOWS,

  // variable state
  ELEMENT_INSTANCE_CHILD_PARENT,
  VARIABLES,
  @Deprecated
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
  MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
  MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,

  // message subscription
  MESSAGE_SUBSCRIPTION_BY_KEY,
  @Deprecated // only used for migration logic
  MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
  // migration end
  MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,

  // message start event subscription
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,

  // process message subscription
  PROCESS_SUBSCRIPTION_BY_KEY,
  // migration start
  @Deprecated // only used for migration logic
  PROCESS_SUBSCRIPTION_BY_SENT_TIME,
  // migration end

  // incident
  INCIDENTS,
  INCIDENT_PROCESS_INSTANCES,
  INCIDENT_JOBS,

  // event
  EVENT_SCOPE,
  EVENT_TRIGGER,

  BLACKLIST,

  EXPORTER,

  AWAIT_WORKLOW_RESULT,

  JOB_BACKOFF,

  DMN_DECISIONS,
  DMN_DECISION_REQUIREMENTS,
  DMN_LATEST_DECISION_BY_ID,
  DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
  DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
  DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
  DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,

  // signal subscription
  SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
  SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,

  // distribution
  PENDING_DISTRIBUTION,
  COMMAND_DISTRIBUTION_RECORD,
}
