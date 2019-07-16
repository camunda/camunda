/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

/**
 * Represents the exception which will be thrown if something during the mapping of documents goes
 * wrong.
 */
public class MappingException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public MappingException(String msg) {
    super(msg);
  }
}
