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
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import java.util.List;
import org.immutables.value.Value;

/**
 * A worker's poll for probes, and the probes it collected, as {@link JobBatchRecordValue} is for
 * jobs. Unlike a job poll, which names one job type, a probe poll names every probe ID of the
 * worker.
 *
 * <p>See {@link ProbeBatchIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProbeBatchRecordValue.Builder.class)
public interface ProbeBatchRecordValue extends RecordValue {

  /**
   * @return the probe IDs to collect probes for
   */
  List<String> getProbeIds();

  /**
   * @return the name of the polling worker, recorded on the probes it collects
   */
  String getWorker();

  /**
   * @return the most probes this poll collects, across all its probe IDs
   */
  int getMaxProbesToActivate();

  /**
   * @return the keys of the collected probes
   */
  List<Long> getProbeKeys();

  /**
   * @return the collected probes, in the order of {@link #getProbeKeys()}, with secret placeholders
   *     in their input
   */
  List<ProbeRecordValue> getProbes();

  /**
   * @return whether more probes were activatable than fit into this batch
   */
  boolean isTruncated();

  /**
   * @return the identifiers of the tenants that this poll may collect probes for
   */
  List<String> getTenantIds();

  /**
   * @return the tenant filtering strategy used for the poll
   */
  TenantFilter getTenantFilter();
}
