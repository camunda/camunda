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
import io.camunda.zeebe.protocol.record.intent.PolicyStatusIntent;
import org.immutables.value.Value;

/**
 * The policy status of partition 1: the committed policy, and the policy update that is open. The
 * query command carries no data; the answer carries the status.
 *
 * <p>See {@link PolicyStatusIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutablePolicyStatusRecordValue.Builder.class)
public interface PolicyStatusRecordValue extends RecordValue {

  /**
   * @return the ID of the committed policy, or empty if no policy update was committed
   */
  String getPolicyId();

  /**
   * @return the version of the committed policy, or 0 if no policy update was committed
   */
  long getPolicyVersion();

  /**
   * @return the Hub sequence of the committed policy, or 0 if no policy update was committed
   */
  long getSeqNo();

  /**
   * @return the status of the open policy update
   */
  PolicyUpdateStatus getUpdateStatus();

  /**
   * @return the ID of the open policy update, or empty if no update is open
   */
  String getOpenUpdateId();
}
