/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.tagged.impl;

import java.util.Map;

/**
 * Structured representation of a tagged parameter/value that can be used for further processing,
 * such as generating a JSON schema or documentation.
 */
public record TaggedParameter(
    String name,
    String description,
    String type,
    Map<String, Object> schema,
    Map<String, Object> options) {}
