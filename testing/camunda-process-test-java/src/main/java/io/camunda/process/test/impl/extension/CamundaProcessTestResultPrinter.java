/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.extension;

import io.camunda.process.test.api.CamundaProcessTestExtension;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.TestWatcher;

public class CamundaProcessTestResultPrinter implements TestWatcher {

  @Override
  public void testFailed(final ExtensionContext context, final Throwable cause) {
    final ProcessTestResult processTestResult = getTestResult(context);
    System.err.println(processTestResult);
  }

  private static ProcessTestResult getTestResult(final ExtensionContext context) {
    final Store store = context.getStore(CamundaProcessTestExtension.NAMESPACE);
    final Object processTestResult = store.get(CamundaProcessTestExtension.STORE_KEY_TEST_RESULT);

    if (processTestResult == null) {
      throw new IllegalStateException("No process test result found");
    }

    return (ProcessTestResult) processTestResult;
  }
}
