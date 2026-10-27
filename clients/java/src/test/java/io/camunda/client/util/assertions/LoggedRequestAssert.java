/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.util.assertions;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;

public class LoggedRequestAssert extends AbstractAssert<LoggedRequestAssert, LoggedRequest> {

  private static final CamundaObjectMapper JSON_MAPPER = new CamundaObjectMapper();

  public LoggedRequestAssert(LoggedRequest actual) {
    super(actual, LoggedRequestAssert.class);
  }

  public static LoggedRequestAssert assertThat(LoggedRequest actual) {
    return new LoggedRequestAssert(actual);
  }

  public LoggedRequestAssert hasMethod(final RequestMethod method) {
    isNotNull();

    Assertions.assertThat(actual.getMethod())
        .describedAs("Expected request method to be <%s> but was <%s>", method, actual.getMethod())
        .isEqualTo(method);

    return this;
  }

  public LoggedRequestAssert hasUrl(final String url) {
    isNotNull();

    Assertions.assertThat(actual.getUrl())
        .describedAs("Expected request URL to be <%s> but was <%s>", url, actual.getUrl())
        .isEqualTo(url);

    return this;
  }

  @SafeVarargs
  public final <T> LoggedRequestAssert hasBodySatisfying(
      final Class<T> type, final Consumer<? super T>... requirements) {
    final T body = extractAndValidateBodyPresence(type);

    Assertions.assertThat(body)
        .describedAs(
            "Expected request body of type <%s> to satisfy the given requirements",
            type.getSimpleName())
        .satisfies(requirements);

    return this;
  }

  public <T> ObjectAssert<T> extractingBody(final Class<T> type) {
    final T body = extractAndValidateBodyPresence(type);

    return Assertions.assertThat(body);
  }

  private <T> T extractAndValidateBodyPresence(final Class<T> type) {
    isNotNull();

    Assertions.assertThat(actual.getBodyAsString())
        .describedAs("Expected request body to be non-blank")
        .isNotBlank();

    return JSON_MAPPER.fromJson(actual.getBodyAsString(), type);
  }

  public LoggedRequestAssert hasEmptyBody() {
    isNotNull();

    Assertions.assertThat(actual.getBodyAsString())
        .describedAs("Expected request body to be empty")
        .isEmpty();

    return this;
  }
}
