/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
