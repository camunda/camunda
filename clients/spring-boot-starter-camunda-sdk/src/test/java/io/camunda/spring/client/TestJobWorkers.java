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
package io.camunda.spring.client;

import static java.util.function.Predicate.not;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.AnnotationUtil;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.annotation.Variable;
import io.camunda.spring.client.annotation.VariablesAsType;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.exception.CamundaError;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class TestJobWorkers {

  @Test
  void shouldGenerateAllRequiredJobWorkers() {
    generateTestDimensions().forEach(TestJobWorkers::getMethod);
  }

  @Test
  void printMethods() {
    generateTestDimensions().stream()
        .map(TestJobWorkers::generateMethod)
        .forEach(System.out::println);
  }

  public static JobWorkerValue jobWorkerValue(final TestDimension testDimension) {
    return AnnotationUtil.getJobWorkerValue(TestJobWorkers.getMethodInfo(testDimension)).get();
  }

  public static MethodInfo getMethodInfo(final TestDimension td) {
    final Method method = TestJobWorkers.getMethod(td);
    return MethodInfo.builder()
        .classInfo(ClassInfo.builder().bean(new TestJobWorkers()).beanName("testBean").build())
        .method(method)
        .build();
  }

  public static Method getMethod(final TestDimension td) {
    final String methodName = generateMethodName(td);
    final Class<?>[] parameterTypes = generateParameterTypes(td);
    try {
      return TestJobWorkers.class.getDeclaredMethod(methodName, parameterTypes);
    } catch (final NoSuchMethodException e) {
      throw new RuntimeException(
          String.format(
              "Did not find method with name '%s' and parameter types [%s]",
              methodName,
              Arrays.stream(parameterTypes).map(Class::getName).collect(Collectors.joining(", "))),
          e);
    }
  }

  private static Class<?>[] generateParameterTypes(final TestDimension td) {
    final List<Parameter> parameters = new ArrayList<>(td.parameters());
    parameters.sort(Enum::compareTo);
    final List<Class<?>> parameterTypes = new ArrayList<>();
    for (final Parameter parameter : parameters) {
      switch (parameter) {
        case JOB -> parameterTypes.add(ActivatedJob.class);
        case CLIENT -> parameterTypes.add(JobClient.class);
        case VARIABLE_STRING -> parameterTypes.add(String.class);
        case VARIABLES_AS_TYPE_1 -> parameterTypes.add(VariablesAsType1.class);
        case VARIABLES_AS_TYPE_2 -> parameterTypes.add(VariablesAsType2.class);
        case VARIABLE_COMPLEX -> parameterTypes.add(ComplexVariable.class);
      }
    }
    return parameterTypes.toArray(new Class<?>[0]);
  }

  public static List<TestDimension> generateTestDimensions() {
    final List<TestDimension> testDimensions = new ArrayList<>();
    for (final AutoComplete autoComplete : AutoComplete.values()) {
      for (final Response response : Response.values()) {
        final List<List<Parameter>> parameterLists = new ArrayList<>();
        parameterLists.add(new ArrayList<>());
        for (final Parameter parameter : Parameter.values()) {
          // clone all existing lists
          final List<List<Parameter>> clonedParameterLists =
              parameterLists.stream().map(TestJobWorkers::clone).toList();
          // add parameter to clones
          clonedParameterLists.forEach(list -> list.add(parameter));
          // append all clones
          parameterLists.addAll(clonedParameterLists);
        }
        parameterLists.forEach(
            parameters ->
                testDimensions.add(
                    new TestDimension(autoComplete, response, new ArrayList<>(parameters))));
      }
    }
    return testDimensions.stream()
        .filter(not(TestJobWorkers::isNotAutoCompleteAndResponse))
        .toList();
  }

  private static List<Parameter> clone(final List<Parameter> parameters) {
    return new ArrayList<>(parameters);
  }

  private static String generateMethod(final TestDimension td) {
    final StringBuilder sb = new StringBuilder();
    sb.append("@JobWorker")
        .append(td.autoComplete() == AutoComplete.YES ? "" : "(autoComplete = false)")
        .append("\n")
        .append("public ")
        .append(td.response() == Response.RESPONSE ? "JobResponse" : "void")
        .append(" ")
        .append(generateMethodName(td))
        .append("(")
        .append(generateParameterString(td.parameters()))
        .append(") {")
        .append("\n");
    if (td.response() == Response.JOB_ERROR) {
      sb.append("  throw CamundaError.jobError(\"test error\");").append("\n");
    } else if (td.response() == Response.JOB_ERROR_VARIABLES) {
      sb.append("  throw CamundaError.jobError(\"test error\", new JobResponse());").append("\n");
    } else if (td.response() == Response.JOB_ERROR_RETRIES) {
      sb.append("  throw CamundaError.jobError(\"test error\", new JobResponse(), 2);")
          .append("\n");
    } else if (td.response() == Response.JOB_ERROR_RETRY_BACKOFF) {
      sb.append(
              "  throw CamundaError.jobError(\"test error\", new JobResponse(), 2, Duration.ofSeconds(10));")
          .append("\n");
    } else if (td.response() == Response.BPMN_ERROR_VARIABLES) {
      sb.append("  throw CamundaError.bpmnError(\"testCode\",\"test message\", new JobResponse());")
          .append("\n");
    } else if (td.response() == Response.BPMN_ERROR) {
      sb.append("  throw CamundaError.bpmnError(\"testCode\",\"test message\");").append("\n");
    } else if (td.response() == Response.RESPONSE) {
      sb.append("  return new JobResponse();").append("\n");
    }
    sb.append("}").append("\n");
    return sb.toString();
  }

  private static String generateParameterString(final List<Parameter> parameters) {
    final AtomicInteger counter = new AtomicInteger(0);
    return parameters.stream()
        .map(
            p ->
                switch (p) {
                  case JOB -> "ActivatedJob job";
                  case CLIENT -> "JobClient client";
                  case VARIABLE_STRING -> "@Variable String var" + counter.getAndIncrement();
                  case VARIABLE_COMPLEX ->
                      "@Variable ComplexVariable var" + counter.getAndIncrement();
                  case VARIABLES_AS_TYPE_1 ->
                      "@VariablesAsType VariablesAsType1 varAsType" + counter.getAndIncrement();
                  case VARIABLES_AS_TYPE_2 ->
                      "@VariablesAsType VariablesAsType2 varAsType" + counter.getAndIncrement();
                })
        .collect(Collectors.joining(", "));
  }

  private static String generateMethodName(final TestDimension td) {
    final String autoCompletePart =
        switch (td.autoComplete()) {
          case YES -> "autoComplete";
          case NO -> "noAutoComplete";
        };
    final String responsePart =
        switch (td.response()) {
          case VOID -> "";
          case RESPONSE -> "WithResponse";
          case BPMN_ERROR_VARIABLES -> "BpmnErrorVariables";
          case JOB_ERROR -> "JobError";
          case BPMN_ERROR -> "BpmnError";
          case JOB_ERROR_VARIABLES -> "JobErrorVariables";
          case JOB_ERROR_RETRIES -> "JobErrorRetries";
          case JOB_ERROR_RETRY_BACKOFF -> "JobErrorRetryBackoff";
        };
    return autoCompletePart + responsePart;
  }

  private static boolean isNotAutoCompleteAndResponse(final TestDimension testDimension) {
    return testDimension.autoComplete() == AutoComplete.NO
        && testDimension.response() == Response.RESPONSE;
  }

  @JobWorker
  public void autoComplete() {}

  @JobWorker
  public void autoComplete(final ActivatedJob job) {}

  @JobWorker
  public void autoComplete(final JobClient client) {}

  @JobWorker
  public void autoComplete(final ActivatedJob job, final JobClient client) {}

  @JobWorker
  public void autoComplete(@Variable final String var0) {}

  @JobWorker
  public void autoComplete(final ActivatedJob job, @Variable final String var0) {}

  @JobWorker
  public void autoComplete(final JobClient client, @Variable final String var0) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {}

  @JobWorker
  public void autoComplete(@Variable final ComplexVariable var0) {}

  @JobWorker
  public void autoComplete(final ActivatedJob job, @Variable final ComplexVariable var0) {}

  @JobWorker
  public void autoComplete(final JobClient client, @Variable final ComplexVariable var0) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {}

  @JobWorker
  public void autoComplete(@Variable final String var0, @Variable final ComplexVariable var1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {}

  @JobWorker
  public void autoComplete(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {}

  @JobWorker
  public void autoComplete(@VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker
  public void autoComplete(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker
  public void autoComplete(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker
  public void autoComplete(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker
  public void autoComplete(@VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker
  public void autoComplete(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker
  public void autoComplete(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker
  public void autoComplete(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker
  public void autoComplete(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker
  public void autoComplete(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker
  public void autoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker
  public JobResponse autoCompleteWithResponse() {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(final ActivatedJob job) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(final JobClient client) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(final ActivatedJob job, final JobClient client) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(@Variable final String var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(final ActivatedJob job, @Variable final String var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(final JobClient client, @Variable final String var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(@Variable final ComplexVariable var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client, @Variable final ComplexVariable var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(@VariablesAsType final VariablesAsType1 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(@VariablesAsType final VariablesAsType2 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    return new JobResponse();
  }

  @JobWorker
  public JobResponse autoCompleteWithResponse(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    return new JobResponse();
  }

  @JobWorker
  public void autoCompleteBpmnError() {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final ActivatedJob job) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final ActivatedJob job, final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(@Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(@Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables() {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(final ActivatedJob job) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(final ActivatedJob job, final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(@Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(@Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobError() {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final ActivatedJob job) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final JobClient client) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(@Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker
  public void autoCompleteJobErrorVariables() {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(final ActivatedJob job) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(@Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker
  public void autoCompleteJobErrorRetries() {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(final ActivatedJob job) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(@Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff() {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(final ActivatedJob job) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(@Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker
  public void autoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoComplete() {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final ActivatedJob job) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final JobClient client) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final ActivatedJob job, final JobClient client) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(@Variable final String var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final ActivatedJob job, @Variable final String var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final JobClient client, @Variable final String var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(@Variable final ComplexVariable var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final ActivatedJob job, @Variable final ComplexVariable var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(final JobClient client, @Variable final ComplexVariable var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(@Variable final String var0, @Variable final ComplexVariable var1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(@VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(@VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker(autoComplete = false)
  public void noAutoComplete(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {}

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError() {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(final ActivatedJob job) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(final ActivatedJob job, final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(@Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(@Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables() {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(final ActivatedJob job) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(final ActivatedJob job, final JobClient client) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(@Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(@Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteBpmnErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.bpmnError("testCode", "test message", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError() {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final ActivatedJob job) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final JobClient client) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(@Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobError(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error");
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables() {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(final ActivatedJob job) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(@Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorVariables(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse());
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries() {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(final ActivatedJob job) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(@Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(@VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(@VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetries(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2);
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff() {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(final ActivatedJob job) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(final ActivatedJob job, final JobClient client) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(@Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, final JobClient client, @Variable final String var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(@Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, final JobClient client, @Variable final ComplexVariable var0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client, @Variable final String var0, @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client, @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client, @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType2 varAsType0) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final ComplexVariable var0, @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @VariablesAsType final VariablesAsType1 varAsType0,
      @VariablesAsType final VariablesAsType2 varAsType1) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final ComplexVariable var0,
      @VariablesAsType final VariablesAsType1 varAsType1,
      @VariablesAsType final VariablesAsType2 varAsType2) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  @JobWorker(autoComplete = false)
  public void noAutoCompleteJobErrorRetryBackoff(
      final ActivatedJob job,
      final JobClient client,
      @Variable final String var0,
      @Variable final ComplexVariable var1,
      @VariablesAsType final VariablesAsType1 varAsType2,
      @VariablesAsType final VariablesAsType2 varAsType3) {
    throw CamundaError.jobError("test error", new JobResponse(), 2, Duration.ofSeconds(10));
  }

  public record TestDimension(
      AutoComplete autoComplete, Response response, List<Parameter> parameters) {}

  public record JobResponse() {}

  public record ComplexVariable() {}

  public record VariablesAsType1() {}

  public record VariablesAsType2() {}

  public enum AutoComplete {
    YES,
    NO
  }

  public enum Response {
    VOID,
    RESPONSE,
    BPMN_ERROR,
    BPMN_ERROR_VARIABLES,
    JOB_ERROR,
    JOB_ERROR_VARIABLES,
    JOB_ERROR_RETRIES,
    JOB_ERROR_RETRY_BACKOFF
  }

  public enum Parameter {
    JOB,
    CLIENT,
    VARIABLE_STRING,
    VARIABLE_COMPLEX,
    VARIABLES_AS_TYPE_1,
    VARIABLES_AS_TYPE_2
  }
}
