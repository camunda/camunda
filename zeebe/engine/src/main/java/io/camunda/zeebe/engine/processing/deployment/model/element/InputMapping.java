/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * A single transformed input mapping: the parsed source expression and the target path it is stored
 * under, split into its {@code '.'}-separated segments (e.g. target {@code a.b.c} becomes {@code
 * [a, b, c]}). Input mappings are evaluated one by one in modeling order at runtime, and a nested
 * target shadows a same-named variable from a higher scope only for the keys it defines.
 */
@NullMarked
public record InputMapping(Expression source, List<String> targetPath) {}
