/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.matcher;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Set;

/**
 * Filter class that represents a filter for a specific ValueType and a set of intents.
 *
 * @param valueType
 * @param intents
 */
public record Filter(ValueType valueType, Set<String> intents) {}
