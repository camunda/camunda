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
package io.zeebe.gateway.api.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import org.junit.Test;

public class FailJobTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final FailJobStub stub = new FailJobStub();
    stub.registerWith(gateway);

    final int retries = 123;

    final FailJobRequest request =
        FailJobRequest.newBuilder()
            .setJobKey(stub.getKey())
            .setRetries(retries)
            .setErrorMessage("failed")
            .build();

    // when
    final FailJobResponse response = client.failJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerFailJobRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.FAIL);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getRetries()).isEqualTo(retries);
    assertThat(brokerRequestValue.getErrorMessage()).isEqualTo(wrapString("failed"));
  }
}
