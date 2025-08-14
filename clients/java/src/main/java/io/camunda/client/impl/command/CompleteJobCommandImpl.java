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
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.enums.JobResultType;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CompleteJobResponseImpl;
import io.camunda.client.impl.response.EmptyApiResponse;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.client.protocol.rest.JobResult.TypeEnum;
import io.camunda.client.protocol.rest.JobResultActivateElement;
import io.camunda.client.protocol.rest.JobResultAdHocSubProcess;
import io.camunda.client.protocol.rest.JobResultCorrections;
import io.camunda.client.protocol.rest.JobResultUserTask;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CompleteJobCommandImpl extends CommandWithVariables<CompleteJobCommandStep1>
    implements CompleteJobCommandStep1, CompleteJobCommandJobResultStep {

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
  public CompleteJobCommandStep1 withResult(
      final Function<CompleteJobCommandJobResultStep, CompleteJobResult> function) {
    final CompleteJobResult result = function.apply(this);
    if (result instanceof CompleteUserTaskJobResultImpl) {
      setJobResult((CompleteUserTaskJobResultImpl) result);
    } else if (result instanceof CompleteAdHocSubProcessResultStep1) {
      setJobResult(((CompleteAdHocSubProcessJobResultImpl) result));
    } else {
      throw new IllegalArgumentException(
          "Unsupported job result type: " + result.getClass().getName());
    }
    return this;
  }

  @Override
  public CompleteUserTaskJobResultImpl forUserTask() {
    return new CompleteUserTaskJobResultImpl();
  }

  @Override
  public CompleteAdHocSubProcessResultStep1 forAdHocSubProcess() {
    return new CompleteAdHocSubProcessJobResultImpl(objectMapper);
  }

  private void setJobResult(final CompleteUserTaskJobResultImpl jobResult) {
    if (useRest) {
      setRestJobResult(jobResult);
    } else {
      setGrpcJobResult(jobResult);
    }
  }

  private void setRestJobResult(final CompleteUserTaskJobResultImpl jobResult) {
    final JobResultUserTask resultRest = new JobResultUserTask();
    final JobResultCorrections correctionsRest = new JobResultCorrections();
    correctionsRest
        .assignee(jobResult.getCorrections().getAssignee())
        .dueDate(jobResult.getCorrections().getDueDate())
        .followUpDate(jobResult.getCorrections().getFollowUpDate())
        .candidateUsers(jobResult.getCorrections().getCandidateUsers())
        .candidateGroups(jobResult.getCorrections().getCandidateGroups())
        .priority(jobResult.getCorrections().getPriority());
    resultRest
        .type(getJobResultTypeEnum(jobResult.getType()))
        .denied(jobResult.isDenied())
        .deniedReason(jobResult.getDeniedReason())
        .corrections(correctionsRest);
    httpRequestObject.setResult(resultRest);
  }

  private void setGrpcJobResult(final CompleteUserTaskJobResultImpl jobResult) {
    final JobResult.Builder resultGrpc = JobResult.newBuilder();
    final GatewayOuterClass.JobResultCorrections.Builder correctionsGrpc =
        GatewayOuterClass.JobResultCorrections.newBuilder();
    if (jobResult.getCorrections().getAssignee() != null) {
      correctionsGrpc.setAssignee(jobResult.getCorrections().getAssignee());
    }
    if (jobResult.getCorrections().getDueDate() != null) {
      correctionsGrpc.setDueDate(jobResult.getCorrections().getDueDate());
    }
    if (jobResult.getCorrections().getFollowUpDate() != null) {
      correctionsGrpc.setFollowUpDate(jobResult.getCorrections().getFollowUpDate());
    }
    if (jobResult.getCorrections().getCandidateUsers() != null) {
      correctionsGrpc.setCandidateUsers(
          StringList.newBuilder().addAllValues(jobResult.getCorrections().getCandidateUsers()));
    }
    if (jobResult.getCorrections().getCandidateGroups() != null) {
      correctionsGrpc.setCandidateGroups(
          StringList.newBuilder().addAllValues(jobResult.getCorrections().getCandidateGroups()));
    }
    if (jobResult.getCorrections().getPriority() != null) {
      correctionsGrpc.setPriority(jobResult.getCorrections().getPriority());
    }
    resultGrpc
        .setType(getJobResultTypeEnum(jobResult.getType()).getValue())
        .setDenied(jobResult.isDenied())
        .setDeniedReason(jobResult.getDeniedReason() == null ? "" : jobResult.getDeniedReason())
        .setCorrections(correctionsGrpc);
    grpcRequestObjectBuilder.setResult(resultGrpc);
  }

  private TypeEnum getJobResultTypeEnum(final JobResultType type) {
    switch (type) {
      case USER_TASK:
        return TypeEnum.USER_TASK;
      case AD_HOC_SUB_PROCESS:
        return TypeEnum.AD_HOC_SUB_PROCESS;
      default:
        throw new IllegalArgumentException("Unsupported job result type: " + type);
    }
  }

  private void setJobResult(final CompleteAdHocSubProcessJobResultImpl jobResult) {
    if (useRest) {
      setRestJobResult(jobResult);
    } else {
      setGrpcJobResult(jobResult);
    }
  }

  private void setRestJobResult(final CompleteAdHocSubProcessJobResultImpl jobResult) {
    final JobResultAdHocSubProcess resultRest = new JobResultAdHocSubProcess();
    final List<JobResultActivateElement> activateElements =
        jobResult.getActivateElements().stream()
            .map(
                element -> {
                  final JobResultActivateElement activateElement =
                      new JobResultActivateElement().elementId(element.getElementId());
                  if (element.getVariables() != null) {
                    activateElement.setVariables(jsonMapper.fromJsonAsMap(element.getVariables()));
                  }
                  return activateElement;
                })
            .collect(Collectors.toList());
    resultRest
        .type(getJobResultTypeEnum(jobResult.getType()))
        .activateElements(activateElements)
        .isCompletionConditionFulfilled(jobResult.isCompletionConditionFulfilled())
        .isCancelRemainingInstances(jobResult.isCancelRemainingInstances());
    httpRequestObject.setResult(resultRest);
  }

  private void setGrpcJobResult(final CompleteAdHocSubProcessJobResultImpl jobResult) {
    final JobResult.Builder resultGrpc = JobResult.newBuilder();
    resultGrpc
        .setType(getJobResultTypeEnum(jobResult.getType()).getValue())
        .setIsCompletionConditionFulfilled(jobResult.isCompletionConditionFulfilled())
        .setIsCancelRemainingInstances(jobResult.isCancelRemainingInstances());
    jobResult.getActivateElements().stream()
        .map(
            element -> {
              final GatewayOuterClass.JobResultActivateElement.Builder activateElement =
                  GatewayOuterClass.JobResultActivateElement.newBuilder()
                      .setElementId(element.getElementId());
              if (element.getVariables() != null) {
                activateElement.setVariables(element.getVariables());
              }
              return activateElement.build();
            })
        .forEach(resultGrpc::addActivateElements);
    grpcRequestObjectBuilder.setResult(resultGrpc);
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
        r -> new EmptyApiResponse(),
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
