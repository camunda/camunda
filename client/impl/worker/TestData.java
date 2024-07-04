/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.worker;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class TestData {
  static GatewayOuterClass.ActivatedJob job() {
    return job(12);
  }

  static GatewayOuterClass.ActivatedJob job(final long key) {
    return GatewayOuterClass.ActivatedJob.newBuilder()
        .setKey(key)
        .setType("foo")
        .setProcessInstanceKey(123)
        .setBpmnProcessId("test1")
        .setProcessDefinitionVersion(2)
        .setProcessDefinitionKey(23)
        .setElementId("foo")
        .setElementInstanceKey(23213)
        .setCustomHeaders("{\"version\": \"1\"}")
        .setWorker("worker1")
        .setRetries(34)
        .setDeadline(1231)
        .setVariables("{\"key\": \"val\"}")
        .build();
  }

  static List<ActivatedJob> jobs(final int numberOfJobs) {
    return IntStream.range(0, numberOfJobs).mapToObj(TestData::job).collect(Collectors.toList());
  }
}
