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
import org.immutables.value.Value;

/**
 * The record value for a secret reference — a record that tracks the lifecycle of resolving a named
 * secret from an external secret store on behalf of paused jobs.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableSecretReferenceRecordValue.Builder.class)
public interface SecretReferenceRecordValue extends RecordValue {

  /** The identifier of the secret store that holds the referenced secret. */
  String getStoreId();

  /**
   * An opaque reference string that identifies the secret within the store. This is the name or key
   * used to look up the secret — not the resolved secret value itself.
   */
  String getSecretReference();

  /** The outcome of the most recent resolution attempt. */
  ResolutionState getResolutionState();

  /** The keys of the jobs that were non-activatable while awaiting this secret to be resolved. */
  List<Long> getJobKeys();
}
