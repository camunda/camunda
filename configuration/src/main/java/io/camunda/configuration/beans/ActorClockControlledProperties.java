/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beans;

/**
 * NOTE: Some of the fields of this object are overridden with values coming from the Unified
 * Configuration system, from the object
 * io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride
 */
public record ActorClockControlledProperties(boolean controlled) {}
