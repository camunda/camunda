/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.grpc.codec;

import io.grpc.Codec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

public final class SnappyCodec implements Codec {

  @Override
  public String getMessageEncoding() {
    return "snappy";
  }

  @Override
  public OutputStream compress(final OutputStream os) throws IOException {
    return new SnappyOutputStream(os);
  }

  @Override
  public InputStream decompress(final InputStream is) throws IOException {
    return new SnappyInputStream(is);
  }
}
