/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Pins the behaviour that the synthetic <em>inner instance</em> of an ad-hoc subprocess surfaces in
 * the instance history named after the element that was activated inside it (its entry element),
 * rather than the generic synthetic id {@code <ahspId>#innerInstance}.
 *
 * <p>Activating "listUsers" (name "List users") makes the inner instance's {@code elementName} read
 * "List users". The name must also survive completion of the inner instance / process instance
 * (guarding the null-clobber regression where a later write could overwrite the resolved name).
 *
 * <p>Disabled on RDBMS: the naming lives only in the camunda-exporter (Elasticsearch/OpenSearch).
 * The RDBMS exporter has no equivalent handler, so the inner instance name stays null there and
 * this test's await would time out. RDBMS parity is out of scope for 8.10.
 */
@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason =
        "Inner-instance naming is implemented only in the camunda-exporter (ES/OS); "
            + "the RDBMS exporter has no equivalent, so the name stays null on RDBMS.")
public class AdHocSubProcessInnerInstanceNameIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldNameInnerInstanceAfterActivatedEntryElementAndRetainItAfterCompletion() {
    // given
    final var processModel =
        Bpmn.createExecutableProcess("adHocInnerInstanceNameProcess")
            .startEvent()
            .adHocSubProcess(
                "adHocSubProcess",
                ahsp ->
                    ahsp.serviceTask("listUsers", t -> t.zeebeJobType("listUsersJob"))
                        .name("List users"))
            .endEvent("end")
            .done();

    final var process =
        deployProcessAndWaitForIt(camundaClient, processModel, "adhoc-inner-instance-name.bpmn");

    // when
    final long processInstanceKey =
        startProcessInstance(camundaClient, process.getBpmnProcessId()).getProcessInstanceKey();

    waitForElementInstances(
        camundaClient,
        f -> f.elementId("adHocSubProcess").processInstanceKey(processInstanceKey),
        1);

    final long adHocSubProcessInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId("adHocSubProcess").processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    camundaClient
        .newActivateAdHocSubProcessActivitiesCommand(String.valueOf(adHocSubProcessInstanceKey))
        .activateElements("listUsers")
        .send()
        .join();

    waitForElementInstances(
        camundaClient,
        f -> f.elementId("adHocSubProcess#innerInstance").processInstanceKey(processInstanceKey),
        1);

    final long jobKey = awaitEntryElementJobKey("listUsersJob");

    // the name is written by the entry child's ELEMENT_ACTIVATING record, which may be exported
    // after the inner-instance doc itself, so await the name before asserting on it.
    final var activeInnerInstance =
        Awaitility.await("inner instance is named")
            .atMost(Duration.ofSeconds(60))
            .ignoreExceptions()
            .until(
                () -> queryInnerInstance(processInstanceKey, "adHocSubProcess#innerInstance"),
                instance -> instance.getElementName() != null);

    // then
    assertThat(activeInnerInstance.getElementName())
        .describedAs("inner instance should be named after the activated entry element")
        .isEqualTo("List users");
    assertThat(activeInnerInstance.getElementName())
        .describedAs("inner instance name should not fall back to the synthetic id")
        .isNotEqualTo("adHocSubProcess#innerInstance");

    // when
    camundaClient.newCompleteCommand(jobKey).send().join();

    Awaitility.await("inner instance completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        queryInnerInstance(processInstanceKey, "adHocSubProcess#innerInstance")
                            .getState())
                    .isEqualTo(ElementInstanceState.COMPLETED));

    // then
    final var completedInnerInstance =
        queryInnerInstance(processInstanceKey, "adHocSubProcess#innerInstance");
    assertThat(completedInnerInstance.getElementName())
        .describedAs("inner instance name should survive completion")
        .isEqualTo("List users");
    assertThat(completedInnerInstance.getElementName())
        .describedAs("completed inner instance name should not fall back to the synthetic id")
        .isNotEqualTo("adHocSubProcess#innerInstance");
  }

  private static long awaitEntryElementJobKey(final String jobType) {
    return Awaitility.await("job for entry element is created")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () ->
                camundaClient
                    .newActivateJobsCommand()
                    .jobType(jobType)
                    .maxJobsToActivate(1)
                    .timeout(Duration.ofMinutes(5))
                    .send()
                    .join()
                    .getJobs()
                    .stream()
                    .findFirst()
                    .map(ActivatedJob::getKey)
                    .orElse(null),
            key -> key != null);
  }

  private static ElementInstance queryInnerInstance(
      final long processInstanceKey, final String innerInstanceElementId) {
    return camundaClient
        .newElementInstanceSearchRequest()
        .filter(f -> f.elementId(innerInstanceElementId).processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .getFirst();
  }
}
