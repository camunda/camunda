/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

public class LangUtil {

  public static void rethrowUnchecked(final Throwable ex) {
    LangUtil.<RuntimeException>rethrow(ex);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void rethrow(final Throwable t) throws T {
    throw (T) t;
  }
}
