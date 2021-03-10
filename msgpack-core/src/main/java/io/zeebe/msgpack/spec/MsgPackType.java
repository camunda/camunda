/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

public enum MsgPackType {
  NIL(true),
  INTEGER(true),
  BOOLEAN(true),
  FLOAT(true),
  ARRAY(false),
  MAP(false),
  BINARY(true),
  STRING(true),
  EXTENSION(true),
  NEVER_USED(true);

  protected final boolean isScalar;

  MsgPackType(final boolean isScalar) {
    this.isScalar = isScalar;
  }

  public boolean isScalar() {
    return isScalar;
  }
}
