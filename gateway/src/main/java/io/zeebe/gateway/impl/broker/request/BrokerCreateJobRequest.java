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

import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerCreateJobRequest extends BrokerExecuteCommand<JobRecord> {

  private final JobRecord requestDto = new JobRecord();

  public BrokerCreateJobRequest(String jobType) {
    super(ValueType.JOB, JobIntent.CREATE);
    requestDto.setType(jobType);
  }

  public BrokerCreateJobRequest setRetries(int retries) {
    requestDto.setRetries(retries);
    return this;
  }

  public BrokerCreateJobRequest setCustomHeaders(DirectBuffer customHeaders) {
    requestDto.setCustomHeaders(customHeaders);
    return this;
  }

  public BrokerCreateJobRequest setPayload(DirectBuffer payload) {
    requestDto.setPayload(payload);
    return this;
  }

  @Override
  protected BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected JobRecord toResponseDto(DirectBuffer buffer) {
    final JobRecord responseDto = new JobRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
