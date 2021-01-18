/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.grpc.ErrorMappingStreamObserver;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
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
  public void cancelWorkflowInstance(
      final CancelWorkflowInstanceRequest request,
      final StreamObserver<CancelWorkflowInstanceResponse> responseObserver) {
    endpointManager.cancelWorkflowInstance(
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
  public void createWorkflowInstance(
      final CreateWorkflowInstanceRequest request,
      final StreamObserver<CreateWorkflowInstanceResponse> responseObserver) {
    endpointManager.createWorkflowInstance(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void createWorkflowInstanceWithResult(
      final CreateWorkflowInstanceWithResultRequest request,
      final StreamObserver<CreateWorkflowInstanceWithResultResponse> responseObserver) {
    endpointManager.createWorkflowInstanceWithResult(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void deployWorkflow(
      final DeployWorkflowRequest request,
      final StreamObserver<DeployWorkflowResponse> responseObserver) {
    endpointManager.deployWorkflow(
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
