/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.webapp.rest.exception.Error;
import org.assertj.core.api.AbstractAssert;
import org.springframework.http.HttpStatus;

public class ErrorAssert extends AbstractAssert<ErrorAssert, Error> {

  public ErrorAssert(Error actual) {
    super(actual, ErrorAssert.class);
  }

  public ErrorAssert hasStatus(HttpStatus expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.getStatus()).isEqualTo(expected.value());
    return this;
  }

  public ErrorAssert hasInstanceId() {
    assertThat(actual).isNotNull();
    assertThat(actual.getInstance()).isNotBlank();
    return this;
  }

  public ErrorAssert hasMessage(String expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.getMessage()).isEqualTo(expected);
    return this;
  }

  public ErrorAssert hasMessage(String expectedStringTemplate, Object... args) {
    assertThat(actual).isNotNull();
    assertThat(actual.getMessage()).isEqualTo(expectedStringTemplate, args);
    return this;
  }

  public ErrorAssert hasMessageStartingWith(String expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.getMessage()).startsWith(expected);
    return this;
  }

  public ErrorAssert hasMessageStartingWith(String expectedStringTemplate, Object... args) {
    assertThat(actual).isNotNull();
    assertThat(actual.getMessage()).startsWith(String.format(expectedStringTemplate, args));
    return this;
  }
}
