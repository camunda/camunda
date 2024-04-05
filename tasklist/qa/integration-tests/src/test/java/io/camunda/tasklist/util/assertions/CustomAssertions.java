/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util.assertions;

import io.camunda.tasklist.webapp.rest.exception.Error;
import org.springframework.mock.web.MockHttpServletResponse;

public class CustomAssertions {
  public static MockHttpServletResponseAssert assertThat(MockHttpServletResponse actual) {
    return new MockHttpServletResponseAssert(actual);
  }

  public static ErrorAssert assertThat(Error actual) {
    return new ErrorAssert(actual);
  }
}
