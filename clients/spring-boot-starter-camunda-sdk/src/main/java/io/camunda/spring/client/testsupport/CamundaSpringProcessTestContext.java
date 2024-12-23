/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.testsupport;

/**
 * Marker bean, if present in Spring context we are running in a test environment and might want to
 * adjust certain lifecycle handlings
 */
public class CamundaSpringProcessTestContext {}
