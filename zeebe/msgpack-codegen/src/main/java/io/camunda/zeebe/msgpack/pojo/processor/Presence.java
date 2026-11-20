/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

/** Whether a property can be unset, like a legacy property declared without a default. */
enum Presence {
  /** It has a default, the field initializer. */
  DEFAULT,
  /** A {@code @Nullable} field, unset while it is null. */
  NULLABLE,
  /** A {@code RequiredLong} or {@code RequiredInt} holder, unset until a value is set. */
  HOLDER
}
