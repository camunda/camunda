/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/** This class helps to run test, that is Parameterized and full-featured Spring boot test. */
public class SpringParametersRunnerFactory implements ParametersRunnerFactory {
  @Override
  public Runner createRunnerForTestWithParameters(TestWithParameters test)
      throws InitializationError {
    final BlockJUnit4ClassRunnerWithParameters runnerWithParameters =
        new BlockJUnit4ClassRunnerWithParameters(test);
    return new SpringJUnit4ClassRunner(test.getTestClass().getJavaClass()) {
      @Override
      protected Object createTest() throws Exception {
        final Object testInstance = runnerWithParameters.createTest();
        getTestContextManager().prepareTestInstance(testInstance);
        return testInstance;
      }
    };
  }
}
