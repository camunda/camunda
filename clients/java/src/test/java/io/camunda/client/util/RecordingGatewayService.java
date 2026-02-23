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
package io.camunda.client.util;

import com.google.protobuf.GeneratedMessage;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionRequirementsMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Deployment;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateConditionalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateConditionalResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FormMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerHealth;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessInstanceReference;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;

public final class RecordingGatewayService extends GatewayImplBase {

  private final BlockingDeque<GeneratedMessage> requests = new LinkedBlockingDeque<>();

  private final Map<Class<? extends GeneratedMessage>, RequestHandler> requestHandlers =
      new HashMap<>();
  private final Map<Class<? extends GeneratedMessage>, Supplier<Throwable>> errorHandlers =
      new HashMap<>();

  public RecordingGatewayService() {
    addRequestHandler(TopologyRequest.class, r -> TopologyResponse.getDefaultInstance());
    addRequestHandler(DeployProcessRequest.class, r -> DeployProcessResponse.getDefaultInstance());
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
    addRequestHandler(
        UpdateJobTimeoutRequest.class, r -> UpdateJobTimeoutResponse.getDefaultInstance());
    addRequestHandler(FailJobRequest.class, r -> FailJobResponse.getDefaultInstance());
    addRequestHandler(ThrowErrorRequest.class, r -> ThrowErrorResponse.getDefaultInstance());
    addRequestHandler(CompleteJobRequest.class, r -> CompleteJobResponse.getDefaultInstance());
    addRequestHandler(ActivateJobsRequest.class, r -> ActivateJobsResponse.getDefaultInstance());
    addRequestHandler(
        ResolveIncidentRequest.class, r -> ResolveIncidentResponse.getDefaultInstance());
    addRequestHandler(
        ModifyProcessInstanceRequest.class,
        r -> ModifyProcessInstanceResponse.getDefaultInstance());
    addRequestHandler(
        MigrateProcessInstanceRequest.class,
        r -> MigrateProcessInstanceResponse.getDefaultInstance());
    addRequestHandler(
        EvaluateDecisionRequest.class, r -> EvaluateDecisionResponse.getDefaultInstance());
    addRequestHandler(
        DeleteResourceRequest.class, r -> DeleteResourceResponse.getDefaultInstance());
    addRequestHandler(
        BroadcastSignalRequest.class, r -> BroadcastSignalResponse.getDefaultInstance());
    addRequestHandler(StreamActivatedJobsRequest.class, r -> ActivatedJob.getDefaultInstance());
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

  public static Deployment deployment(final ProcessMetadata metadata) {
    return Deployment.newBuilder().setProcess(metadata).build();
  }

  public static Deployment deployment(final DecisionMetadata metadata) {
    return Deployment.newBuilder().setDecision(metadata).build();
  }

  public static Deployment deployment(final DecisionRequirementsMetadata metadata) {
    return Deployment.newBuilder().setDecisionRequirements(metadata).build();
  }

  public static Deployment deployment(final FormMetadata metadata) {
    return Deployment.newBuilder().setForm(metadata).build();
  }

  public static ProcessMetadata deployedProcess(
      final String bpmnProcessId,
      final int version,
      final long processDefinitionKey,
      final String resourceName) {
    return deployedProcess(
        bpmnProcessId,
        version,
        processDefinitionKey,
        resourceName,
        CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  public static ProcessMetadata deployedProcess(
      final String bpmnProcessId,
      final int version,
      final long processDefinitionKey,
      final String resourceName,
      final String tenantId) {
    return ProcessMetadata.newBuilder()
        .setBpmnProcessId(bpmnProcessId)
        .setVersion(version)
        .setProcessDefinitionKey(processDefinitionKey)
        .setResourceName(resourceName)
        .setTenantId(tenantId)
        .build();
  }

  public static DecisionMetadata deployedDecision(
      final String dmnDecisionId,
      final String dmnDecisionName,
      final int version,
      final long decisionKey,
      final String dmnDecisionRequirementsId,
      final long decisionRequirementsKey) {
    return deployedDecision(
        dmnDecisionId,
        dmnDecisionName,
        version,
        decisionKey,
        dmnDecisionRequirementsId,
        decisionRequirementsKey,
        CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  public static DecisionMetadata deployedDecision(
      final String dmnDecisionId,
      final String dmnDecisionName,
      final int version,
      final long decisionKey,
      final String dmnDecisionRequirementsId,
      final long decisionRequirementsKey,
      final String tenantId) {
    return DecisionMetadata.newBuilder()
        .setDmnDecisionId(dmnDecisionId)
        .setDmnDecisionName(dmnDecisionName)
        .setVersion(version)
        .setDecisionKey(decisionKey)
        .setDmnDecisionRequirementsId(dmnDecisionRequirementsId)
        .setDecisionRequirementsKey(decisionRequirementsKey)
        .setTenantId(tenantId)
        .build();
  }

  public static DecisionRequirementsMetadata deployedDecisionRequirements(
      final String dmnDecisionRequirementsId,
      final String dmnDecisionRequirementsName,
      final int version,
      final long decisionRequirementsKey,
      final String resourceName) {
    return deployedDecisionRequirements(
        dmnDecisionRequirementsId,
        dmnDecisionRequirementsName,
        version,
        decisionRequirementsKey,
        resourceName,
        CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  public static DecisionRequirementsMetadata deployedDecisionRequirements(
      final String dmnDecisionRequirementsId,
      final String dmnDecisionRequirementsName,
      final int version,
      final long decisionRequirementsKey,
      final String resourceName,
      final String tenantId) {
    return DecisionRequirementsMetadata.newBuilder()
        .setDmnDecisionRequirementsId(dmnDecisionRequirementsId)
        .setDmnDecisionRequirementsName(dmnDecisionRequirementsName)
        .setVersion(version)
        .setDecisionRequirementsKey(decisionRequirementsKey)
        .setResourceName(resourceName)
        .setTenantId(tenantId)
        .build();
  }

  public static FormMetadata deployedForm(
      final String formId,
      final int version,
      final long formKey,
      final String resourceName,
      final String tenantId) {
    return FormMetadata.newBuilder()
        .setFormId(formId)
        .setVersion(version)
        .setFormKey(formKey)
        .setResourceName(resourceName)
        .setTenantId(tenantId)
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
  public void activateJobs(
      final ActivateJobsRequest request,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void streamActivatedJobs(
      final StreamActivatedJobsRequest request,
      final StreamObserver<ActivatedJob> responseObserver) {
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
  public void evaluateDecision(
      final EvaluateDecisionRequest request,
      final StreamObserver<EvaluateDecisionResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void deployProcess(
      final DeployProcessRequest request,
      final StreamObserver<DeployProcessResponse> responseObserver) {
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

  @Override
  public void modifyProcessInstance(
      final ModifyProcessInstanceRequest request,
      final StreamObserver<ModifyProcessInstanceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void migrateProcessInstance(
      final MigrateProcessInstanceRequest request,
      final StreamObserver<MigrateProcessInstanceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void updateJobTimeout(
      final UpdateJobTimeoutRequest request,
      final StreamObserver<UpdateJobTimeoutResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void deleteResource(
      final DeleteResourceRequest request,
      final StreamObserver<DeleteResourceResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void broadcastSignal(
      final BroadcastSignalRequest request,
      final StreamObserver<BroadcastSignalResponse> responseObserver) {
    handle(request, responseObserver);
  }

  @Override
  public void evaluateConditional(
      final EvaluateConditionalRequest request,
      final StreamObserver<EvaluateConditionalResponse> responseObserver) {
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

  public void onDeployProcessRequest(final long key, final ProcessMetadata... processes) {
    addRequestHandler(
        DeployProcessRequest.class,
        request ->
            DeployProcessResponse.newBuilder()
                .setKey(key)
                .addAllProcesses(Arrays.asList(processes))
                .build());
  }

  public void onDeployResourceRequest(
      final long key, final String tenantId, final Deployment... deployments) {
    addRequestHandler(
        DeployResourceRequest.class,
        request ->
            DeployResourceResponse.newBuilder()
                .setKey(key)
                .setTenantId(tenantId)
                .addAllDeployments(Arrays.asList(deployments))
                .build());
  }

  public void onCreateProcessInstanceRequest(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final long processInstanceKey) {
    onCreateProcessInstanceRequest(
        processDefinitionKey, bpmnProcessId, version, processInstanceKey, new HashSet<>());
  }

  public void onCreateProcessInstanceRequest(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final long processInstanceKey,
      final Set<String> tags) {
    onCreateProcessInstanceRequest(
        processDefinitionKey, bpmnProcessId, version, processInstanceKey, tags, "");
  }

  public void onCreateProcessInstanceRequest(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final long processInstanceKey,
      final Set<String> tags,
      final String businessId) {
    addRequestHandler(
        CreateProcessInstanceRequest.class,
        request ->
            CreateProcessInstanceResponse.newBuilder()
                .setProcessDefinitionKey(processDefinitionKey)
                .setBpmnProcessId(bpmnProcessId)
                .setVersion(version)
                .setProcessInstanceKey(processInstanceKey)
                .addAllTags(tags)
                .setBusinessId(businessId)
                .build());
  }

  public void onEvaluateDecisionRequest(final EvaluateDecisionResponse evaluateDecisionResponse) {
    addRequestHandler(EvaluateDecisionRequest.class, request -> evaluateDecisionResponse);
  }

  public void onPublishMessageRequest(final long key) {
    addRequestHandler(
        PublishMessageRequest.class,
        request ->
            PublishMessageResponse.newBuilder()
                .setKey(key)
                .setTenantId(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER)
                .build());
  }

  public void onBroadcastSignalRequest(final long key) {
    addRequestHandler(
        BroadcastSignalRequest.class,
        request ->
            BroadcastSignalResponse.newBuilder()
                .setKey(key)
                .setTenantId(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER)
                .build());
  }

  public void onEvaluateConditionalRequest(
      final long processDefinitionKey, final long processInstanceKey) {
    onEvaluateConditionalRequest(
        12345L,
        CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER,
        processDefinitionKey,
        processInstanceKey);
  }

  public void onEvaluateConditionalRequest(
      final long conditionalEvaluationKey,
      final String tenantId,
      final long processDefinitionKey,
      final long processInstanceKey) {
    addRequestHandler(
        EvaluateConditionalRequest.class,
        request ->
            EvaluateConditionalResponse.newBuilder()
                .setConditionalEvaluationKey(conditionalEvaluationKey)
                .setTenantId(tenantId)
                .addProcessInstances(
                    ProcessInstanceReference.newBuilder()
                        .setProcessDefinitionKey(processDefinitionKey)
                        .setProcessInstanceKey(processInstanceKey)
                        .build())
                .build());
  }

  public void onEvaluateConditionalRequest() {
    addRequestHandler(
        EvaluateConditionalRequest.class,
        request ->
            EvaluateConditionalResponse.newBuilder()
                .setConditionalEvaluationKey(12345L)
                .setTenantId(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER)
                .build());
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

  public void onStreamJobsRequest(final Throwable error) {
    addRequestHandler(StreamActivatedJobsRequest.class, () -> error);
  }

  public void onStreamJobsRequest(final ActivatedJob... activatedJobs) {
    final List<ActivatedJob> jobs = Arrays.asList(activatedJobs);
    addRequestHandler(
        StreamActivatedJobsRequest.class,
        new RequestHandler<StreamActivatedJobsRequest, ActivatedJob>() {
          @Override
          public ActivatedJob handle(final StreamActivatedJobsRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Collection<ActivatedJob> handleMultiple(final StreamActivatedJobsRequest request) {
            return jobs;
          }
        });
  }

  public void onSetVariablesRequest(final long key) {
    addRequestHandler(
        SetVariablesRequest.class,
        request -> SetVariablesResponse.newBuilder().setKey(key).build());
  }

  public void onDeleteResourceRequest(final long resourceKey) {
    addRequestHandler(
        DeleteResourceRequest.class,
        request ->
            DeleteResourceResponse.newBuilder()
                .setResourceKey(String.valueOf(resourceKey))
                .build());
  }

  public void onDeleteResourceRequest(
      final long resourceKey, final String batchOperationKey, final int batchOperationType) {
    addRequestHandler(
        DeleteResourceRequest.class,
        request ->
            DeleteResourceResponse.newBuilder()
                .setResourceKey(String.valueOf(resourceKey))
                .setBatchOperation(
                    io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BatchOperationCreatedResult
                        .newBuilder()
                        .setBatchOperationKey(batchOperationKey)
                        .setBatchOperationTypeValue(batchOperationType)
                        .build())
                .build());
  }

  public void errorOnRequest(
      final Class<? extends GeneratedMessage> requestClass,
      final Supplier<Exception> errorSupplier) {
    addRequestHandler(
        requestClass,
        request -> {
          throw errorSupplier.get();
        });
  }

  @SuppressWarnings("unchecked")
  public <T extends GeneratedMessage> T getLastRequest() {
    return (T) requests.getLast();
  }

  public <T extends GeneratedMessage> void addRequestHandler(
      final Class<T> requestClass,
      final RequestHandler<T, ? extends GeneratedMessage> requestHandler) {
    errorHandlers.remove(requestClass);
    requestHandlers.put(requestClass, requestHandler);
  }

  public void addRequestHandler(
      final Class<? extends GeneratedMessage> requestClass, final Supplier<Throwable> thrower) {
    requestHandlers.remove(requestClass);
    errorHandlers.put(requestClass, thrower);
  }

  @SuppressWarnings("unchecked")
  private <RequestT extends GeneratedMessage, ResponseT extends GeneratedMessage> void handle(
      final RequestT request, final StreamObserver<ResponseT> responseObserver) {
    requests.add(request);
    try {
      final Class<? extends GeneratedMessage> requestType = request.getClass();
      final RequestHandler<RequestT, ResponseT> requestHandler = requestHandlers.get(requestType);
      if (requestHandler != null) {
        requestHandler.handleMultiple(request).forEach(responseObserver::onNext);
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
  interface RequestHandler<RequestT extends GeneratedMessage, ResponseT extends GeneratedMessage> {
    ResponseT handle(RequestT request) throws Exception;

    default Collection<ResponseT> handleMultiple(final RequestT request) throws Exception {
      return Collections.singleton(handle(request));
    }
  }
}
