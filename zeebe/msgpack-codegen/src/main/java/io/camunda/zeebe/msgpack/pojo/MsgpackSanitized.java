/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a stored field of a {@link MsgpackPojo} whose value is sensitive and must not appear in a
 * string representation, like a property declared with {@code sanitized()}. The stored format is
 * not affected: the value is written and read as any other.
 *
 * <p>The processor records the mark so that generated string representations can mask the value;
 * nothing generated uses it yet.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface MsgpackSanitized {}
