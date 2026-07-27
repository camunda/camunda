/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * The transformed input mappings of a flow node: one entry per {@code zeebe:input} element, in
 * modeling order.
 */
@NullMarked
public record InputMappings(List<InputMapping> mappings) {}
