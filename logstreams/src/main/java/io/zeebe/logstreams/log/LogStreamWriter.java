/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.log;

public interface LogStreamWriter {

  /**
   * Attempts to write the event to the underlying stream.
   *
   * @return the event position or a negative value if fails to write the event
   */
  long tryWrite();
}
