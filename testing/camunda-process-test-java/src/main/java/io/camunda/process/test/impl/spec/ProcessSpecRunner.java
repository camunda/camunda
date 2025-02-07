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
package io.camunda.process.test.impl.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.spec.CamundaProcessSpec;
import io.camunda.process.test.api.spec.CamundaProcessSpecResource;
import io.camunda.process.test.api.spec.CamundaProcessSpecRunner;
import io.camunda.process.test.api.spec.CamundaProcessSpecTestCase;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.spec.dsl.SpecAction;
import io.camunda.process.test.impl.spec.dsl.SpecInstruction;
import io.camunda.process.test.impl.spec.dsl.SpecTestCase;
import io.camunda.process.test.impl.spec.dsl.SpecVerification;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessSpecRunner implements CamundaProcessSpecRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSpecRunner.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final CamundaProcessTestContext processTestContext;

  public ProcessSpecRunner(final CamundaProcessTestContext processTestContext) {
    this.processTestContext = processTestContext;
  }

  @Override
  public void runTestCase(
      final CamundaProcessSpecTestCase testCase, final List<CamundaProcessSpecResource> resources)
      throws AssertionError {
    if (testCase instanceof SpecTestCase) {

      final CamundaDataSource camundaDataSource =
          new CamundaDataSource(processTestContext.getCamundaRestAddress().toString());
      final CamundaClient camundaClient = processTestContext.createClient();
      final SpecTestContext testContext = new SpecTestContext(camundaDataSource, camundaClient);

      final SpecTestCaseResult result = run(testContext, (SpecTestCase) testCase, resources);

      if (!result.isSuccess()) {
        final String failureMessage = createFailureMessage((SpecTestCase) testCase, result);
        throw new AssertionError(failureMessage);
      }
    }
  }

  private SpecTestCaseResult run(
      final SpecTestContext testContext,
      final SpecTestCase testCase,
      final List<CamundaProcessSpecResource> testResources) {

    final SpecTestCaseResult testCaseResult = new SpecTestCaseResult();
    testCaseResult.setName(testCase.getName());
    testCaseResult.setSuccess(true);

    try {
      // 1: deploy resources
      try {
        deployResources(testContext.getCamundaClient(), testResources);
      } catch (final Exception e) {
        LOGGER.error("Failed to deploy test resources: {}.", testResources, e);

        testCaseResult.setSuccess(false);
        testCaseResult.setFailureMessage("Failed to deploy test resources: " + e.getMessage());
        return testCaseResult;
      }

      final Instant startTime = Instant.now();

      // 2: apply instructions
      final SpecVerificationResult verificationResult =
          runInstructions(testCase.getInstructions(), testContext, processTestContext);

      final Duration testDuration = Duration.between(startTime, Instant.now());
      testCaseResult.setTestDuration(testDuration);

      if (!verificationResult.isSuccessful()) {
        testCaseResult.setSuccess(false);
        testCaseResult.setFailedInstruction(verificationResult.getInstruction());
        testCaseResult.setFailureMessage(verificationResult.getFailureMessage());
      }

    } catch (final Exception e) {
      testCaseResult.setSuccess(false);
      testCaseResult.setFailureMessage("Failed to run test case: " + e.getMessage());

      LOGGER.error("Failed to run test case: {}.", testCase.getName(), e);
    }

    return testCaseResult;
  }

  private static void deployResources(
      final CamundaClient camundaClient, final List<CamundaProcessSpecResource> testResources) {
    testResources.forEach(
        testResource ->
            camundaClient
                .newDeployResourceCommand()
                .addResourceBytes(testResource.getResource(), testResource.getName())
                .send()
                .join());
  }

  private SpecVerificationResult runInstructions(
      final List<SpecInstruction> instructions,
      final SpecTestContext testContext,
      final CamundaProcessTestContext processTestContext) {

    for (final SpecInstruction instruction : instructions) {
      try {
        if (instruction instanceof SpecAction) {
          final SpecAction action = (SpecAction) instruction;
          action.execute(testContext, processTestContext);

        } else if (instruction instanceof SpecVerification) {
          final SpecVerification verification = (SpecVerification) instruction;
          verification.verify(testContext, processTestContext);
        }
      } catch (final AssertionError | Exception e) {
        return new SpecVerificationResult(instruction, false, e.getMessage());
      }
    }
    return new SpecVerificationResult(null, true, "<success>");
  }

  public ProcessSpecResult runSpec(final CamundaProcessSpec processSpec) {
    // 1: bootstrap
    final CamundaDataSource camundaDataSource =
        new CamundaDataSource(processTestContext.getCamundaRestAddress().toString());
    final CamundaClient camundaClient = processTestContext.createClient();
    final SpecTestContext testContext = new SpecTestContext(camundaDataSource, camundaClient);

    final CamundaProcessTestResultCollector processTestResultCollector =
        new CamundaProcessTestResultCollector(camundaDataSource);

    // 2: deploy resources
    try {
      deployResources(camundaClient, processSpec.getTestResources());
    } catch (final Exception e) {
      LOGGER.error("Failed to deploy test resources: {}.", processSpec.getTestResources(), e);

      final SpecTestCaseResult deploymentFailure = new SpecTestCaseResult();
      deploymentFailure.setName("Deploy test resources");
      deploymentFailure.setSuccess(false);
      deploymentFailure.setFailureMessage("Failed to deploy test resources: " + e.getMessage());

      return new ProcessSpecResult(
          0,
          processSpec.getTestCases().size(),
          Duration.ZERO,
          Collections.singletonList(deploymentFailure));
    }

    // 3: run test cases
    final Instant startTime = Instant.now();

    final List<SpecTestCaseResult> testResults =
        runTestCases(testContext, processTestResultCollector, processSpec);

    final int passedTestCases =
        (int) testResults.stream().filter(SpecTestCaseResult::isSuccess).count();
    final int totalTestCases = testResults.size();
    final Duration totalTestDuration = Duration.between(startTime, Instant.now());

    return new ProcessSpecResult(passedTestCases, totalTestCases, totalTestDuration, testResults);
  }

  private List<SpecTestCaseResult> runTestCases(
      final SpecTestContext testContext,
      final CamundaProcessTestResultCollector processTestResultCollector,
      final CamundaProcessSpec testSpecification) {
    return testSpecification.getTestCases().stream()
        .map(
            testCase -> {
              final SpecTestCaseResult testCaseResult =
                  run(testContext, (SpecTestCase) testCase, Collections.emptyList());

              if (!testCaseResult.isSuccess()) {
                final ProcessTestResult processTestResult = processTestResultCollector.collect();
                testCaseResult.setTestOutput(processTestResult.getProcessInstanceTestResults());
              }
              // TODO: clean state (i.e. purge)
              return testCaseResult;
            })
        .collect(Collectors.toList());
  }

  private static String createFailureMessage(
      final SpecTestCase testCase, final SpecTestCaseResult result) {

    final List<SpecInstruction> instructions = testCase.getInstructions();
    final SpecInstruction failedInstruction = result.getFailedInstruction();

    final List<String> formattedInstructions =
        instructions.stream()
            .limit(instructions.indexOf(failedInstruction))
            .map(instruction -> "(passed) -> " + instructionToString(instruction))
            .collect(Collectors.toList());
    formattedInstructions.add("(failed) -> " + instructionToString(failedInstruction));

    return String.format(
        "%s\n\nInstructions:\n%s",
        result.getFailureMessage(), String.join("\n", formattedInstructions));
  }

  private static String instructionToString(final SpecInstruction testInstruction) {
    try {
      return OBJECT_MAPPER.writeValueAsString(testInstruction);
    } catch (final JsonProcessingException e) {
      return "<???>";
    }
  }
}
