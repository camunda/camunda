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
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.msgpack.value.StringValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerActivateJobsRequest extends BrokerExecuteCommand<JobBatchRecord> {

  private final JobBatchRecord requestDto = new JobBatchRecord();

  public BrokerActivateJobsRequest(String jobType) {
    super(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE);
    requestDto.setType(jobType);
  }

  public BrokerActivateJobsRequest setWorker(String worker) {
    requestDto.setWorker(worker);
    return this;
  }

  public BrokerActivateJobsRequest setTimeout(long timeout) {
    requestDto.setTimeout(timeout);
    return this;
  }

  public BrokerActivateJobsRequest setMaxJobsToActivate(int maxJobsToActivate) {
    requestDto.setMaxJobsToActivate(maxJobsToActivate);
    return this;
  }

  public BrokerActivateJobsRequest setVariables(List<String> fetchVariables) {
    final ValueArray<StringValue> variables = requestDto.variables();
    fetchVariables.stream()
        .map(BufferUtil::wrapString)
        .forEach(buffer -> variables.add().wrap(buffer));

    return this;
  }

  @Override
  public JobBatchRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected JobBatchRecord toResponseDto(DirectBuffer buffer) {
    final JobBatchRecord responseDto = new JobBatchRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
