/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class TasklistIndexPrefixHolder {

  private String indexPrefix;
  private volatile boolean needsCleanUp;

  public String createNewIndexPrefix() {
    indexPrefix = TestUtil.createRandomString(10);
    needsCleanUp = true;
    return indexPrefix;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void cleanupIndicesIfNeeded(final Consumer<String> indexCleanup) {
    if (needsCleanUp && indexPrefix != null) {
      indexCleanup.accept(indexPrefix);
      needsCleanUp = false;
    }
  }
}
