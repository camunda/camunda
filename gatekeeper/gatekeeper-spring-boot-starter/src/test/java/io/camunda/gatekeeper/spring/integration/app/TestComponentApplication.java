/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A minimal Spring Boot application that integrates gatekeeper. This mirrors what an external
 * Camunda component would set up: a {@code @SpringBootApplication} with SPI adapter beans
 * discovered via component scanning.
 *
 * <p>The adapter implementations in the {@code adapter/} package are annotated with
 * {@code @Component} and will be picked up automatically, just as they would in a real component.
 */
@SpringBootApplication
public class TestComponentApplication {}
