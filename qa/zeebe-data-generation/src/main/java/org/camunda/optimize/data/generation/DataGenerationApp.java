/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataGenerationApp implements CommandLineRunner {

  private final DataGenerator dataGenerator;
  private final Integer instanceCount;
  private final Integer definitionCount;

  public DataGenerationApp(final DataGenerator dataGenerator,
                           @Value("${DATA_INSTANCE_COUNT:1000000}") final Integer instanceCount,
                           @Value("${DATA_PROCESS_DEFINITION_COUNT:100}") final Integer definitionCount) {
    this.dataGenerator = dataGenerator;
    this.instanceCount = instanceCount;
    this.definitionCount = definitionCount;
  }

  public static void main(String[] args) {
    SpringApplication.run(DataGenerationApp.class, args);
  }

  @Override
  public void run(String... args) {
    if (instanceCount < definitionCount || instanceCount <= 0 || definitionCount <= 0) {
      throw new IllegalArgumentException(
        "Instance count must be greater than or equal to definition count and both must be greater than 0.");
    }
    dataGenerator.createData();
    System.exit(0);
  }


}
