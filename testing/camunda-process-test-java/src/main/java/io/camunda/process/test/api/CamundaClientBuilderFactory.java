/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import io.camunda.client.CamundaClientBuilder;
import java.util.function.Supplier;

public interface CamundaClientBuilderFactory extends Supplier<CamundaClientBuilder> {}
