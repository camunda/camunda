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
package io.camunda.zeebe.protocol.record.value;

/**
 * Defines what wrote the value of a variable. There is one type per write path, so a reader can
 * tell apart, for example, a job completion from an output mapping.
 *
 * <p>The types fall into two groups. A requester type names the request that sent the value, such
 * as {@link #JOB_COMPLETION}. An engine type names the model element or property that produced the
 * value, such as {@link #OUTPUT_MAPPING} or {@link #MULTI_INSTANCE_LOOP_COUNTER}. {@link #UNKNOWN}
 * is used for records written before the source was tracked and for anything not covered by another
 * type.
 *
 * <p>The element that caused the write is on the variable source, see {@link
 * VariableSourceValue#getElementInstanceKey()} and {@link VariableSourceValue#getElementId()}.
 */
public enum VariableOperationType {

  /** A set variables request. */
  API,

  /** Variables produced by native user task output processing. */
  USER_TASK_COMPLETION,

  /** A create process instance request with variables. */
  PROCESS_INSTANCE_CREATION,

  /** The variable instructions of a process instance modification. */
  PROCESS_INSTANCE_MODIFICATION,

  /**
   * A job completion. This includes job-based script and business rule tasks, and the job result of
   * an ad-hoc sub-process.
   */
  JOB_COMPLETION,

  /** A fail job request with variables. */
  JOB_FAILURE,

  /** A throw error request from a job with variables. */
  JOB_ERROR,

  /** The completion of an execution listener job. */
  EXECUTION_LISTENER_COMPLETION,

  /** The payload of a message, written by the client that published it. */
  MESSAGE_CORRELATION,

  /**
   * The payload of a signal, sent by a broadcast signal request or by a process that throws the
   * signal.
   */
  SIGNAL_BROADCAST,

  /** An evaluate conditional request with variables. */
  CONDITIONAL_EVALUATION,

  /** A request that activates ad-hoc sub-process activities with variables. */
  AD_HOC_SUB_PROCESS_ACTIVATION,

  /** The result of an input mapping, written by the engine. */
  INPUT_MAPPING,

  /** The result of an output mapping, written by the engine. */
  OUTPUT_MAPPING,

  /** The result variable of a FEEL script task, written by the engine. */
  SCRIPT_RESULT,

  /** The result variable of a business rule task with a called decision, written by the engine. */
  DECISION_RESULT,

  /** The copy from the parent into the child process instance of a call activity, by the engine. */
  CALL_ACTIVITY_INPUT,

  /**
   * The copy from the child into the parent process instance when a call activity completes, by the
   * engine.
   */
  CALL_ACTIVITY_OUTPUT,

  /** The input element variable of a multi-instance activity, written by the engine. */
  MULTI_INSTANCE_INPUT_ELEMENT,

  /** The loop counter variable of a multi-instance activity, written by the engine. */
  MULTI_INSTANCE_LOOP_COUNTER,

  /**
   * The output element variable of a multi-instance inner instance, created with a nil value by the
   * engine.
   */
  MULTI_INSTANCE_OUTPUT_ELEMENT,

  /** The output collection of a multi-instance activity, written by the engine. */
  MULTI_INSTANCE_OUTPUT_COLLECTION,

  /**
   * The {@code adHocSubProcessElements} variable of an ad-hoc sub-process, which holds metadata
   * about its available activities, written by the engine. A process instance migration also uses
   * this type when it rewrites the variable for the target process definition.
   */
  AD_HOC_SUB_PROCESS_METADATA,

  /**
   * The output element variable of an ad-hoc sub-process inner instance, created with a nil value
   * by the engine.
   */
  AD_HOC_SUB_PROCESS_OUTPUT_ELEMENT,

  /** The output collection of an ad-hoc sub-process, written by the engine. */
  AD_HOC_SUB_PROCESS_OUTPUT_COLLECTION,

  /** A write that is not tracked, such as a record written before the source was tracked. */
  UNKNOWN
}
