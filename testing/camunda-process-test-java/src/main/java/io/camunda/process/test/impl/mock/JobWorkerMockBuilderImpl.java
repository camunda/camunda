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
package io.camunda.process.test.impl.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.mock.VariablesTable;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import io.camunda.process.test.impl.mock.BpmnExampleDataReader.BpmnExampleDataReaderException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerMockBuilderImpl implements JobWorkerMockBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerMockBuilderImpl.class);

  private static final String ACTION_COMPLETE_JOB = "Complete job";
  private static final String ACTION_THROW_BPMN_ERROR = "Throw BPMN Error";

  private final String jobType;
  private final CamundaClient client;
  private final BpmnExampleDataReader exampleDataReader;

  private final BiFunction<String, ActivatedJob, String> logMessagePrefix;

  public JobWorkerMockBuilderImpl(
      final String jobType,
      final CamundaClient client,
      final BpmnExampleDataReader exampleDataReader) {

    this.jobType = jobType;
    this.client = client;
    this.exampleDataReader = exampleDataReader;
    logMessagePrefix =
        (action, job) ->
            String.format("Mock: %s [jobType: %s, jobKey: %s]", action, jobType, job.getKey());
  }

  @Override
  public JobWorkerMock thenComplete() {
    return thenComplete(new HashMap<>());
  }

  @Override
  public JobWorkerMock thenComplete(final Map<String, Object> variables) {
    return withHandler(
        (jobClient, job) -> {
          LOGGER.debug(
              "{} with variables {}", logMessagePrefix.apply(ACTION_COMPLETE_JOB, job), variables);

          jobClient.newCompleteCommand(job).variables(variables).send().join();
        });
  }

  @Override
  public JobWorkerMock thenComplete(
      final Function<Map<String, Object>, Map<String, Object>> variablesSupplier) {
    return withHandler(
        (jobClient, job) -> {
          final Map<String, Object> jobVariables = job.getVariablesAsMap();
          final Map<String, Object> variables = variablesSupplier.apply(jobVariables);

          if (variables == null) {
            throw new IllegalStateException(
                String.format(
                    "%s: expected the variables supplier to return a map of variables, but it "
                        + "returned null.",
                    logMessagePrefix.apply(ACTION_COMPLETE_JOB, job)));
          }

          LOGGER.debug(
              "{} with variables {}", logMessagePrefix.apply(ACTION_COMPLETE_JOB, job), variables);

          jobClient.newCompleteCommand(job).variables(variables).send().join();
        });
  }

  @Override
  public JobWorkerMock thenComplete(final VariablesTable variablesTable) {
    final CamundaAssertJsonMapper assertJsonMapper =
        new CamundaAssertJsonMapper(client.getConfiguration().getJsonMapper());

    final List<NormalizedRow> normalizedRows =
        variablesTable.getRows().stream()
            .map(
                row ->
                    new NormalizedRow(
                        normalize(row.getWhen(), assertJsonMapper), row.getVariables()))
            .collect(Collectors.toList());

    return thenComplete(
        jobVariables -> {
          final Map<String, Object> normalizedJobVariables =
              normalize(jobVariables, assertJsonMapper);

          for (int i = 0; i < normalizedRows.size(); i++) {
            final NormalizedRow row = normalizedRows.get(i);
            if (matches(row.when, normalizedJobVariables)) {
              LOGGER.debug("Mock: Variables table matched row {} for job type '{}'", i, jobType);
              return row.variables;
            }
          }

          LOGGER.debug("Mock: Variables table has no matching row for job type '{}'", jobType);
          return new HashMap<>();
        });
  }

  @Override
  public JobWorkerMock thenCompleteWithExampleData() {
    return withHandler(
        (jobClient, job) -> {
          final String logMessagePrefix = this.logMessagePrefix.apply(ACTION_COMPLETE_JOB, job);

          try {
            final String exampleDataVariables =
                exampleDataReader.readExampleData(
                    job.getProcessDefinitionKey(), job.getBpmnProcessId(), job.getElementId());

            LOGGER.debug("{} with example data {}", logMessagePrefix, exampleDataVariables);
            jobClient.newCompleteCommand(job).variables(exampleDataVariables).send().join();
          } catch (final BpmnExampleDataReaderException e) {

            LOGGER.warn(
                "{} without example data due to errors. {}", logMessagePrefix, e.getMessage());
            jobClient.newCompleteCommand(job).send().join();
          }
        });
  }

  @Override
  public JobWorkerMock thenThrowBpmnError(final String errorCode) {
    return thenThrowBpmnError(errorCode, new HashMap<>());
  }

  @Override
  public JobWorkerMock thenThrowBpmnError(
      final String errorCode, final Map<String, Object> variables) {
    return thenThrowBpmnError(errorCode, null, variables);
  }

  @Override
  public JobWorkerMock thenThrowBpmnError(
      final String errorCode, final String errorMessage, final Map<String, Object> variables) {
    return withHandler(
        (jobClient, job) -> {
          LOGGER.debug(
              "{} with error code {}, error message {} and variables {}",
              logMessagePrefix.apply(ACTION_THROW_BPMN_ERROR, job),
              errorCode,
              errorMessage,
              variables);

          final ThrowErrorCommandStep2 command =
              jobClient.newThrowErrorCommand(job).errorCode(errorCode).variables(variables);

          if (errorMessage != null) {
            command.errorMessage(errorMessage);
          }

          command.send().join();
        });
  }

  @Override
  public JobWorkerMock withHandler(final JobHandler jobHandler) {
    return new JobWorkerMockImpl(jobType, client, jobHandler);
  }

  private static Map<String, Object> normalize(
      final Map<String, Object> when, final CamundaAssertJsonMapper assertJsonMapper) {
    final Map<String, Object> normalized = new LinkedHashMap<>();
    when.forEach((key, value) -> normalized.put(key, assertJsonMapper.toJsonValue(value)));
    return Collections.unmodifiableMap(normalized);
  }

  private static boolean matches(
      final Map<String, Object> normalizedWhen, final Map<String, Object> jobVariables) {
    return normalizedWhen.entrySet().stream()
        .allMatch(
            entry ->
                jobVariables.containsKey(entry.getKey())
                    && Objects.equals(jobVariables.get(entry.getKey()), entry.getValue()));
  }

  private static final class NormalizedRow {

    private final Map<String, Object> when;
    private final Map<String, Object> variables;

    private NormalizedRow(final Map<String, Object> when, final Map<String, Object> variables) {
      this.when = when;
      this.variables = variables;
    }
  }
}
