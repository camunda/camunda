/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import java.util.function.Supplier;
import org.junit.jupiter.api.extension.Extension;

public interface DatabaseTestExtension extends Extension {

  void assertMaxOpenScrollContexts(final int maxOpenScrollContexts);

  void refreshIndexesInElasticsearch();

  void refreshZeebeIndices();

  void refreshTasklistIndices();

  void processAllRecordsAndWait(TestCheck testCheck, Object... arguments);

  void processRecordsAndWaitFor(
      TestCheck testCheck, Supplier<Object> supplier, Object... arguments);

  int getOpenScrollcontextSize();
}
