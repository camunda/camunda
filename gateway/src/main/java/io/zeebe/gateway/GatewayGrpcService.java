/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.grpc.ErrorMappingStreamObserver;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
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

public class GatewayGrpcService extends GatewayImplBase {
  private final EndpointManager endpointManager;

  public GatewayGrpcService(final EndpointManager endpointManager) {
    this.endpointManager = endpointManager;
  }

  @Override
  public void activateJobs(
      final ActivateJobsRequest request,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    endpointManager.activateJobs(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void cancelProcessInstance(
      final CancelProcessInstanceRequest request,
      final StreamObserver<CancelProcessInstanceResponse> responseObserver) {
    endpointManager.cancelProcessInstance(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void completeJob(
      final CompleteJobRequest request,
      final StreamObserver<CompleteJobResponse> responseObserver) {
    endpointManager.completeJob(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void createProcessInstance(
      final CreateProcessInstanceRequest request,
      final StreamObserver<CreateProcessInstanceResponse> responseObserver) {
    endpointManager.createProcessInstance(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void createProcessInstanceWithResult(
      final CreateProcessInstanceWithResultRequest request,
      final StreamObserver<CreateProcessInstanceWithResultResponse> responseObserver) {
    endpointManager.createProcessInstanceWithResult(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void deployProcess(
      final DeployProcessRequest request,
      final StreamObserver<DeployProcessResponse> responseObserver) {
    endpointManager.deployProcess(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void failJob(
      final FailJobRequest request, final StreamObserver<FailJobResponse> responseObserver) {
    endpointManager.failJob(request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void throwError(
      final ThrowErrorRequest request, final StreamObserver<ThrowErrorResponse> responseObserver) {
    endpointManager.throwError(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void publishMessage(
      final PublishMessageRequest request,
      final StreamObserver<PublishMessageResponse> responseObserver) {
    endpointManager.publishMessage(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void resolveIncident(
      final ResolveIncidentRequest request,
      final StreamObserver<ResolveIncidentResponse> responseObserver) {
    endpointManager.resolveIncident(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void setVariables(
      final SetVariablesRequest request,
      final StreamObserver<SetVariablesResponse> responseObserver) {
    endpointManager.setVariables(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void topology(
      final TopologyRequest request, final StreamObserver<TopologyResponse> responseObserver) {
    endpointManager.topology(ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void updateJobRetries(
      final UpdateJobRetriesRequest request,
      final StreamObserver<UpdateJobRetriesResponse> responseObserver) {
    endpointManager.updateJobRetries(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }
}
