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
package io.camunda.zeebe.client.util;

import com.google.protobuf.GeneratedMessageV3;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Deployment;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerHealth;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class RecordingGatewayService extends GatewayImplBase {

  private final List<GeneratedMessageV3> requests = new ArrayList<>();

  private final Map<Class<? extends GeneratedMessageV3>, RequestHandler> requestHandlers =
      new HashMap<>();
  private final Map<Class<? extends GeneratedMessageV3>, Supplier<Throwable>> errorHandlers =
      new HashMap<>();

  public RecordingGatewayService() {
    addRequestHandler(TopologyRequest.class, r -> TopologyResponse.getDefaultInstance());
    addRequestHandler(
        DeployResourceRequest.class, r -> DeployResourceResponse.getDefaultInstance());
    addRequestHandler(
        PublishMessageRequest.class, r -> PublishMessageResponse.getDefaultInstance());
    addRequestHandler(
        CreateProcessInstanceRequest.class,
        r -> CreateProcessInstanceResponse.getDefaultInstance());
    addRequestHandler(
        CreateProcessInstanceWithResultRequest.class,
        r -> CreateProcessInstanceWithResultResponse.getDefaultInstance());
    addRequestHandler(
        CancelProcessInstanceRequest.class,
        r -> CancelProcessInstanceResponse.getDefaultInstance());
    addRequestHandler(SetVariablesRequest.class, r -> SetVariablesResponse.getDefaultInstance());
    addRequestHandler(
        UpdateJobRetriesRequest.class, r -> UpdateJobRetriesResponse.getDefaultInstance());
    addRequestHandler(FailJobRequest.class, r -> FailJobResponse.getDefaultInstance());
    addRequestHandler(ThrowErrorRequest.class, r -> ThrowErrorResponse.getDefaultInstance());
    addRequestHandler(CompleteJobRequest.class, r -> CompleteJobResponse.getDefaultInstance());
    addRequestHandler(ActivateJobsRequest.class, r -> ActivateJobsResponse.getDefaultInstance());
    addRequestHandler(
        ResolveIncidentRequest.class, r -> ResolveIncidentResponse.getDefaultInstance());
  }

  public static Partition partition(
      final int partitionId, final PartitionBrokerRole role, final PartitionBrokerHealth health) {
    return Partition.newBuilder()
        .setPartitionId(partitionId)
        .setRole(role)
        .setHealth(health)
        .build();
  }

  public static BrokerInfo broker(
      final int nodeId,
      final String host,
      final int port,
      final String version,
      final Partition... partitions) {
    return BrokerInfo.newBuilder()
        .setNodeId(nodeId)
        .setHost(host)
        .setPort(port)
        .setVersion(version)
        .addAllPartitions(Arrays.asList(partitions))
        .build();
  }

  public static ProcessMetadata deployedProcess(
      final String bpmnProcessId,
      final int version,
      final long processDefinitionKey,
      final String resourceName) {
    return ProcessMetadata.newBuilder()
        .setBpmnProcessId(bpmnProcessId)
        .setVersion(version)
        .setProcessDefinitionKey(processDefinitionKey)
        .setResourceName(resourceName)
        .build();
  }

  public static Deployment deployedResource(final ProcessMetadata process) {
    return Deployment.newBuilder().setProcess(process).build();
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
  public void activateJobs(
      final ActivateJobsRequest request,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void cancelProcessInstance(
      final CancelProcessInstanceRequest request,
      final StreamObserver<CancelProcessInstanceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void completeJob(
      final CompleteJobRequest request,
      final StreamObserver<CompleteJobResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void createProcessInstance(
      final CreateProcessInstanceRequest request,
      final StreamObserver<CreateProcessInstanceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void createProcessInstanceWithResult(
      final CreateProcessInstanceWithResultRequest request,
      final StreamObserver<CreateProcessInstanceWithResultResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void deployResource(
      final DeployResourceRequest request,
      final StreamObserver<DeployResourceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void failJob(
      final FailJobRequest request, final StreamObserver<FailJobResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void throwError(
      final ThrowErrorRequest request, final StreamObserver<ThrowErrorResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void publishMessage(
      final PublishMessageRequest request,
      final StreamObserver<PublishMessageResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void resolveIncident(
      final ResolveIncidentRequest request,
      final StreamObserver<ResolveIncidentResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void setVariables(
      final SetVariablesRequest request,
      final StreamObserver<SetVariablesResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void topology(
      final TopologyRequest request, final StreamObserver<TopologyResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void updateJobRetries(
      final UpdateJobRetriesRequest request,
      final StreamObserver<UpdateJobRetriesResponse> responseObserver) {
    handle(request, responseObserver);
  }

  public void onTopologyRequest(
      final int clusterSize,
      final int partitionsCount,
      final int replicationFactor,
      final String gatewayVersion,
      final BrokerInfo... brokers) {
    addRequestHandler(
        TopologyRequest.class,
        request ->
            TopologyResponse.newBuilder()
                .setClusterSize(clusterSize)
                .setPartitionsCount(partitionsCount)
                .setReplicationFactor(replicationFactor)
                .setGatewayVersion(gatewayVersion)
                .addAllBrokers(Arrays.asList(brokers))
                .build());
  }

  public void onDeployResourceRequest(final long key, final Deployment... deployments) {
    addRequestHandler(
        DeployResourceRequest.class,
        request ->
            DeployResourceResponse.newBuilder()
                .setKey(key)
                .addAllDeployments(Arrays.asList(deployments))
                .build());
  }

  public void onCreateProcessInstanceRequest(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final long processInstanceKey) {
    addRequestHandler(
        CreateProcessInstanceRequest.class,
        request ->
            CreateProcessInstanceResponse.newBuilder()
                .setProcessDefinitionKey(processDefinitionKey)
                .setBpmnProcessId(bpmnProcessId)
                .setVersion(version)
                .setProcessInstanceKey(processInstanceKey)
                .build());
  }

  public void onPublishMessageRequest(final long key) {
    addRequestHandler(
        PublishMessageRequest.class,
        request -> PublishMessageResponse.newBuilder().setKey(key).build());
  }

  public void onCreateProcessInstanceWithResultRequest(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final long processInstanceKey,
      final String variables) {
    addRequestHandler(
        CreateProcessInstanceWithResultRequest.class,
        request ->
            CreateProcessInstanceWithResultResponse.newBuilder()
                .setProcessDefinitionKey(processDefinitionKey)
                .setBpmnProcessId(bpmnProcessId)
                .setVersion(version)
                .setProcessInstanceKey(processInstanceKey)
                .setVariables(variables)
                .build());
  }

  public void onActivateJobsRequest(final ActivatedJob... activatedJobs) {
    addRequestHandler(
        ActivateJobsRequest.class,
        request ->
            ActivateJobsResponse.newBuilder().addAllJobs(Arrays.asList(activatedJobs)).build());
  }

  public void onActivateJobsRequest(final Throwable error) {
    addRequestHandler(ActivateJobsRequest.class, () -> error);
  }

  public void onSetVariablesRequest(final long key) {
    addRequestHandler(
        SetVariablesRequest.class,
        request -> SetVariablesResponse.newBuilder().setKey(key).build());
  }

  public void errorOnRequest(
      final Class<? extends GeneratedMessageV3> requestClass,
      final Supplier<Exception> errorSupplier) {
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
  public <T extends GeneratedMessageV3> T getRequest(final int index) {
    return (T) requests.get(index);
  }

  public <T extends GeneratedMessageV3> T getLastRequest() {
    return getRequest(requests.size() - 1);
  }

  public <T extends GeneratedMessageV3> void addRequestHandler(
      final Class<T> requestClass,
      final RequestHandler<T, ? extends GeneratedMessageV3> requestHandler) {
    errorHandlers.remove(requestClass);
    requestHandlers.put(requestClass, requestHandler);
  }

  public void addRequestHandler(
      final Class<? extends GeneratedMessageV3> requestClass, final Supplier<Throwable> thrower) {
    requestHandlers.remove(requestClass);
    errorHandlers.put(requestClass, thrower);
  }

  @SuppressWarnings("unchecked")
  private <RequestT extends GeneratedMessageV3, ResponseT extends GeneratedMessageV3> void handle(
      final RequestT request, final StreamObserver<ResponseT> responseObserver) {
    requests.add(request);
    try {
      final Class<? extends GeneratedMessageV3> requestType = request.getClass();
      if (requestHandlers.containsKey(requestType)) {
        final ResponseT response = (ResponseT) requestHandlers.get(requestType).handle(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
      } else if (errorHandlers.containsKey(requestType)) {
        final Throwable error = errorHandlers.get(requestType).get();
        responseObserver.onError(Status.fromThrowable(error).asRuntimeException());
      } else {
        throw new IllegalStateException(
            "No request or error handler found for request class: " + requestType);
      }

    } catch (final Exception e) {
      responseObserver.onError(convertThrowable(e));
    }
  }

  @FunctionalInterface
  interface RequestHandler<
      RequestT extends GeneratedMessageV3, ResponseT extends GeneratedMessageV3> {
    ResponseT handle(RequestT request) throws Exception;
  }
}
