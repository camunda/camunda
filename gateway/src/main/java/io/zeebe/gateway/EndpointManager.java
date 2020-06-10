/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.ResponseMapper.BrokerResponseMapper;
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.ClientOutOfMemoryException;
import io.zeebe.gateway.cmd.GrpcStatusException;
import io.zeebe.gateway.cmd.GrpcStatusExceptionImpl;
import io.zeebe.gateway.cmd.PartitionNotFoundException;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.RequestRetryHandler;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
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
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultResponse;
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
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.msgpack.MsgpackPropertyException;
import io.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class EndpointManager extends GatewayGrpc.GatewayImplBase {

  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final LongPollingActivateJobsHandler activateJobsHandler;
  private final RequestRetryHandler requestRetryHandler;

  public EndpointManager(
      final BrokerClient brokerClient, final LongPollingActivateJobsHandler longPollingHandler) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
    activateJobsHandler = longPollingHandler;
    requestRetryHandler = new RequestRetryHandler(brokerClient, topologyManager);
  }

  private void addBrokerInfo(
      final Builder brokerInfo, final Integer brokerId, final BrokerClusterState topology) {
    final String[] addressParts = topology.getBrokerAddress(brokerId).split(":");

    brokerInfo
        .setNodeId(brokerId)
        .setHost(addressParts[0])
        .setPort(Integer.parseInt(addressParts[1]))
        .setVersion(topology.getBrokerVersion(brokerId));
  }

  private void addPartitionInfoToBrokerInfo(
      final Builder brokerInfo, final Integer brokerId, final BrokerClusterState topology) {
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
  public void activateJobs(
      final ActivateJobsRequest request,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    activateJobsHandler.activateJobs(request, responseObserver);
  }

  @Override
  public void cancelWorkflowInstance(
      final CancelWorkflowInstanceRequest request,
      final StreamObserver<CancelWorkflowInstanceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCancelWorkflowInstanceRequest,
        ResponseMapper::toCancelWorkflowInstanceResponse,
        responseObserver);
  }

  @Override
  public void completeJob(
      final CompleteJobRequest request,
      final StreamObserver<CompleteJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCompleteJobRequest,
        ResponseMapper::toCompleteJobResponse,
        responseObserver);
  }

  @Override
  public void createWorkflowInstance(
      final CreateWorkflowInstanceRequest request,
      final StreamObserver<CreateWorkflowInstanceResponse> responseObserver) {
    sendRequestWithRetryPartitions(
        request,
        RequestMapper::toCreateWorkflowInstanceRequest,
        ResponseMapper::toCreateWorkflowInstanceResponse,
        responseObserver);
  }

  @Override
  public void createWorkflowInstanceWithResult(
      final CreateWorkflowInstanceWithResultRequest request,
      final StreamObserver<CreateWorkflowInstanceWithResultResponse> responseObserver) {
    if (request.getRequestTimeout() > 0) {
      sendRequestWithRetryPartitions(
          request,
          RequestMapper::toCreateWorkflowInstanceWithResultRequest,
          ResponseMapper::toCreateWorkflowInstanceWithResultResponse,
          responseObserver,
          Duration.ofMillis(request.getRequestTimeout()));
    } else {
      sendRequestWithRetryPartitions(
          request,
          RequestMapper::toCreateWorkflowInstanceWithResultRequest,
          ResponseMapper::toCreateWorkflowInstanceWithResultResponse,
          responseObserver);
    }
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
  public void failJob(
      final FailJobRequest request, final StreamObserver<FailJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toFailJobRequest,
        ResponseMapper::toFailJobResponse,
        responseObserver);
  }

  @Override
  public void throwError(
      final ThrowErrorRequest request, final StreamObserver<ThrowErrorResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toThrowErrorRequest,
        ResponseMapper::toThrowErrorResponse,
        responseObserver);
  }

  @Override
  public void publishMessage(
      final PublishMessageRequest request,
      final StreamObserver<PublishMessageResponse> responseObserver) {

    sendRequest(
        request,
        RequestMapper::toPublishMessageRequest,
        ResponseMapper::toPublishMessageResponse,
        responseObserver);
  }

  @Override
  public void resolveIncident(
      final ResolveIncidentRequest request,
      final StreamObserver<ResolveIncidentResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toResolveIncidentRequest,
        ResponseMapper::toResolveIncidentResponse,
        responseObserver);
  }

  @Override
  public void setVariables(
      final SetVariablesRequest request,
      final StreamObserver<SetVariablesResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toSetVariablesRequest,
        ResponseMapper::toSetVariablesResponse,
        responseObserver);
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

      final String gatewayVersion = VersionUtil.getVersion();
      if (gatewayVersion != null && !gatewayVersion.isBlank()) {
        topologyResponseBuilder.setGatewayVersion(gatewayVersion);
      }

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

  @Override
  public void updateJobRetries(
      final UpdateJobRetriesRequest request,
      final StreamObserver<UpdateJobRetriesResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toUpdateJobRetriesRequest,
        ResponseMapper::toUpdateJobRetriesResponse,
        responseObserver);
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequest(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final StreamObserver<GrpcResponseT> streamObserver) {

    final BrokerRequest<BrokerResponseT> brokerRequest =
        mapRequest(grpcRequest, requestMapper, streamObserver);
    if (brokerRequest == null) {
      return;
    }

    suppressCancelledException(grpcRequest, streamObserver);
    brokerClient.sendRequestWithRetry(
        brokerRequest,
        (key, response) -> consumeResponse(responseMapper, streamObserver, key, response),
        error -> streamObserver.onError(convertThrowable(error)));
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequestWithRetryPartitions(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final StreamObserver<GrpcResponseT> streamObserver) {

    final BrokerRequest<BrokerResponseT> brokerRequest =
        mapRequest(grpcRequest, requestMapper, streamObserver);
    if (brokerRequest == null) {
      return;
    }

    suppressCancelledException(grpcRequest, streamObserver);
    requestRetryHandler.sendRequest(
        brokerRequest,
        (key, response) -> consumeResponse(responseMapper, streamObserver, key, response),
        error -> streamObserver.onError(convertThrowable(error)));
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequestWithRetryPartitions(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final StreamObserver<GrpcResponseT> streamObserver,
      final Duration timeout) {

    final BrokerRequest<BrokerResponseT> brokerRequest =
        mapRequest(grpcRequest, requestMapper, streamObserver);
    if (brokerRequest == null) {
      return;
    }

    suppressCancelledException(grpcRequest, streamObserver);
    requestRetryHandler.sendRequest(
        brokerRequest,
        (key, response) -> consumeResponse(responseMapper, streamObserver, key, response),
        error -> streamObserver.onError(convertThrowable(error)),
        timeout);
  }

  private <GrpcRequestT, GrpcResponseT> void suppressCancelledException(
      final GrpcRequestT grpcRequest, final StreamObserver<GrpcResponseT> streamObserver) {
    final ServerCallStreamObserver<GrpcResponseT> serverObserver =
        (ServerCallStreamObserver<GrpcResponseT>) streamObserver;
    serverObserver.setOnCancelHandler(
        () -> Loggers.GATEWAY_LOGGER.trace("gRPC {} request cancelled", grpcRequest.getClass()));
  }

  private <BrokerResponseT, GrpcResponseT> void consumeResponse(
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final StreamObserver<GrpcResponseT> streamObserver,
      final long key,
      final BrokerResponseT response) {
    final GrpcResponseT grpcResponse = responseMapper.apply(key, response);
    streamObserver.onNext(grpcResponse);
    streamObserver.onCompleted();
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> BrokerRequest<BrokerResponseT> mapRequest(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final StreamObserver<GrpcResponseT> streamObserver) {
    final BrokerRequest<BrokerResponseT> brokerRequest;
    try {
      brokerRequest = requestMapper.apply(grpcRequest);
    } catch (final MsgpackPropertyException e) {
      streamObserver.onError(
          convertThrowable(
              new GrpcStatusExceptionImpl(e.getMessage(), Status.INVALID_ARGUMENT, e)));
      return null;
    } catch (final Exception e) {
      streamObserver.onError(convertThrowable(e));
      return null;
    }
    return brokerRequest;
  }

  public static StatusRuntimeException convertThrowable(final Throwable cause) {
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
    } else if (cause instanceof TimeoutException) { // can be thrown by transport
      status =
          Status.DEADLINE_EXCEEDED.augmentDescription(
              "Time out between gateway and broker: " + cause.getMessage());
    } else if (cause instanceof GrpcStatusException) {
      status = ((GrpcStatusException) cause).getGrpcStatus();
    } else if (cause instanceof PartitionNotFoundException) {
      status = Status.NOT_FOUND.augmentDescription(cause.getMessage());
    } else {
      status = status.augmentDescription("Unexpected error occurred during the request processing");
    }

    final StatusRuntimeException convertedThrowable = status.withCause(cause).asRuntimeException();

    // When there is back pressure, there will be a lot of `RESOURCE_EXHAUSTED` errors and the log
    // can get flooded. Until we find a way to limit the number of log messages,
    // let's do not log them.
    if (status.getCode() != Status.RESOURCE_EXHAUSTED.getCode()) {
      Loggers.GATEWAY_LOGGER.error("Error handling gRPC request", convertedThrowable);
    }

    return convertedThrowable;
  }

  private static Status mapBrokerErrorToStatus(final BrokerError error) {
    switch (error.getCode()) {
      case WORKFLOW_NOT_FOUND:
        return Status.NOT_FOUND.augmentDescription(error.getMessage());
      case RESOURCE_EXHAUSTED:
        return Status.RESOURCE_EXHAUSTED.augmentDescription(error.getMessage());
      default:
        return Status.INTERNAL.augmentDescription(
            String.format(
                "Unexpected error occurred between gateway and broker (code: %s)",
                error.getCode()));
    }
  }

  private static Status mapRejectionToStatus(final BrokerRejection rejection) {
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
