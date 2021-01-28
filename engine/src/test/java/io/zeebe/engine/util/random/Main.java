/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.Optional;
import java.util.Random;

public class Main {

  public static void main(String[] args) {
    Random random = new Random();

    for (int i = 0; i < 100; i++) {
      System.out.println("Generating process " + i);

      String id = "process" + i;

      RandomWorkflowBuilder builder =
          new RandomWorkflowBuilder(
              random.nextLong(), Optional.empty(), Optional.empty(), Optional.empty());

      Bpmn.writeModelToFile(new File(id + ".bpmn"), builder.buildWorkflow());

      for (int p = 0; p < 10; p++) {
        ExecutionPath path = builder.findRandomExecutionPath(random.nextLong());

        System.out.println("Execution path " + p + " :" + path);
      }
    }
  }
}
