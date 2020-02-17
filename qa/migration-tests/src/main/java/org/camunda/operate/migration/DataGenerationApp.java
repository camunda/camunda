/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.camunda.operate.property","org.camunda.operate.migration"})
public class DataGenerationApp implements CommandLineRunner {

  @Autowired
  private DataGenerator dataGenerator;

  public static void main(String[] args) {
    SpringApplication.run(DataGenerationApp.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    dataGenerator.createData();
    System.exit(0);
  }

}
