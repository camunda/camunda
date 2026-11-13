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
 * Marks the single {@link UnknownProperties} field of a {@link MsgpackPojo}. Keys the POJO does not
 * declare are kept there on read and written back after the declared ones, so that a read-modify-
 * write by an older version does not drop properties of a newer one. Every {@link MsgpackPojo}
 * needs exactly one such field, and it must be final.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface MsgpackUnknownProperties {}
