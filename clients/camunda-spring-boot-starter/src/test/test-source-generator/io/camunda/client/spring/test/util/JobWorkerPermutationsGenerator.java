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
package io.camunda.client.spring.test.util;

import static java.util.function.Predicate.not;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.exception.CamundaError;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

public class JobWorkerPermutationsGenerator {

  public static void main(final String[] args) throws IOException {
    final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
    javaClass
        .setPackage(JobWorkerPermutationsGenerator.class.getPackageName())
        .setName("JobWorkerPermutations");

    generateTestDimensions().forEach(td -> addWorkerMethod(javaClass, td));

    final File file =
        new File(
            args[0]
                + "/"
                + javaClass.getPackage().replace('.', '/')
                + "/"
                + javaClass.getName()
                + ".java");
    final File directory = file.getParentFile();
    if (!directory.exists()) {
      directory.mkdirs();
    }
    final BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(javaClass.toString());
    writer.close();
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
              parameterLists.stream().map(JobWorkerPermutationsGenerator::clone).toList();
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
        .filter(not(JobWorkerPermutationsGenerator::isNotAutoCompleteAndResponse))
        .toList();
  }

  private static List<Parameter> clone(final List<Parameter> parameters) {
    return new ArrayList<>(parameters);
  }

  private static void addWorkerMethod(final JavaClassSource source, final TestDimension td) {

    final MethodSource<JavaClassSource> method =
        source.addMethod().setName(generateMethodName(td)).setPublic().setBody("");

    if (td.response() == Response.RESPONSE) {
      method.setReturnType(JobResponse.class);
    } else {
      method.setReturnTypeVoid();
    }

    addParameters(method, td.parameters);

    final AnnotationSource<JavaClassSource> jobWorkerAnnotation =
        method.addAnnotation(JobWorker.class);
    if (td.autoComplete() == AutoComplete.NO) {
      jobWorkerAnnotation.setLiteralValue("autoComplete", "false");
    }

    source.addImport(CamundaError.class);
    source.addImport(JobResponse.class);
    source.addImport(Duration.class);

    if (td.response() == Response.JOB_ERROR) {
      method.setBody("  throw CamundaError.jobError(\"test error\");");
    } else if (td.response() == Response.JOB_ERROR_VARIABLES) {
      method.setBody("  throw CamundaError.jobError(\"test error\", new JobResponse());");
    } else if (td.response() == Response.JOB_ERROR_RETRIES) {
      method.setBody("  throw CamundaError.jobError(\"test error\", new JobResponse(), 2);");
    } else if (td.response() == Response.JOB_ERROR_RETRY_BACKOFF) {
      method.setBody(
          "  throw CamundaError.jobError(\"test error\", new JobResponse(), 2, Duration.ofSeconds(10));");
    } else if (td.response() == Response.BPMN_ERROR_VARIABLES) {
      method.setBody(
          "  throw CamundaError.bpmnError(\"testCode\",\"test message\", new JobResponse());");
    } else if (td.response() == Response.BPMN_ERROR) {
      method.setBody("  throw CamundaError.bpmnError(\"testCode\",\"test message\");");
    } else if (td.response() == Response.RESPONSE) {
      method.setBody("  return new JobResponse();");
    }
  }

  private static void addParameters(
      final MethodSource<JavaClassSource> methodSource, final List<Parameter> parameters) {
    final AtomicInteger counter = new AtomicInteger(0);
    parameters.forEach(
        p -> {
          switch (p) {
            case JOB -> methodSource.addParameter(ActivatedJob.class, "job");
            case CLIENT -> methodSource.addParameter(JobClient.class, "client");
            case VARIABLE_COMPLEX ->
                methodSource
                    .addParameter(ComplexVariable.class, "var" + counter.getAndIncrement())
                    .addAnnotation(Variable.class);
            case VARIABLE_STRING ->
                methodSource
                    .addParameter(String.class, "var" + counter.getAndIncrement())
                    .addAnnotation(Variable.class);
            case VARIABLES_AS_TYPE_1 ->
                methodSource
                    .addParameter(VariablesAsType1.class, "varAsType" + counter.getAndIncrement())
                    .addAnnotation(VariablesAsType.class);
            case VARIABLES_AS_TYPE_2 ->
                methodSource
                    .addParameter(VariablesAsType2.class, "varAsType" + counter.getAndIncrement())
                    .addAnnotation(VariablesAsType.class);
            default -> throw new RuntimeException("Unexpected parameter type: " + p);
          }
        });
  }

  public static String generateMethodName(final TestDimension td) {
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

  public static Class<?>[] generateParameterTypes(final TestDimension td) {
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
        default -> throw new RuntimeException("Unexpected parameter type: " + parameter);
      }
    }
    return parameterTypes.toArray(new Class<?>[0]);
  }

  private static boolean isNotAutoCompleteAndResponse(final TestDimension testDimension) {
    return testDimension.autoComplete() == AutoComplete.NO
        && testDimension.response() == Response.RESPONSE;
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
