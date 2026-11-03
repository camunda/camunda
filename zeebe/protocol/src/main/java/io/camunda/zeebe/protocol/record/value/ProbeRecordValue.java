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
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/**
 * A probe: a short request from a client to a worker, answered within the client's request. A probe
 * belongs to no process instance and is not a job.
 *
 * <p>No probe record ever holds a secret value: the input carries placeholders only, and the values
 * are injected into the copy pushed to the worker.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProbeRecordValue.Builder.class)
public interface ProbeRecordValue extends RecordValue, TenantOwned {

  /**
   * @return what is asked, matched exactly against the probe IDs a worker declares; opaque to the
   *     engine. By convention it names the kind and the subject, e.g. {@code
   *     validate-configuration/io.camunda:aws-credential:1}
   */
  String getProbeId();

  /**
   * @return the FEEL expression of the input, as sent by the client; only set on {@code CREATE}
   */
  String getInputExpression();

  /**
   * @return the evaluated input, with secret placeholders instead of values
   */
  Map<String, Object> getInput();

  /**
   * @return the secret references of the input, each with the JSON pointer of its leaf
   */
  List<JobSecretReferenceValue> getSecretReferences();

  /**
   * @return the request timeout the client asked for, in milliseconds; 0 means the default
   */
  long getRequestTimeout();

  /**
   * @return the point in time (epoch millis) at which the probe times out
   */
  long getDeadline();

  /**
   * @return the name of the subscription the probe was pushed to
   */
  String getWorker();

  /**
   * @return how many times a push of this probe was yielded
   */
  int getYieldCount();

  /**
   * @return the outcome of the answer
   */
  ProbeOutcome getOutcome();

  /**
   * @return the data of the answer
   */
  Map<String, Object> getData();

  /**
   * @return the message of an {@code ERROR} answer, or why the engine canceled the probe
   */
  String getMessage();

  /**
   * @return why the probe ended without an answer
   */
  ProbeEndReason getEndReason();
}
