/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.camunda.optimize.data.generation.DataGenerationConfig.DATA_INSTANCE_COUNT;
import static org.camunda.optimize.data.generation.DataGenerationConfig.DATA_PROCESS_DEFINITION_COUNT;

@SpringBootApplication
public class DataGenerationApp implements CommandLineRunner {

  @Autowired
  private DataGenerator dataGenerator;

  public static void main(String[] args) {
    SpringApplication.run(DataGenerationApp.class, args);
  }

  @Override
  public void run(String... args) {
    if (Integer.getInteger(DATA_INSTANCE_COUNT) < Integer.getInteger(DATA_PROCESS_DEFINITION_COUNT)
      || Integer.getInteger(DATA_INSTANCE_COUNT) <= 0
      || Integer.getInteger(DATA_PROCESS_DEFINITION_COUNT) <= 0) {
      throw new IllegalArgumentException(
        "Instance count must be greater than or equal to definition count and both must be greater than 0.");
    }
    dataGenerator.createData();
    System.exit(0);
  }


}
