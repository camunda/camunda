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
package io.camunda.client.impl.command;

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandStep2;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.JobResultCorrections;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CompleteJobResponseImpl;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StringList;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CompleteJobCommandImpl extends CommandWithVariables<CompleteJobCommandStep1>
    implements CompleteJobCommandStep1, CompleteJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final JobCompletionRequest httpRequestObject;
  private boolean useRest;
  private final long jobKey;
  private final JsonMapper jsonMapper;
  private JobResult.Builder resultGrpc;
  private io.camunda.client.protocol.rest.JobResult resultRest;
  private io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResultCorrections.Builder
      correctionsGrpc;
  private io.camunda.client.protocol.rest.JobResultCorrections correctionsRest;

  public CompleteJobCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final long key,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = CompleteJobRequest.newBuilder();
    grpcRequestObjectBuilder.setJobKey(key);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobCompletionRequest();
    useRest = preferRestOverGrpc;
    jobKey = key;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public FinalCommandStep<CompleteJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CompleteJobResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  @Override
  public CompleteJobCommandStep2 withResult() {
    initJobResult();
    return this;
  }

  @Override
  public CompleteJobCommandStep1 withResult(final CompleteJobResult jobResult) {
    return withResult()
        .deny(jobResult.isDenied())
        .deniedReason(jobResult.getDeniedReason())
        .correct(jobResult.getCorrections())
        .resultDone();
  }

  @Override
  public CompleteJobCommandStep1 withResult(
      final UnaryOperator<CompleteJobResult> jobResultModifier) {
    initJobResult();
    final CompleteJobResult reconstructedJobResult =
        new CompleteJobResult()
            .deny(resultRest.getDenied() != null ? resultRest.getDenied() : false)
            .deniedReason(resultRest.getDeniedReason())
            .correct(reconstructCorrections());
    return withResult(jobResultModifier.apply(reconstructedJobResult));
  }

  private void initJobResult() {
    resultRest = new io.camunda.client.protocol.rest.JobResult();
    correctionsRest = new io.camunda.client.protocol.rest.JobResultCorrections();
    resultRest.setCorrections(correctionsRest);
    httpRequestObject.setResult(resultRest);

    resultGrpc = JobResult.newBuilder();
    correctionsGrpc = GatewayOuterClass.JobResultCorrections.newBuilder();
    resultGrpc.setCorrections(correctionsGrpc);
    grpcRequestObjectBuilder.setResult(resultGrpc);
  }

  @Override
  public CompleteJobCommandStep2 deny(final boolean isDenied) {
    resultRest.setDenied(isDenied);
    resultGrpc.setDenied(isDenied);
    onResultChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 deny(final boolean isDenied, final String deniedReason) {
    return deny(isDenied).deniedReason(deniedReason);
  }

  @Override
  public CompleteJobCommandStep2 deniedReason(final String deniedReason) {
    resultRest.setDeniedReason(deniedReason);
    resultGrpc.setDeniedReason(deniedReason == null ? "" : deniedReason);
    onResultChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 correct(final JobResultCorrections corrections) {
    return correctAssignee(corrections.getAssignee())
        .correctCandidateGroups(corrections.getCandidateGroups())
        .correctCandidateUsers(corrections.getCandidateUsers())
        .correctDueDate(corrections.getDueDate())
        .correctFollowUpDate(corrections.getFollowUpDate())
        .correctPriority(corrections.getPriority());
  }

  @Override
  public CompleteJobCommandStep2 correct(final UnaryOperator<JobResultCorrections> corrections) {
    final JobResultCorrections reconstructedCorrections = reconstructCorrections();
    return correct(corrections.apply(reconstructedCorrections));
  }

  @Override
  public CompleteJobCommandStep2 correctAssignee(final String assignee) {
    correctionsRest.setAssignee(assignee);
    if (assignee == null) {
      correctionsGrpc.clearAssignee();
    } else {
      correctionsGrpc.setAssignee(assignee);
    }
    onCorrectionsChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 correctDueDate(final String dueDate) {
    correctionsRest.setDueDate(dueDate);
    if (dueDate == null) {
      correctionsGrpc.clearDueDate();
    } else {
      correctionsGrpc.setDueDate(dueDate);
    }
    onCorrectionsChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 correctFollowUpDate(final String followUpDate) {
    correctionsRest.setFollowUpDate(followUpDate);
    if (followUpDate == null) {
      correctionsGrpc.clearFollowUpDate();
    } else {
      correctionsGrpc.setFollowUpDate(followUpDate);
    }
    onCorrectionsChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 correctCandidateUsers(final List<String> candidateUsers) {
    correctionsRest.setCandidateUsers(candidateUsers);
    if (candidateUsers == null) {
      correctionsGrpc.clearCandidateUsers();
    } else {
      correctionsGrpc.setCandidateUsers(
          StringList.newBuilder().addAllValues(candidateUsers).build());
    }
    onCorrectionsChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 correctCandidateGroups(final List<String> candidateGroups) {
    correctionsRest.setCandidateGroups(candidateGroups);
    if (candidateGroups == null) {
      correctionsGrpc.clearCandidateGroups();
    } else {
      correctionsGrpc.setCandidateGroups(
          StringList.newBuilder().addAllValues(candidateGroups).build());
    }
    onCorrectionsChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep2 correctPriority(final Integer priority) {
    correctionsRest.setPriority(priority);
    if (priority == null) {
      correctionsGrpc.clearPriority();
    } else {
      correctionsGrpc.setPriority(priority);
    }
    onCorrectionsChange();
    return this;
  }

  @Override
  public CompleteJobCommandStep1 resultDone() {
    return this;
  }

  private JobResultCorrections reconstructCorrections() {
    return new JobResultCorrections()
        .assignee(correctionsRest.getAssignee())
        .candidateGroups(correctionsRest.getCandidateGroups())
        .candidateUsers(correctionsRest.getCandidateUsers())
        .dueDate(correctionsRest.getDueDate())
        .followUpDate(correctionsRest.getFollowUpDate())
        .priority(correctionsRest.getPriority());
  }

  private void onResultChange() {
    // grpcRequestObjectBuilder.setResult() makes immutable copy of passed value so we need to
    // refresh it everytime when we need to set another jobResult property
    grpcRequestObjectBuilder.setResult(resultGrpc);
  }

  private void onCorrectionsChange() {
    // resultGrpc.setCorrections() makes immutable copy of passed value so we need to
    // refresh it everytime when we need to set another correctionsGrpc property
    resultGrpc.setCorrections(correctionsGrpc);
    onResultChange();
  }

  @Override
  public CompleteJobCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public CompleteJobCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }

  private CamundaFuture<CompleteJobResponse> sendRestRequest() {
    final HttpCamundaFuture<CompleteJobResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/jobs/" + jobKey + "/completion",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        result);
    return result;
  }

  private CamundaFuture<CompleteJobResponse> sendGrpcRequest() {
    final CompleteJobRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<CompleteJobResponse, GatewayOuterClass.CompleteJobResponse>
        future =
            new RetriableClientFutureImpl<>(
                CompleteJobResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final CompleteJobRequest request,
      final StreamObserver<GatewayOuterClass.CompleteJobResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .completeJob(request, streamObserver);
  }

  @Override
  protected CompleteJobCommandStep1 setVariablesInternal(final String variables) {
    grpcRequestObjectBuilder.setVariables(variables);
    // This check is mandatory. Without it, gRPC requests can fail unnecessarily.
    // gRPC and REST handle setting variables differently:
    // - For gRPC commands, we only check if the JSON is valid and forward it to the engine.
    //    The engine checks if the provided String can be transformed into a Map, if not it
    //    throws an error.
    // - For REST commands, users have to provide a valid JSON Object String.
    //    Otherwise, the client throws an exception already.
    if (useRest) {
      httpRequestObject.setVariables(jsonMapper.fromJsonAsMap(variables));
    }
    return this;
  }
}
