/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

  BLACKLIST
}
