/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.util;

import io.camunda.operate.zeebeimport.CountImportListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TestImportListener extends CountImportListener {

  public void resetCounters() {
    importedCount = new AtomicInteger(0);
    scheduledCount = new AtomicInteger(0);
  }

}
