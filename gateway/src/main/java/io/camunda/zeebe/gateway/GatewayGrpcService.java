/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.grpc.ErrorMappingStreamObserver;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
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
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceResponse;
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
import io.grpc.stub.StreamObserver;

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
  public void evaluateDecision(
      final EvaluateDecisionRequest request,
      final StreamObserver<EvaluateDecisionResponse> responseObserver) {
    endpointManager.evaluateDecision(
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
  public void deployResource(
      final DeployResourceRequest request,
      final StreamObserver<DeployResourceResponse> responseObserver) {
    endpointManager.deployResource(
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

  @Override
  public void modifyProcessInstance(
      final ModifyProcessInstanceRequest request,
      final StreamObserver<ModifyProcessInstanceResponse> responseObserver) {
    endpointManager.modifyProcessInstance(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }

  @Override
  public void deleteResource(
      final DeleteResourceRequest request,
      final StreamObserver<DeleteResourceResponse> responseObserver) {
    endpointManager.deleteResource(
        request, ErrorMappingStreamObserver.ofStreamObserver(responseObserver));
  }
}
