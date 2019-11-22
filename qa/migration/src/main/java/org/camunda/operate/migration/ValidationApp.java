package org.camunda.operate.migration;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ValidationApp implements CommandLineRunner {

  @Autowired
  private Validator validator;

  public static void main(String[] args) {
    SpringApplication.run(ValidationApp.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
	validator.validate();
    System.exit(0);
  }
}
