/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.example.cluster.TopologyViewer;
import io.zeebe.example.data.HandleVariablesAsPojo;
import io.zeebe.example.job.JobWorkerCreator;
import io.zeebe.example.workflow.NonBlockingWorkflowInstanceCreator;
import io.zeebe.example.workflow.WorkflowDeployer;
import io.zeebe.example.workflow.WorkflowInstanceCreator;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DocsConsistencyTest {
  @Parameter(0)
  public Class<?> exampleClass;

  @Parameter(1)
  public String expectedClassName;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {TopologyViewer.class, "io.zeebe.example.cluster.TopologyViewer"},
          {JobWorkerCreator.class, "io.zeebe.example.job.JobWorkerCreator"},
          {
            NonBlockingWorkflowInstanceCreator.class,
            "io.zeebe.example.workflow.NonBlockingWorkflowInstanceCreator"
          },
          {WorkflowDeployer.class, "io.zeebe.example.workflow.WorkflowDeployer"},
          {WorkflowInstanceCreator.class, "io.zeebe.example.workflow.WorkflowInstanceCreator"},
          {HandleVariablesAsPojo.class, "io.zeebe.example.data.HandleVariablesAsPojo"},
        });
  }

  @Test
  public void todo() {
    assertThat(exampleClass.getName())
        .withFailMessage(
            "This class's source code is referenced from the java-client-example docs. "
                + "Make sure to adapt them as well.")
        .isEqualTo(expectedClassName);
  }
}
