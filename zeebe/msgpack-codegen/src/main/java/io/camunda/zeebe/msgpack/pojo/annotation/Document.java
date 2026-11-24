/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code DirectBuffer} field holds a document, like the {@code DocumentProperty} it replaces: a
 * msgpack map that the codec stores as a msgpack binary. The codec reads a nil or empty value as
 * the empty map, and rejects any other value that is not a map, see {@code Decoding#document}. Do
 * not use it together with {@link Raw} or {@link Binary}.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Document {}
