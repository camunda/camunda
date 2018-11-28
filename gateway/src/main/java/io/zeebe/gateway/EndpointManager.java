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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.ResponseMapper.BrokerResponseMapper;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadResponse;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class EndpointManager extends GatewayGrpc.GatewayImplBase {

  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final ActivateJobsHandler activateJobsHandler;

  public EndpointManager(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    this.topologyManager = brokerClient.getTopologyManager();
    this.activateJobsHandler = new ActivateJobsHandler(brokerClient);
  }

  @Override
  public void topology(
      final TopologyRequest request, final StreamObserver<TopologyResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toTopologyRequest,
        ResponseMapper::toTopologyResponse,
        responseObserver);
  }

  @Override
  public void deployWorkflow(
      final DeployWorkflowRequest request,
      final StreamObserver<DeployWorkflowResponse> responseObserver) {

    sendRequest(
        request,
        RequestMapper::toDeployWorkflowRequest,
        ResponseMapper::toDeployWorkflowResponse,
        responseObserver);
  }

  @Override
  public void publishMessage(
      PublishMessageRequest request, StreamObserver<PublishMessageResponse> responseObserver) {

    sendRequest(
        request,
        RequestMapper::toPublishMessageRequest,
        ResponseMapper::toPublishMessageResponse,
        responseObserver);
  }

  @Override
  public void updateJobRetries(
      UpdateJobRetriesRequest request, StreamObserver<UpdateJobRetriesResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toUpdateJobRetriesRequest,
        ResponseMapper::toUpdateJobRetriesResponse,
        responseObserver);
  }

  @Override
  public void createWorkflowInstance(
      CreateWorkflowInstanceRequest request,
      StreamObserver<CreateWorkflowInstanceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCreateWorkflowInstanceRequest,
        ResponseMapper::toCreateWorkflowInstanceResponse,
        responseObserver);
  }

  @Override
  public void cancelWorkflowInstance(
      CancelWorkflowInstanceRequest request,
      StreamObserver<CancelWorkflowInstanceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCancelWorkflowInstanceRequest,
        ResponseMapper::toCancelWorkflowInstanceResponse,
        responseObserver);
  }

  @Override
  public void updateWorkflowInstancePayload(
      UpdateWorkflowInstancePayloadRequest request,
      StreamObserver<UpdateWorkflowInstancePayloadResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toUpdateWorkflowInstancePayloadRequest,
        ResponseMapper::toUpdateWorkflowInstancePayloadResponse,
        responseObserver);
  }

  @Override
  public void failJob(FailJobRequest request, StreamObserver<FailJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toFailJobRequest,
        ResponseMapper::toFailJobResponse,
        responseObserver);
  }

  @Override
  public void listWorkflows(
      ListWorkflowsRequest request, StreamObserver<ListWorkflowsResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toListWorkflowsRequest,
        ResponseMapper::toListWorkflowsResponse,
        responseObserver);
  }

  @Override
  public void completeJob(
      CompleteJobRequest request, StreamObserver<CompleteJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCompleteJobRequest,
        ResponseMapper::toCompleteJobResponse,
        responseObserver);
  }

  @Override
  public void getWorkflow(
      GetWorkflowRequest request, StreamObserver<GetWorkflowResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toGetWorkflowRequest,
        ResponseMapper::toGetWorkflowResponse,
        responseObserver);
  }

  @Override
  public void activateJobs(
      ActivateJobsRequest request, StreamObserver<ActivateJobsResponse> responseObserver) {
    topologyManager.withTopology(
        topology ->
            activateJobsHandler.activateJobs(
                topology.getPartitionsCount(), request, responseObserver));
  }

  @Override
  public void resolveIncident(
      ResolveIncidentRequest request, StreamObserver<ResolveIncidentResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toResolveIncidentRequest,
        ResponseMapper::toResolveIncidentResponse,
        responseObserver);
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequest(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final StreamObserver<GrpcResponseT> streamObserver) {

    final BrokerRequest<BrokerResponseT> brokerRequest;
    try {
      brokerRequest = requestMapper.apply(grpcRequest);
    } catch (Exception e) {
      streamObserver.onError(convertThrowable(e));
      return;
    }

    brokerClient.sendRequest(
        brokerRequest,
        (key, response) -> {
          final GrpcResponseT grpcResponse = responseMapper.apply(key, response);
          streamObserver.onNext(grpcResponse);
          streamObserver.onCompleted();
        },
        error -> streamObserver.onError(convertThrowable(error)));
  }

  private static StatusRuntimeException convertThrowable(final Throwable cause) {
    final String description;

    if (cause instanceof ExecutionException) {
      description = cause.getCause().getMessage();
    } else {
      description = cause.getMessage();
    }

    return Status.INTERNAL.augmentDescription(description).withCause(cause).asRuntimeException();
  }
}
