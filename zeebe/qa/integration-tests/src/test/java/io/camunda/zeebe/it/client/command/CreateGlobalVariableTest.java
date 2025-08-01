/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class CreateGlobalVariableTest {
  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(5)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  public void checkGlobalVariableIsResolvedAndDoesNotAffectVariablesBehavior() {

    final var result = client.newGlobalVariableCreationRequest().variable("KEY_2", "_2_").execute();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .serviceTask(
                "USER_TASK_ELEMENT_ID",
                serviceTaskBuilder -> serviceTaskBuilder.zeebeJobType("ServiceTaskJob"))
            .endEvent()
            .done();

    final var key = resourcesHelper.deployProcess(process);
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(key)
        .variable("process_variable", "test_2")
        .send()
        .join()
        .getProcessInstanceKey();

    final JobHandler completeJobHandler =
        (jobClient, job) -> {
          System.out.println(job.getVariables());
          client.newCompleteCommand(job).send().join();
        };

    client.newWorker().jobType("ServiceTaskJob").handler(completeJobHandler).open();

    final var jobBatch =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType("ServiceTaskJob")
            .getFirst()
            .getValue();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.COMPLETED)
        .withType("ServiceTaskJob")
        .await();

    Assertions.assertThat(jobBatch.getJobs().getFirst())
        .hasVariables(Map.of("process_variable", "test_2"));
    assertThat(jobBatch.getJobs().getFirst().getVariables()).doesNotContainKey("KEY_2");
  }

  @Test
  public void checkGlobalVariableAsObject() {

    final var dima = new Student("Dima", "Melnychunk", 30);
    final var mathias = new Student("Mathias", "Vandaele", 30);

    final var result =
        client.newGlobalVariableCreationRequest().variable("GLOBAL_KEY", dima).execute();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .serviceTask(
                "USER_TASK_ELEMENT_ID",
                serviceTaskBuilder ->
                    serviceTaskBuilder.zeebeJobTypeExpression(
                        "camunda.vars.env.GLOBAL_KEY.firstname"))
            .endEvent()
            .done();

    final var key = resourcesHelper.deployProcess(process);
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(key)
        .variable("process_variable", mathias)
        .send()
        .join()
        .getProcessInstanceKey();

    final JobHandler completeJobHandler =
        (jobClient, job) -> {
          System.out.println(job.getVariables());
          client.newCompleteCommand(job).send().join();
        };

    client.newWorker().jobType("Dima").handler(completeJobHandler).open();

    RecordingExporter.jobRecords().withIntent(JobIntent.COMPLETED).withType("Dima").await();
  }

  record Student(String firstname, String lastname, int age) {}
}
