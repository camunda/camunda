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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public class CreateJobTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CreateJobStub stub = new CreateJobStub();
    stub.registerWith(gateway);

    final String payload = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final CreateJobRequest request =
        CreateJobRequest.newBuilder().setJobType(stub.getType()).setPayload(payload).build();

    // when
    final CreateJobResponse response = client.createJob(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKey()).isEqualTo(stub.getKey());

    final BrokerCreateJobRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getPayload(), payload);
  }

  @Test
  public void shouldConvertEmptyPayload() {
    // given
    final CreateJobStub stub = new CreateJobStub();
    stub.registerWith(gateway);

    final CreateJobRequest request =
        CreateJobRequest.newBuilder().setJobType(stub.getType()).setPayload("").build();

    // when
    final CreateJobResponse response = client.createJob(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKey()).isEqualTo(stub.getKey());

    final BrokerCreateJobRequest brokerRequest = gateway.getSingleBrokerRequest();
    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getPayload(), "{}");
  }
}
