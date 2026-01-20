/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessUtil {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessUtil.class);

  public static void deployProcess(final CamundaClient client, final List<String> bpmnXmlPaths) {
    final var deployCmd = constructDeploymentCommand(client, bpmnXmlPaths);

    while (true) {
      try {
        deployCmd.send().join();
        break;
      } catch (final Exception e) {
        LOG.warn("Failed to deploy process, retrying", e);
        try {
          Thread.sleep(200);
        } catch (final InterruptedException ex) {
          // ignore
        }
      }
    }
  }

  private static DeployResourceCommandStep2 constructDeploymentCommand(
      final CamundaClient client, final List<String> bpmnXmlPaths) {
    final var deployCmd =
        client.newDeployResourceCommand().addResourceFromClasspath(bpmnXmlPaths.getFirst());

    for (final var model : bpmnXmlPaths.stream().skip(1).toList()) {
      deployCmd.addResourceFromClasspath(model);
    }

    return deployCmd;
  }
}
