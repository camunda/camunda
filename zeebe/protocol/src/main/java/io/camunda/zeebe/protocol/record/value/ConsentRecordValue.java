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
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/**
 * A consent record: a grant of a subject for an operation of a connection, a one-time approval of
 * one process instance, a decline, a revocation, or the request of a job that waits for one of
 * them. A person is named by their Camunda username.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableConsentRecordValue.Builder.class)
public interface ConsentRecordValue extends RecordValue, TenantOwned {

  /** The key of the grant this record is about, or -1 when it is about no grant. */
  long getConsentKey();

  /** The person whose record changes, and the only one who may consent. */
  String getSubject();

  /** Who started the process instance that asks, or empty when nobody did. */
  String getRequester();

  /** The connection the operation belongs to, for example {@code hibob}. */
  String getConnection();

  /** The operation consented to, for example {@code region.update}. */
  String getOperation();

  ConsentDecision getDecision();

  /** The value an approval shows and binds, as the task's {@code change} expression gave it. */
  Map<String, Object> getChange();

  /** Why an admin revoked a grant that is not theirs. */
  String getReason();

  /** The parked job a request or a resolution is about, or -1. */
  long getJobKey();

  long getProcessInstanceKey();

  long getElementInstanceKey();

  String getBpmnProcessId();

  String getElementId();

  /** The jobs a release hands out. */
  List<Long> getJobKeys();
}
