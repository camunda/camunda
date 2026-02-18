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
package io.camunda.client.spring.actuator;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.command.CompleteAdHocSubProcessJobResultImpl;
import io.camunda.client.impl.command.CompleteUserTaskJobResultImpl;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.client.protocol.rest.JobResult;
import io.camunda.client.protocol.rest.JobResultActivateElement;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface CommandCaptor {

  public class CapturedFuture<RespT> extends CompletableFuture<RespT>
      implements CamundaFuture<RespT> {
    private final Supplier<RespT> responseSupplier;

    public CapturedFuture(final Supplier<RespT> responseSupplier) {
      this.responseSupplier = responseSupplier;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
      return true;
    }

    @Override
    public RespT join(final long timeout, final TimeUnit unit) {
      return responseSupplier.get();
    }

    @Override
    public RespT join() {
      return join(0L, null);
    }
  }

  public abstract class CommandWithVariablesCaptor<T> {

    protected final JsonMapper objectMapper;

    public CommandWithVariablesCaptor(final JsonMapper jsonMapper) {
      objectMapper = jsonMapper;
    }

    public T variables(final InputStream variables) {
      ArgumentUtil.ensureNotNull("variables", variables);
      return setVariablesInternal(objectMapper.validateJson("variables", variables));
    }

    public T variables(final String variables) {
      ArgumentUtil.ensureNotNull("variables", variables);
      return setVariablesInternal(objectMapper.validateJson("variables", variables));
    }

    public T variables(final Map<String, Object> variables) {
      ArgumentUtil.ensureNotNull("variables", variables);
      return variables((Object) variables);
    }

    public T variables(final Object variables) {
      ArgumentUtil.ensureNotNull("variables", variables);
      return setVariablesInternal(objectMapper.toJson(variables));
    }

    public T variable(final String key, final Object value) {
      ArgumentUtil.ensureNotNull("key", key);
      return variables(Collections.singletonMap(key, value));
    }

    protected abstract T setVariablesInternal(String variables);
  }

  public final class CompleteJobCommandCaptor
      extends CommandWithVariablesCaptor<CompleteJobCommandStep1>
      implements CompleteJobCommandStep1,
          CompleteJobCommandJobResultStep,
          CompleteJobResponse,
          CommandCaptor {

    private final JobCompletionRequest httpRequestObject;
    private final long jobKey;
    private final JsonMapper jsonMapper;
    private final Consumer<CapturedCommand> capturedCommandCallback;

    public CompleteJobCommandCaptor(
        final JsonMapper jsonMapper,
        final long key,
        final Consumer<CapturedCommand> capturedCommandCallback) {
      super(jsonMapper);
      httpRequestObject = new JobCompletionRequest();
      jobKey = key;
      this.jsonMapper = jsonMapper;
      this.capturedCommandCallback = capturedCommandCallback;
    }

    @Override
    public FinalCommandStep<CompleteJobResponse> requestTimeout(final Duration requestTimeout) {
      return this;
    }

    @Override
    public CamundaFuture<CompleteJobResponse> send() {
      capturedCommandCallback.accept(createCapturedCommand());
      return new CapturedFuture<>(() -> this);
    }

    private CapturedCommand createCapturedCommand() {
      return new CapturedCommand(
          "completeJobCommand",
          Map.of("jobKey", String.valueOf(jobKey)),
          Map.of(),
          httpRequestObject);
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
      setRestJobResult(jobResult);
    }

    private void setRestJobResult(final CompleteUserTaskJobResultImpl jobResult) {
      final JobResult resultRest = new JobResult();
      final io.camunda.client.protocol.rest.JobResultCorrections correctionsRest =
          new io.camunda.client.protocol.rest.JobResultCorrections();
      correctionsRest
          .assignee(jobResult.getCorrections().getAssignee())
          .dueDate(jobResult.getCorrections().getDueDate())
          .followUpDate(jobResult.getCorrections().getFollowUpDate())
          .candidateUsers(jobResult.getCorrections().getCandidateUsers())
          .candidateGroups(jobResult.getCorrections().getCandidateGroups())
          .priority(jobResult.getCorrections().getPriority());
      resultRest
          .type(jobResult.getType().getProtocolValue())
          .denied(jobResult.isDenied())
          .deniedReason(jobResult.getDeniedReason())
          .corrections(correctionsRest)
          // null values as they are not applicable for user task completion
          .isCompletionConditionFulfilled(null)
          .isCancelRemainingInstances(null)
          .activateElements(null);
      httpRequestObject.setResult(resultRest);
    }

    private void setJobResult(final CompleteAdHocSubProcessJobResultImpl jobResult) {
      setRestJobResult(jobResult);
    }

    private void setRestJobResult(final CompleteAdHocSubProcessJobResultImpl jobResult) {
      final JobResult resultRest = new JobResult();
      final List<JobResultActivateElement> activateElements =
          jobResult.getActivateElements().stream()
              .map(
                  element -> {
                    final JobResultActivateElement activateElement =
                        new JobResultActivateElement().elementId(element.getElementId());
                    if (element.getVariables() != null) {
                      activateElement.setVariables(
                          jsonMapper.fromJsonAsMap(element.getVariables()));
                    }
                    return activateElement;
                  })
              .collect(Collectors.toList());
      resultRest
          .type(jobResult.getType().getProtocolValue())
          .activateElements(activateElements)
          .isCompletionConditionFulfilled(jobResult.isCompletionConditionFulfilled())
          .isCancelRemainingInstances(jobResult.isCancelRemainingInstances())
          // null values as they are not applicable for ad-hoc sub process completion
          .denied(null)
          .corrections(null)
          .deniedReason(null);
      httpRequestObject.setResult(resultRest);
    }

    @Override
    public CompleteJobCommandStep1 useRest() {
      return this;
    }

    @Override
    public CompleteJobCommandStep1 useGrpc() {
      return this;
    }

    @Override
    protected CompleteJobCommandStep1 setVariablesInternal(final String variables) {
      httpRequestObject.setVariables(jsonMapper.fromJsonAsMap(variables));
      return this;
    }
  }
}
