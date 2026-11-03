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
 * A worker's poll for the probes of one probe ID, and the probes it collected, as {@link
 * JobBatchRecordValue} for jobs. The probes carry their input with secret placeholders only.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProbeBatchRecordValue.Builder.class)
public interface ProbeBatchRecordValue extends RecordValue {

  /**
   * @return the probe ID to collect probes for
   */
  String getProbeId();

  /**
   * @return the name of the worker that polls, recorded on the probes it collects
   */
  String getWorker();

  /**
   * @return the most probes this poll collects
   */
  int getMaxProbesToActivate();

  /**
   * @return the tenants to collect probes for, unless the filter is {@code ASSIGNED}
   */
  List<String> getTenantIds();

  /**
   * @return how the tenants of the poll are determined
   */
  TenantFilter getTenantFilter();

  /**
   * @return the keys of the collected probes
   */
  List<Long> getProbeKeys();

  /**
   * @return the collected probes, in the order of {@link #getProbeKeys()}
   */
  List<ProbeRecordValue> getProbes();

  /**
   * @return whether more probes were waiting than fitted into the batch
   */
  boolean isTruncated();
}
