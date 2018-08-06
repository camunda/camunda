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
package io.zeebe.gateway;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.mocks.ZeebeClientMock;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import org.junit.Before;
import org.junit.Test;

public class HealthCheckEndpointTest {

  private ResponseMapper responseMapper;

  private HealthRequest request;
  private HealthResponse response;

  @Before
  public void setUp() {
    this.request = HealthRequest.getDefaultInstance();
    this.responseMapper = mock(ResponseMapper.class);

    final Partition partition =
        Partition.newBuilder()
            .setPartitionId(5)
            .setRole(PartitionBrokerRole.LEADER)
            .setTopicName(DEFAULT_TOPIC)
            .build();

    this.response =
        HealthResponse.newBuilder()
            .addBrokers(
                BrokerInfo.newBuilder()
                    .setPort(51015)
                    .setHost("localhost")
                    .addPartitions(partition)
                    .build())
            .build();
    when(responseMapper.toResponse(any())).thenReturn(this.response);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void healthCheckShouldCheckCorrectInvocation() {
    final EndpointManager endpoints =
        new EndpointManager(this.responseMapper, new ZeebeClientMock());

    final StreamObserver<HealthResponse> observer =
        (StreamObserver<HealthResponse>) mock(StreamObserver.class);

    endpoints.health(this.request, observer);

    verify(observer).onNext(this.response);
    verify(observer).onCompleted();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void healthCheckShouldProduceException() {
    final EndpointManager endpointsWithError =
        new EndpointManager(responseMapper, new ZeebeClientMock(true));

    final StreamObserver<HealthResponse> observer =
        (StreamObserver<HealthResponse>) mock(StreamObserver.class);

    endpointsWithError.health(this.request, observer);

    verify(observer).onError(any(RuntimeException.class));
  }
}
