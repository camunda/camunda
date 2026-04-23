/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

import org.agrona.DirectBuffer;

public interface OutboundBuffer extends AutoCloseable {

  void writeBytes(DirectBuffer src, int srcOffset, int length);

  int length();

  @Override
  void close();
}
