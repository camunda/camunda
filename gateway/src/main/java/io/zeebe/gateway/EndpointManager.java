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
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.ClientOutOfMemoryException;
import io.zeebe.gateway.cmd.GrpcStatusException;
import io.zeebe.gateway.cmd.GrpcStatusExceptionImpl;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo.Builder;
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
import io.zeebe.msgpack.MsgpackPropertyException;
import java.util.ArrayList;
import java.util.List;
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
    final TopologyResponse.Builder topologyResponseBuilder = TopologyResponse.newBuilder();
    final BrokerClusterState topology = topologyManager.getTopology();

    if (topology != null) {

      topologyResponseBuilder
          .setClusterSize(topology.getClusterSize())
          .setPartitionsCount(topology.getPartitionsCount())
          .setReplicationFactor(topology.getReplicationFactor());

      final ArrayList<BrokerInfo> brokers = new ArrayList<>();

      topology
          .getBrokers()
          .forEach(
              brokerId -> {
                final Builder brokerInfo = BrokerInfo.newBuilder();
                addBrokerInfo(brokerInfo, brokerId, topology);
                addPartitionInfoToBrokerInfo(brokerInfo, brokerId, topology);

                brokers.add(brokerInfo.build());
              });

      topologyResponseBuilder.addAllBrokers(brokers);
      final TopologyResponse response = topologyResponseBuilder.build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } else {
      final StatusRuntimeException error =
          Status.UNAVAILABLE.augmentDescription("No brokers available").asRuntimeException();
      responseObserver.onError(error);
    }
  }

  private void addBrokerInfo(Builder brokerInfo, Integer brokerId, BrokerClusterState topology) {
    final String[] addressParts = topology.getBrokerAddress(brokerId).split(":");

    brokerInfo
        .setNodeId(brokerId)
        .setHost(addressParts[0])
        .setPort(Integer.parseInt(addressParts[1]));
  }

  private void addPartitionInfoToBrokerInfo(
      Builder brokerInfo, Integer brokerId, BrokerClusterState topology) {
    topology
        .getPartitions()
        .forEach(
            partitionId -> {
              final Partition.Builder partitionBuilder = Partition.newBuilder();
              partitionBuilder.setPartitionId(partitionId);

              if (topology.getLeaderForPartition(partitionId) == brokerId) {
                partitionBuilder.setRole(PartitionBrokerRole.LEADER);
              } else {
                final List<Integer> followersForPartition =
                    topology.getFollowersForPartition(partitionId);

                if (followersForPartition != null && followersForPartition.contains(brokerId)) {
                  partitionBuilder.setRole(PartitionBrokerRole.FOLLOWER);
                } else {
                  return;
                }
              }
              brokerInfo.addPartitions(partitionBuilder);
            });
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
  public void setVariables(
      SetVariablesRequest request, StreamObserver<SetVariablesResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toSetVariablesRequest,
        ResponseMapper::toSetVariablesResponse,
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
  public void completeJob(
      CompleteJobRequest request, StreamObserver<CompleteJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCompleteJobRequest,
        ResponseMapper::toCompleteJobResponse,
        responseObserver);
  }

  @Override
  public void activateJobs(
      ActivateJobsRequest request, StreamObserver<ActivateJobsResponse> responseObserver) {
    final BrokerClusterState topology = topologyManager.getTopology();
    activateJobsHandler.activateJobs(topology.getPartitionsCount(), request, responseObserver);
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
    } catch (MsgpackPropertyException e) {
      streamObserver.onError(
          convertThrowable(
              new GrpcStatusExceptionImpl(e.getMessage(), Status.INVALID_ARGUMENT, e)));
      return;
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

  private StatusRuntimeException convertThrowable(Throwable cause) {
    Status status = Status.INTERNAL;

    if (cause instanceof ExecutionException) {
      return convertThrowable(cause.getCause());
    }

    if (cause instanceof BrokerErrorException) {
      status = mapBrokerErrorToStatus(((BrokerErrorException) cause).getError());
    } else if (cause instanceof BrokerRejectionException) {
      status = mapRejectionToStatus(((BrokerRejectionException) cause).getRejection());
    } else if (cause instanceof ClientOutOfMemoryException) {
      status = Status.UNAVAILABLE.augmentDescription(cause.getMessage());
    } else if (cause instanceof GrpcStatusException) {
      status = ((GrpcStatusException) cause).getGrpcStatus();
    } else {
      status = status.augmentDescription("Unexpected error occurred during the request processing");
    }

    final StatusRuntimeException convertedThrowable = status.withCause(cause).asRuntimeException();
    Loggers.GATEWAY_LOGGER.error("Error handling gRPC request", convertedThrowable);

    return convertedThrowable;
  }

  private Status mapBrokerErrorToStatus(BrokerError error) {
    switch (error.getCode()) {
      case WORKFLOW_NOT_FOUND:
        return Status.NOT_FOUND.augmentDescription(error.getMessage());
      default:
        return Status.INTERNAL.augmentDescription(
            String.format(
                "Unexpected error occurred between gateway and broker (code: %s)",
                error.getCode()));
    }
  }

  private Status mapRejectionToStatus(BrokerRejection rejection) {
    final String description =
        String.format(
            "Command rejected with code '%s': %s", rejection.getIntent(), rejection.getReason());
    final Status status;

    switch (rejection.getType()) {
      case INVALID_ARGUMENT:
        status = Status.INVALID_ARGUMENT;
        break;
      case NOT_FOUND:
        status = Status.NOT_FOUND;
        break;
      case ALREADY_EXISTS:
        status = Status.ALREADY_EXISTS;
        break;
      case INVALID_STATE:
        status = Status.FAILED_PRECONDITION;
        break;
      default:
        status = Status.UNKNOWN;
        break;
    }

    return status.augmentDescription(description);
  }
}
