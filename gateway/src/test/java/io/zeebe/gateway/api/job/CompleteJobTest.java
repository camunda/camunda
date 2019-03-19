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
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public class CompleteJobTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(gateway);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setVariables(variables).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariables(), variables);
  }

  @Test
  public void shouldConvertEmptyVariables() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(gateway);

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setVariables("").build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariables(), "{}");
  }
}
