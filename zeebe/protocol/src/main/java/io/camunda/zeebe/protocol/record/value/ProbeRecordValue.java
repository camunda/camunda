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

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/**
 * A probe: a short request from a client to a worker, answered within the client's request. A probe
 * belongs to no process instance and is not a job.
 *
 * <p>One record serves every {@link ProbeIntent}; each intent fills only the properties listed on
 * them, and the others keep their defaults.
 *
 * <p>No probe record holds a secret value: the input carries placeholders, and the engine injects
 * the values into the activation response only.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProbeRecordValue.Builder.class)
public interface ProbeRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the routing key of the probe, matched by exact string equality against the probe IDs a
   *     worker polls for, for example {@code validate-configuration/io.camunda:aws-credential:1};
   *     the engine never interprets it
   */
  String getProbeId();

  /**
   * @return the FEEL expression of the input, as the client sent it; set on {@code CREATE} only
   */
  String getInputExpression();

  /**
   * @return the evaluated input, with a placeholder instead of each secret value; set from {@code
   *     CREATED} on
   */
  Map<String, Object> getInput();

  /**
   * @return the secrets referenced by the input, each with the JSON pointer of the leaf the engine
   *     injects its value into; set from {@code CREATED} on
   */
  List<JobSecretReferenceValue> getSecretReferences();

  /**
   * @return how long the client waits for an answer, in milliseconds, as it sent it; 0 means the
   *     cluster's request timeout. Set on {@code CREATE}
   */
  long getRequestTimeout();

  /**
   * @return the absolute time (epoch milliseconds) at which the probe times out, computed by the
   *     gateway when it received the request
   */
  long getDeadline();

  /**
   * @return the name of the worker of the poll that collected the probe; set on the collected
   *     probes of {@code PROBE_BATCH:ACTIVATED}, on {@code ANSWER} and on {@code ANSWERED}
   */
  String getWorker();

  /**
   * @return how the worker answered; {@link ProbeOutcome#UNSPECIFIED} unless the record is {@code
   *     ANSWER} or {@code ANSWERED}
   */
  ProbeOutcome getOutcome();

  /**
   * @return the result of an {@link ProbeOutcome#OK} answer, whose shape the worker of the probe ID
   *     defines; set on the {@code ANSWER} command only, and empty on every event
   */
  Map<String, Object> getData();

  /**
   * @return why the worker could not answer; set on {@code ANSWER} and {@code ANSWERED} when the
   *     outcome is {@link ProbeOutcome#ERROR}
   */
  String getMessage();

  /**
   * @return why the engine ended the probe without an answer; set on {@code TIMED_OUT} and {@code
   *     CANCELED}
   */
  ProbeEndReason getEndReason();
}
