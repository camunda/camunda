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
 * <p>The class needs a public no-arg constructor. Default values are the field initializers. Every
 * {@link MsgpackProperty} field needs a getter ({@code getX}, or {@code isX} for booleans) and, for
 * scalar and string fields, a setter. Properties are written in field declaration order, which is
 * part of the stored format.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface MsgpackPojo {}
