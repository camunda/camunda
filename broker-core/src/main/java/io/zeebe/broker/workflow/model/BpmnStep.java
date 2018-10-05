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
  NONE,

  // exactly one outgoing sequence flow
  TAKE_SEQUENCE_FLOW,

  // end event, no outgoing sequence flow
  CONSUME_TOKEN,

  // xor-gateway
  EXCLUSIVE_SPLIT,

  // parallel gateway
  PARALLEL_SPLIT,
  PARALLEL_MERGE,

  CREATE_JOB,

  APPLY_INPUT_MAPPING,

  APPLY_OUTPUT_MAPPING,

  ACTIVATE_GATEWAY,

  SUBSCRIBE_TO_INTERMEDIATE_MESSAGE,

  CREATE_TIMER,

  START_STATEFUL_ELEMENT,

  TRIGGER_END_EVENT,
  TRIGGER_START_EVENT,

  TERMINATE_CONTAINED_INSTANCES,
  TERMINATE_JOB_TASK,
  TERMINATE_TIMER,
  TERMINATE_ELEMENT,
  PROPAGATE_TERMINATION,

  CANCEL_PROCESS,
  COMPLETE_PROCESS
}
