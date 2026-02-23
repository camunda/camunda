/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class JobIT {

  private static CamundaClient client;

  private static final String PROCESS_ID = "job-collection-fields-test";
  private static final String JOB_TYPE = "job-collection-fields-job";

  @BeforeAll
  static void beforeAll() {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            // service task with no custom task headers defined
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(client, process, PROCESS_ID + ".bpmn");
    startProcessInstance(client, PROCESS_ID);

    // wait until the job is available in the search index
    Awaitility.await("job should be available in search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newJobSearchRequest()
                            .filter(f -> f.type(t -> t.eq(JOB_TYPE)))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
  }

  @Test
  void shouldReturnEmptyMapForCustomHeadersWhenNoneAreDefined() {
    // when
    final Job job =
        client
            .newJobSearchRequest()
            .filter(f -> f.type(t -> t.eq(JOB_TYPE)))
            .send()
            .join()
            .items()
            .getFirst();

    // then — customHeaders must not be null even though no headers were defined in the process
    assertThat(job.getCustomerHeaders()).isNotNull().isEmpty();
  }
}
