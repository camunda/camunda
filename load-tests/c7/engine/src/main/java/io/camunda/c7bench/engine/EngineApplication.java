/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Camunda 7 engine for the benchmark: embedded engine + REST API on a shared PostgreSQL.
 *
 * <p>Several replicas of this app against one database form a C7 "cluster" (homogeneous engines
 * sharing the DB), which is the standard C7 scale-out model.
 */
@SpringBootApplication
public class EngineApplication {
  public static void main(final String[] args) {
    SpringApplication.run(EngineApplication.class, args);
  }
}
