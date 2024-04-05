/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util.assertions;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.webapp.rest.exception.Error;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

public class MockHttpServletResponseAssert
    extends AbstractAssert<MockHttpServletResponseAssert, MockHttpServletResponse> {

  public MockHttpServletResponseAssert(MockHttpServletResponse actual) {
    super(actual, MockHttpServletResponseAssert.class);
  }

  public MockHttpServletResponseAssert hasHttpStatus(HttpStatus expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.getStatus()).isEqualTo(expected.value());
    return this;
  }

  public MockHttpServletResponseAssert hasOkHttpStatus() {
    return hasHttpStatus(HttpStatus.OK);
  }

  public MockHttpServletResponseAssert hasContentType(MediaType expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.getContentType()).isNotNull();
    assertThat(MediaType.parseMediaType(actual.getContentType())).isEqualTo(expected);
    return this;
  }

  public MockHttpServletResponseAssert hasApplicationJsonContentType() {
    return hasContentType(MediaType.APPLICATION_JSON);
  }

  public MockHttpServletResponseAssert hasApplicationProblemJsonContentType() {
    return hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  public MockHttpServletResponseAssert hasNoContent() {
    final var contentAsString = getContentAsString();
    assertThat(contentAsString).isEmpty();
    assertThat(actual.getContentType()).isNull();
    assertThat(actual.getContentLength()).isZero();
    return this;
  }

  public <T> ListAssert<T> extractingListContent(ObjectMapper extractor, Class<T> type) {
    final var contentAsString = getContentAsString();

    List<T> listResponse = null;
    try {
      listResponse =
          extractor.readValue(
              contentAsString,
              extractor.getTypeFactory().constructCollectionType(ArrayList.class, type));
    } catch (JsonProcessingException e) {
      fail(
          String.format(
              "Error during converting string content\n\"%s\"\nto List<%s>",
              contentAsString, type.getSimpleName()),
          e);
    }

    return assertThat(listResponse);
  }

  public <T> ObjectAssert<T> extractingContent(ObjectMapper extractor, Class<T> type) {
    final T response = getContent(extractor, type);
    return assertThat(response);
  }

  public ErrorAssert extractingErrorContent(ObjectMapper extractor) {
    final Error response = getContent(extractor, Error.class);
    return assertThat(response);
  }

  private <T> T getContent(ObjectMapper extractor, Class<T> type) {
    final var contentAsString = getContentAsString();

    T response = null;
    try {
      response = extractor.readValue(contentAsString, type);
    } catch (JsonProcessingException e) {
      fail(
          String.format(
              "Error during converting string content\n\"%s\"\nto <%s>",
              contentAsString, type.getSimpleName()),
          e);
    }
    return response;
  }

  @NotNull
  private String getContentAsString() {
    assertThat(actual).isNotNull();
    String contentAsString = null;
    try {
      contentAsString = actual.getContentAsString();
    } catch (UnsupportedEncodingException e) {
      fail("Error during extracting content from response:", e);
    }
    assertThat(contentAsString).isNotNull();
    return contentAsString;
  }
}
