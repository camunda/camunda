/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.io;

import java.io.IOException;
import java.io.InputStream;

public final class AlwaysFailingInputStream extends InputStream {
  @Override
  public int read() throws IOException {
    throw new IOException("Read failure - try again");
  }
}
