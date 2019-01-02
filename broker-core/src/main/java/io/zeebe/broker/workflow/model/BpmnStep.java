/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.model;

public enum BpmnStep {

  // flow element container (process, sub process)
  TRIGGER_START_EVENT,
  CREATE_INSTANCE_ON_START_EVENT,
  COMPLETE_PROCESS,
  TERMINATE_CONTAINED_INSTANCES,

  // flow node
  ENTER_FLOW_NODE,
  ACTIVATE_FLOW_NODE,
  COMPLETE_FLOW_NODE,
  TERMINATE_FLOW_NODE,
  PROPAGATE_TERMINATION,

  CONSUME_TOKEN,
  TAKE_SEQUENCE_FLOW,

  // activity
  ACTIVATE_ACTIVITY,
  COMPLETE_ACTIVITY,
  TERMINATE_ACTIVITY,

  // task
  CREATE_JOB,
  TERMINATE_JOB_TASK,
  ACTIVATE_RECEIVE_TASK,

  // exclusive gateway
  ACTIVATE_GATEWAY,
  EXCLUSIVE_SPLIT,

  // parallel gateway
  PARALLEL_SPLIT,
  PARALLEL_MERGE,

  // events
  ENTER_EVENT,
  ACTIVATE_EVENT,
  SUBSCRIBE_TO_EVENTS,
  TRIGGER_EVENT,
  TRIGGER_EVENT_BASED_GATEWAY,
  TRIGGER_BOUNDARY_EVENT,
  TRIGGER_RECEIVE_TASK,
  APPLY_EVENT,
}
