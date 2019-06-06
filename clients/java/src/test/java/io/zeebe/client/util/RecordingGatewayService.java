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
package io.zeebe.client.util;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
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
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class RecordingGatewayService extends GatewayImplBase {

  private final List<GeneratedMessageV3> requests = new ArrayList<>();

  private final Map<Class<? extends GeneratedMessageV3>, RequestHandler> requestHandlers =
      new HashMap<>();

  public RecordingGatewayService() {
    addRequestHandler(TopologyRequest.class, r -> TopologyResponse.getDefaultInstance());
    addRequestHandler(
        DeployWorkflowRequest.class, r -> DeployWorkflowResponse.getDefaultInstance());
    addRequestHandler(
        PublishMessageRequest.class, r -> PublishMessageResponse.getDefaultInstance());
    addRequestHandler(
        CreateWorkflowInstanceRequest.class,
        r -> CreateWorkflowInstanceResponse.getDefaultInstance());
    addRequestHandler(
        CancelWorkflowInstanceRequest.class,
        r -> CancelWorkflowInstanceResponse.getDefaultInstance());
    addRequestHandler(SetVariablesRequest.class, r -> SetVariablesResponse.getDefaultInstance());
    addRequestHandler(
        UpdateJobRetriesRequest.class, r -> UpdateJobRetriesResponse.getDefaultInstance());
    addRequestHandler(FailJobRequest.class, r -> FailJobResponse.getDefaultInstance());
    addRequestHandler(CompleteJobRequest.class, r -> CompleteJobResponse.getDefaultInstance());
    addRequestHandler(ActivateJobsRequest.class, r -> ActivateJobsResponse.getDefaultInstance());
    addRequestHandler(
        ResolveIncidentRequest.class, r -> ResolveIncidentResponse.getDefaultInstance());
  }

  public static Partition partition(int partitionId, PartitionBrokerRole role) {
    return Partition.newBuilder().setPartitionId(partitionId).setRole(role).build();
  }

  public static BrokerInfo broker(int nodeId, String host, int port, Partition... partitions) {
    return BrokerInfo.newBuilder()
        .setNodeId(nodeId)
        .setHost(host)
        .setPort(port)
        .addAllPartitions(Arrays.asList(partitions))
        .build();
  }

  public static WorkflowMetadata deployedWorkflow(
      String bpmnProcessId, int version, long workflowKey, String resourceName) {
    return WorkflowMetadata.newBuilder()
        .setBpmnProcessId(bpmnProcessId)
        .setVersion(version)
        .setWorkflowKey(workflowKey)
        .setResourceName(resourceName)
        .build();
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

  @Override
  public void topology(TopologyRequest request, StreamObserver<TopologyResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void deployWorkflow(
      DeployWorkflowRequest request, StreamObserver<DeployWorkflowResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void publishMessage(
      PublishMessageRequest request, StreamObserver<PublishMessageResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void createWorkflowInstance(
      CreateWorkflowInstanceRequest request,
      StreamObserver<CreateWorkflowInstanceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void cancelWorkflowInstance(
      CancelWorkflowInstanceRequest request,
      StreamObserver<CancelWorkflowInstanceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void setVariables(
      SetVariablesRequest request, StreamObserver<SetVariablesResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void updateJobRetries(
      UpdateJobRetriesRequest request, StreamObserver<UpdateJobRetriesResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void failJob(FailJobRequest request, StreamObserver<FailJobResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void completeJob(
      CompleteJobRequest request, StreamObserver<CompleteJobResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void activateJobs(
      ActivateJobsRequest request, StreamObserver<ActivateJobsResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void resolveIncident(
      ResolveIncidentRequest request, StreamObserver<ResolveIncidentResponse> responseObserver) {
    handle(request, responseObserver);
  }

  public void onTopologyRequest(
      int clusterSize, int partitionsCount, int replicationFactor, BrokerInfo... brokers) {
    addRequestHandler(
        TopologyRequest.class,
        request ->
            TopologyResponse.newBuilder()
                .setClusterSize(clusterSize)
                .setPartitionsCount(partitionsCount)
                .setReplicationFactor(replicationFactor)
                .addAllBrokers(Arrays.asList(brokers))
                .build());
  }

  public void onDeployWorkflowRequest(long key, WorkflowMetadata... deployedWorkflows) {
    addRequestHandler(
        DeployWorkflowRequest.class,
        request ->
            DeployWorkflowResponse.newBuilder()
                .setKey(key)
                .addAllWorkflows(Arrays.asList(deployedWorkflows))
                .build());
  }

  public void onCreateWorkflowInstanceRequest(
      long workflowKey, String bpmnProcessId, int version, long workflowInstanceKey) {
    addRequestHandler(
        CreateWorkflowInstanceRequest.class,
        request ->
            CreateWorkflowInstanceResponse.newBuilder()
                .setWorkflowKey(workflowKey)
                .setBpmnProcessId(bpmnProcessId)
                .setVersion(version)
                .setWorkflowInstanceKey(workflowInstanceKey)
                .build());
  }

  public void onActivateJobsRequest(ActivatedJob... activatedJobs) {
    addRequestHandler(
        ActivateJobsRequest.class,
        request ->
            ActivateJobsResponse.newBuilder().addAllJobs(Arrays.asList(activatedJobs)).build());
  }

  public void errorOnRequest(
      Class<? extends GeneratedMessageV3> requestClass, Supplier<Exception> errorSupplier) {
    addRequestHandler(
        requestClass,
        request -> {
          throw errorSupplier.get();
        });
  }

  public List<GeneratedMessageV3> getRequests() {
    return requests;
  }

  @SuppressWarnings("unchecked")
  public <T extends GeneratedMessageV3> T getRequest(int index) {
    return (T) requests.get(index);
  }

  public <T extends GeneratedMessageV3> T getLastRequest() {
    return getRequest(requests.size() - 1);
  }

  public void addRequestHandler(
      Class<? extends GeneratedMessageV3> requestClass, RequestHandler requestHandler) {
    requestHandlers.put(requestClass, requestHandler);
  }

  @SuppressWarnings("unchecked")
  private <RequestT extends GeneratedMessageV3, ResponseT extends GeneratedMessageV3> void handle(
      RequestT request, StreamObserver<ResponseT> responseObserver) {
    requests.add(request);
    try {
      final ResponseT response = (ResponseT) getRequestHandler(request).handle(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(convertThrowable(e));
    }
  }

  private RequestHandler getRequestHandler(GeneratedMessageV3 request) {
    final RequestHandler requestHandler = requestHandlers.get(request.getClass());
    if (requestHandler == null) {
      throw new IllegalStateException(
          "No request handler found for request class: " + request.getClass());
    }

    return requestHandler;
  }

  @FunctionalInterface
  interface RequestHandler<
      RequestT extends GeneratedMessageV3, ResponseT extends GeneratedMessageV3> {
    ResponseT handle(RequestT request) throws Exception;
  }
}
