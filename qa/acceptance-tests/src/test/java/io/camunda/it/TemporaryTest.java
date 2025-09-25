/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

public class TemporaryTest {

  @Test
  void x() throws URISyntaxException, InterruptedException {
    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080"))
            .grpcAddress(new URI("http://localhost:26500"))
            .preferRestOverGrpc(true)
            .build();

    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join();
    Thread.sleep(5000);
  }
}
