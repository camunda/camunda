/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;

/**
 * Utility to produce some work for a Zeebe cluster. The intention is to use {@link
 * #performSampleWorkload(ZeebeClient client)} for blackbox testing.
 *
 * <p>It's not the intention to add more methods that produce a specific workload to test individual
 * features. Prefer to define these methods next to those tests instead of making {@link
 * WorkloadGenerator} a grab-bag of specialized methods.
 */
public final class WorkloadGenerator {
  private static final BpmnModelInstance SAMPLE_PROCESS =
      Bpmn.createExecutableProcess("testProcess")
          .startEvent()
          .intermediateCatchEvent(
              "message",
              e -> e.message(m -> m.name("catch").zeebeCorrelationKeyExpression("orderId")))
          .serviceTask("task", t -> t.zeebeJobType("work").zeebeTaskHeader("foo", "bar"))
          .endEvent()
          .done();

  private WorkloadGenerator() {}

  /**
   * Given a client, deploy a process, start instances, work on service tasks, create and resolve
   * incidents and finish the instance.
   */
  public static void performSampleWorkload(final ZeebeClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(SAMPLE_PROCESS, "sample_process.bpmn")
        .send()
        .join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", "foo-bar-123");
    variables.put("largeValue", "x".repeat(8192));
    variables.put("unicode", "Á");

    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("testProcess")
            .latestVersion()
            .variables(variables)
            .send()
            .join()
            .getProcessInstanceKey();

    // create job worker which fails on first try and sets retries to 0 to create an incident
    final AtomicBoolean fail = new AtomicBoolean(true);
    final JobWorker worker =
        client
            .newWorker()
            .jobType("work")
            .handler(
                (handlerClient, job) -> {
                  if (fail.getAndSet(false)) {
                    // fail job
                    handlerClient
                        .newFailCommand(job.getKey())
                        .retries(0)
                        .errorMessage("failed")
                        .send()
                        .join();
                  } else {
                    handlerClient.newCompleteCommand(job.getKey()).send().join();
                  }
                })
            .open();

    client
        .newPublishMessageCommand()
        .messageName("catch")
        .correlationKey("foo-bar-123")
        .send()
        .join();

    // wait for incident and resolve it
    final Record<IncidentRecordValue> incident =
        Awaitility.await("the incident was created")
            .timeout(Duration.ofMinutes(1))
            .until(
                () ->
                    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .withElementId("task")
                        .findFirst(),
                Optional::isPresent)
            .orElseThrow();

    client.newUpdateRetriesCommand(incident.getValue().getJobKey()).retries(3).send().join();
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // wrap up
    Awaitility.await("the process instance was completed")
        .timeout(Duration.ofMinutes(1))
        .until(
            () ->
                RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                    .filter(r -> r.getKey() == processInstanceKey)
                    .exists());
    worker.close();
  }
}
