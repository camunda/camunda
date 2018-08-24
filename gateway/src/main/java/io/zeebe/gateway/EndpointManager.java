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

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;

public class EndpointManager extends GatewayGrpc.GatewayImplBase {

  private final ResponseMapper responseMapper;
  private final ZeebeClient zbClient;

  public ResponseMapper getResponseMapper() {
    return responseMapper;
  }

  public ZeebeClient getZbClient() {
    return zbClient;
  }

  EndpointManager(final ResponseMapper mapper, final ZeebeClient clusterClient) {
    this.responseMapper = mapper;
    this.zbClient = clusterClient;
  }

  @Override
  public void health(
      final HealthRequest request, final StreamObserver<HealthResponse> responseObserver) {

    try {
      final Topology response = zbClient.newTopologyRequest().send().join();

      responseObserver.onNext(responseMapper.toResponse(response));
      responseObserver.onCompleted();

    } catch (final RuntimeException e) {
      responseObserver.onError(e);
    }
  }
}
