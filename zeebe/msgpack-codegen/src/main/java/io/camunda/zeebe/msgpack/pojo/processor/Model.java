/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @param unknownField the {@code UnknownProperties} field, or null if the class does not keep
 *     unknown properties
 */
record Model(String pkg, String name, List<Property> properties, @Nullable String unknownField) {}
