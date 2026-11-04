/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

/**
 * Application event that {@code Starter} publishes when the configured duration limit has elapsed.
 * {@code ProcessInstanceCreator} and the meters listen to it and stop their work.
 */
public record StarterFinished() {}
