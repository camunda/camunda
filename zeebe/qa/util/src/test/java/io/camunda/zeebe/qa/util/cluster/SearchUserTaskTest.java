/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import net.bytebuddy.utility.dispatcher.JavaDispatcher.Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@ZeebeIntegration
public class SearchUserTaskTest {


  @TestZeebe
  final TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  final ZeebeClient zeebeClient = testStandaloneCamunda.newClientBuilder().build();


  @BeforeEach
  void initClientAndInstances() {

  }

  @Test
  void shouldRetrieveUserTask() {

  }


}
