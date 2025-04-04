/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.atomix.utils.net.Address;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.auth.ClaimTransformer;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.ResponseMapper.BrokerResponseMapper;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.RequestRetryHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.stream.StreamJobsHandler;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerHealth;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutResponse;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.util.VersionUtil;
import io.grpc.Context;
import io.grpc.stub.ServerCallStreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class EndpointManager {

  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final ActivateJobsHandler<ActivateJobsResponse> activateJobsHandler;
  private final RequestRetryHandler requestRetryHandler;
  private final StreamJobsHandler streamJobsHandler;

  public EndpointManager(
      final BrokerClient brokerClient,
      final ActivateJobsHandler<ActivateJobsResponse> activateJobsHandler,
      final StreamJobsHandler streamJobsHandler,
      final MultiTenancyConfiguration multiTenancy) {
    this.brokerClient = brokerClient;
    this.activateJobsHandler = activateJobsHandler;
    this.streamJobsHandler = streamJobsHandler;
    topologyManager = brokerClient.getTopologyManager();
    requestRetryHandler = new RequestRetryHandler(brokerClient, topologyManager);
    RequestMapper.setMultiTenancyEnabled(multiTenancy.isEnabled());
  }

  private void addBrokerInfo(
      final Builder brokerInfo, final Integer brokerId, final BrokerClusterState topology) {
    final String brokerAddress = topology.getBrokerAddress(brokerId);
    final Address address = Address.from(brokerAddress);

    brokerInfo
        .setNodeId(brokerId)
        .setHost(address.host())
        .setPort(address.port())
        .setVersion(topology.getBrokerVersion(brokerId));
  }

  private void addPartitionInfoToBrokerInfo(
      final Builder brokerInfo, final Integer brokerId, final BrokerClusterState topology) {
    topology
        .getPartitions()
        .forEach(
            partitionId -> {
              final Partition.Builder partitionBuilder =
                  Partition.newBuilder().setPartitionId(partitionId);

              if (!setRole(brokerId, partitionId, topology, partitionBuilder)) {
                return;
              }

              final var status = topology.getPartitionHealth(brokerId, partitionId);
              switch (status) {
                case HEALTHY -> partitionBuilder.setHealth(PartitionBrokerHealth.HEALTHY);
                case UNHEALTHY -> partitionBuilder.setHealth(PartitionBrokerHealth.UNHEALTHY);
                case DEAD -> partitionBuilder.setHealth(PartitionBrokerHealth.DEAD);
                default ->
                    Loggers.GATEWAY_LOGGER.debug(
                        "Unsupported partition broker health status '{}'", status.name());
              }
              brokerInfo.addPartitions(partitionBuilder);
            });
  }

  /**
   * Sets the broker's partition role in the Partition.Builder
   *
   * @return true if it could set the role. False if no role was could be found.
   */
  private boolean setRole(
      final Integer brokerId,
      final Integer partitionId,
      final BrokerClusterState topology,
      final Partition.Builder partitionBuilder) {
    final int partitionLeader = topology.getLeaderForPartition(partitionId);
    final Set<Integer> partitionFollowers = topology.getFollowersForPartition(partitionId);
    final Set<Integer> partitionInactives = topology.getInactiveNodesForPartition(partitionId);

    if (partitionLeader == brokerId) {
      partitionBuilder.setRole(PartitionBrokerRole.LEADER);
    } else if (partitionFollowers != null && partitionFollowers.contains(brokerId)) {
      partitionBuilder.setRole(PartitionBrokerRole.FOLLOWER);
    } else if (partitionInactives != null && partitionInactives.contains(brokerId)) {
      partitionBuilder.setRole(PartitionBrokerRole.INACTIVE);
    } else {
      return false;
    }

    return true;
  }

  public void streamActivatedJobs(
      final StreamActivatedJobsRequest request,
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    try {
      final JobActivationProperties brokerRequest =
          RequestMapper.toJobActivationProperties(request);
      streamJobsHandler.handle(request.getType(), brokerRequest, responseObserver);
    } catch (final Exception e) {
      responseObserver.onError(e);
    }
  }

  public void activateJobs(
      final ActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver) {
    try {
      final BrokerActivateJobsRequest brokerRequest =
          (BrokerActivateJobsRequest)
              mapToBrokerRequest(request, RequestMapper::toActivateJobsRequest);
      activateJobsHandler.activateJobs(
          brokerRequest,
          responseObserver,
          responseObserver::setOnCancelHandler,
          request.getRequestTimeout());
    } catch (final Exception e) {
      responseObserver.onError(e);
    }
  }

  public void cancelProcessInstance(
      final CancelProcessInstanceRequest request,
      final ServerStreamObserver<CancelProcessInstanceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCancelProcessInstanceRequest,
        ResponseMapper::toCancelProcessInstanceResponse,
        responseObserver);
  }

  public void completeJob(
      final CompleteJobRequest request,
      final ServerStreamObserver<CompleteJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toCompleteJobRequest,
        ResponseMapper::toCompleteJobResponse,
        responseObserver);
  }

  public void createProcessInstance(
      final CreateProcessInstanceRequest request,
      final ServerStreamObserver<CreateProcessInstanceResponse> responseObserver) {
    sendRequestWithRetryPartitions(
        request,
        RequestMapper::toCreateProcessInstanceRequest,
        ResponseMapper::toCreateProcessInstanceResponse,
        responseObserver);
  }

  public void createProcessInstanceWithResult(
      final CreateProcessInstanceWithResultRequest request,
      final ServerStreamObserver<CreateProcessInstanceWithResultResponse> responseObserver) {
    if (request.getRequestTimeout() > 0) {
      sendRequestWithRetryPartitions(
          request,
          RequestMapper::toCreateProcessInstanceWithResultRequest,
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          responseObserver,
          Duration.ofMillis(request.getRequestTimeout()));
    } else {
      sendRequestWithRetryPartitions(
          request,
          RequestMapper::toCreateProcessInstanceWithResultRequest,
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          responseObserver);
    }
  }

  public void evaluateDecision(
      final EvaluateDecisionRequest request,
      final ServerStreamObserver<EvaluateDecisionResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toEvaluateDecisionRequest,
        ResponseMapper::toEvaluateDecisionResponse,
        responseObserver);
  }

  public void deployProcess(
      final DeployProcessRequest request,
      final ServerStreamObserver<DeployProcessResponse> responseObserver) {

    sendRequest(
        request,
        RequestMapper::toDeployProcessRequest,
        ResponseMapper::toDeployProcessResponse,
        responseObserver);
  }

  public void deployResource(
      final DeployResourceRequest request,
      final ServerStreamObserver<DeployResourceResponse> responseObserver) {

    sendRequest(
        request,
        RequestMapper::toDeployResourceRequest,
        ResponseMapper::toDeployResourceResponse,
        responseObserver);
  }

  public void failJob(
      final FailJobRequest request, final ServerStreamObserver<FailJobResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toFailJobRequest,
        ResponseMapper::toFailJobResponse,
        responseObserver);
  }

  public void throwError(
      final ThrowErrorRequest request,
      final ServerStreamObserver<ThrowErrorResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toThrowErrorRequest,
        ResponseMapper::toThrowErrorResponse,
        responseObserver);
  }

  public void publishMessage(
      final PublishMessageRequest request,
      final ServerStreamObserver<PublishMessageResponse> responseObserver) {

    sendRequest(
        request,
        RequestMapper::toPublishMessageRequest,
        ResponseMapper::toPublishMessageResponse,
        responseObserver);
  }

  public void resolveIncident(
      final ResolveIncidentRequest request,
      final ServerStreamObserver<ResolveIncidentResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toResolveIncidentRequest,
        ResponseMapper::toResolveIncidentResponse,
        responseObserver);
  }

  public void setVariables(
      final SetVariablesRequest request,
      final ServerStreamObserver<SetVariablesResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toSetVariablesRequest,
        ResponseMapper::toSetVariablesResponse,
        responseObserver);
  }

  public void topology(final ServerStreamObserver<TopologyResponse> responseObserver) {
    final TopologyResponse.Builder topologyResponseBuilder = TopologyResponse.newBuilder();
    final BrokerClusterState topology = topologyManager.getTopology();

    final String gatewayVersion = VersionUtil.getVersion();
    if (gatewayVersion != null && !gatewayVersion.isBlank()) {
      topologyResponseBuilder.setGatewayVersion(gatewayVersion);
    }

    final ArrayList<BrokerInfo> brokers = new ArrayList<>();

    if (topology != null) {
      topologyResponseBuilder
          .setClusterSize(topology.getClusterSize())
          .setPartitionsCount(topology.getPartitionsCount())
          .setReplicationFactor(topology.getReplicationFactor());

      topology
          .getBrokers()
          .forEach(
              brokerId -> {
                final Builder brokerInfo = BrokerInfo.newBuilder();
                addBrokerInfo(brokerInfo, brokerId, topology);
                addPartitionInfoToBrokerInfo(brokerInfo, brokerId, topology);

                brokers.add(brokerInfo.build());
              });
    }

    topologyResponseBuilder.addAllBrokers(brokers);
    final TopologyResponse response = topologyResponseBuilder.build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void updateJobRetries(
      final UpdateJobRetriesRequest request,
      final ServerStreamObserver<UpdateJobRetriesResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toUpdateJobRetriesRequest,
        ResponseMapper::toUpdateJobRetriesResponse,
        responseObserver);
  }

  public void updateJobTimeout(
      final UpdateJobTimeoutRequest request,
      final ServerStreamObserver<UpdateJobTimeoutResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toUpdateJobTimeoutRequest,
        ResponseMapper::toUpdateJobTimeoutResponse,
        responseObserver);
  }

  public void modifyProcessInstance(
      final ModifyProcessInstanceRequest request,
      final ServerStreamObserver<ModifyProcessInstanceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toModifyProcessInstanceRequest,
        ResponseMapper::toModifyProcessInstanceResponse,
        responseObserver);
  }

  public void migrateProcessInstance(
      final MigrateProcessInstanceRequest request,
      final ServerStreamObserver<MigrateProcessInstanceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toMigrateProcessInstanceRequest,
        ResponseMapper::toMigrateProcessInstanceResponse,
        responseObserver);
  }

  public void deleteResource(
      final DeleteResourceRequest request,
      final ServerStreamObserver<DeleteResourceResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toDeleteResourceRequest,
        ResponseMapper::toDeleteResourceResponse,
        responseObserver);
  }

  public void broadcastSignal(
      final BroadcastSignalRequest request,
      final ServerStreamObserver<BroadcastSignalResponse> responseObserver) {
    sendRequest(
        request,
        RequestMapper::toBroadcastSignalRequest,
        ResponseMapper::toBroadcastSignalResponse,
        responseObserver);
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequest(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final ServerStreamObserver<GrpcResponseT> streamObserver) {

    final BrokerRequest<BrokerResponseT> brokerRequest;
    try {
      brokerRequest = mapToBrokerRequest(grpcRequest, requestMapper);
    } catch (final Exception e) {
      streamObserver.onError(e);
      return;
    }

    brokerClient.sendRequestWithRetry(
        brokerRequest,
        (key, response) -> consumeResponse(responseMapper, streamObserver, key, response),
        streamObserver::onError);
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequestWithRetryPartitions(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final ServerStreamObserver<GrpcResponseT> streamObserver) {

    final BrokerRequest<BrokerResponseT> brokerRequest;
    try {
      brokerRequest = mapToBrokerRequest(grpcRequest, requestMapper);
    } catch (final Exception e) {
      streamObserver.onError(e);
      return;
    }

    requestRetryHandler.sendRequest(
        brokerRequest,
        (key, response) -> consumeResponse(responseMapper, streamObserver, key, response),
        streamObserver::onError);
  }

  private <GrpcRequestT, BrokerResponseT, GrpcResponseT> void sendRequestWithRetryPartitions(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper,
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final ServerStreamObserver<GrpcResponseT> streamObserver,
      final Duration timeout) {

    final BrokerRequest<BrokerResponseT> brokerRequest;
    try {
      brokerRequest = mapToBrokerRequest(grpcRequest, requestMapper);
    } catch (final Exception e) {
      streamObserver.onError(e);
      return;
    }

    requestRetryHandler.sendRequest(
        brokerRequest,
        (key, response) -> consumeResponse(responseMapper, streamObserver, key, response),
        streamObserver::onError,
        timeout);
  }

  private <GrpcRequestT, BrokerResponseT> BrokerRequest<BrokerResponseT> mapToBrokerRequest(
      final GrpcRequestT grpcRequest,
      final Function<GrpcRequestT, BrokerRequest<BrokerResponseT>> requestMapper)
      throws Exception {

    final BrokerRequest<BrokerResponseT> brokerRequest = requestMapper.apply(grpcRequest);

    final Map<String, Object> claims = new HashMap<>();

    // retrieve the user claims from the context and add them to the authorization if present
    final Map<String, Object> userClaims =
        Context.current().call(AuthenticationHandler.Oidc.USER_CLAIMS::get);
    if (userClaims != null) {
      userClaims.forEach((key, value) -> ClaimTransformer.applyUserClaim(claims, key, value));
    }

    // retrieve the username from the context and add it to the authorization if present
    final String username = Context.current().call(AuthenticationHandler.USERNAME::get);
    if (username != null) {
      claims.put(Authorization.AUTHORIZED_USERNAME, username);
    }

    brokerRequest.setAuthorization(claims);

    return brokerRequest;
  }

  private <BrokerResponseT, GrpcResponseT> void consumeResponse(
      final BrokerResponseMapper<BrokerResponseT, GrpcResponseT> responseMapper,
      final ServerStreamObserver<GrpcResponseT> streamObserver,
      final long key,
      final BrokerResponseT response) {
    final GrpcResponseT grpcResponse = responseMapper.apply(key, response);
    streamObserver.onNext(grpcResponse);
    streamObserver.onCompleted();
  }
}
