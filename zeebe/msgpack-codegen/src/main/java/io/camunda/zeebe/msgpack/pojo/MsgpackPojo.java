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
 * Marks a plain data class for which a {@code <Name>Serializer} and a {@code <Name>Deserializer}
 * are generated in the same package.
 *
 * <p>The class needs a no-arg constructor that is accessible from the same package, and must be
 * {@link io.camunda.zeebe.msgpack.Recyclable}: its {@code reset()} restores the default of every
 * property and is called before a value is read, so properties missing in the input keep their
 * default. Compilation fails otherwise. The generated code reads and writes the fields directly, so
 * they must not be private; scalar, string, enum and raw fields must not be final. Every non-static
 * field is stored unless annotated with {@link MsgpackIgnore}. Properties are written in field
 * declaration order and keyed by field name, both of which are part of the stored format.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface MsgpackPojo {}
