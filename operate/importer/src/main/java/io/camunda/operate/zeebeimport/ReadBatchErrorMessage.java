/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import java.util.function.BiFunction;

public final class ReadBatchErrorMessage {
  public static final BiFunction<String, String, String> READ_BATCH_ERROR_MESSAGE =
      (aliasName, message) ->
          String.format(
              "Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s",
              aliasName, message);

  private ReadBatchErrorMessage() {}
}
