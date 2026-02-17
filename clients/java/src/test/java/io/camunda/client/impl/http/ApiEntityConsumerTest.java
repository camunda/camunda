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
package io.camunda.client.impl.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.camunda.client.impl.http.ApiEntity.Error;
import io.camunda.client.impl.http.ApiEntity.Response;
import io.camunda.client.impl.http.ApiEntity.Unknown;
import io.camunda.client.protocol.rest.ProblemDetail;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

class ApiEntityConsumerTest {

  public static final ObjectMapper JSON_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void testJsonApiEntityConsumerWithValidJsonResponse() throws IOException {
    // given
    final String jsonResponse = "{\"name\":\"test\",\"value\":123}";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(jsonResponse.getBytes());
    final ApiEntityConsumer<TestEntity> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, TestEntity.class, 2048);

    // when
    // Start the stream with the correct content type
    consumer.streamStart(ContentType.APPLICATION_JSON);
    // Feed the data
    consumer.data(byteBuffer, true);
    // Generate the content
    final ApiEntity<TestEntity> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Response.class);
    final TestEntity response = entity.response();
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo("test");
    assertThat(response.getValue()).isEqualTo(123);
  }

  @Test
  void testJsonApiEntityConsumerWithValidJsonOtherTypeResponse() throws IOException {
    // given
    final String jsonResponse = "{\"foo\":\"test\",\"bar\":123}";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(jsonResponse.getBytes());
    final ApiEntityConsumer<TestEntity> consumer =
        new ApiEntityConsumer<>(new ObjectMapper(), TestEntity.class, 2048);

    // when
    // Start the stream with the correct content type
    consumer.streamStart(ContentType.APPLICATION_JSON);
    // Feed the data
    consumer.data(byteBuffer, true);

    // when-then Generate the content
    assertThatThrownBy(consumer::generateContent).isInstanceOf(UnrecognizedPropertyException.class);
  }

  @Test
  void testJsonApiEntityConsumerWithValidJsonOtherTypeErrorResponse() throws IOException {
    // given
    final String jsonResponse = "{\"foo\":\"test\",\"bar\":123}";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(jsonResponse.getBytes());
    final ApiEntityConsumer<TestEntity> consumer =
        new ApiEntityConsumer<>(new ObjectMapper(), TestEntity.class, 2048);

    // when
    // Start the stream with the correct content type
    consumer.streamStart(ContentType.APPLICATION_PROBLEM_JSON);
    // Feed the data
    consumer.data(byteBuffer, true);

    // when-then Generate the content
    assertThatThrownBy(consumer::generateContent).isInstanceOf(UnrecognizedPropertyException.class);
  }

  @Test
  void testJsonApiEntityConsumerWithProblemDetailResponse() throws IOException {
    // given
    final String problemDetailResponse =
        "{\"type\":\"about:blank\",\"title\":\"Something went wrong\",\"status\":400,\"detail\":\"Invalid request\",\"instance\":\"/v1/entity/123\"}";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(problemDetailResponse.getBytes());
    final ApiEntityConsumer<ProblemDetail> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, ProblemDetail.class, 2048);

    // when
    // Start the stream with content type application/problem+json
    consumer.streamStart(ContentType.APPLICATION_PROBLEM_JSON);
    // Feed the data
    consumer.data(byteBuffer, true);
    // Generate the content
    final ApiEntity<ProblemDetail> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Error.class);
    final ProblemDetail problemDetail = entity.problem();
    assertThat(problemDetail).isNotNull();
    assertThat(problemDetail.getType()).isEqualTo(URI.create("about:blank"));
    assertThat(problemDetail.getTitle()).isEqualTo("Something went wrong");
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("Invalid request");
    assertThat(problemDetail.getInstance()).isEqualTo("/v1/entity/123");
  }

  @Test
  void testRawApiEntityConsumerWithTextXmlResponse() throws IOException {
    // given
    final String textXmlResponse = "<xml/>";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(textXmlResponse.getBytes());
    final ApiEntityConsumer<String> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, String.class, 2048);

    // when
    // Start the stream with a supported text content type (text/xml)
    consumer.streamStart(ContentType.TEXT_XML);
    // Feed the data
    consumer.data(byteBuffer, true);
    // Generate the content (should return the plain text as a string)
    final ApiEntity<String> entity = consumer.generateContent();

    assertThat(entity).isInstanceOf(Response.class);
    assertThat(entity.response()).isEqualTo(textXmlResponse);
  }

  @Test
  void testRawApiEntityConsumerWithTextXmlResponseContainingUTF8Chars() throws IOException {
    // given
    final String textXmlResponse =
        "<xml>Thís ís á UTF-8 text wíth specíal cháracters: €, ñ, ö, 测试</xml>";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(textXmlResponse.getBytes());
    final ApiEntityConsumer<String> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, String.class, 2048);

    // when
    // Start the stream with a supported text content type (text/xml)
    consumer.streamStart(ContentType.TEXT_XML);
    // Feed the data
    consumer.data(byteBuffer, true);
    // Generate the content (should return the plain text as a string)
    final ApiEntity<String> entity = consumer.generateContent();

    assertThat(entity).isInstanceOf(Response.class);
    assertThat(entity.response()).isEqualTo(textXmlResponse);
  }

  @Test
  void testJsonApiEntityConsumerWithPartialData() throws IOException {
    // given
    final String part1 = "{\"name\":\"t";
    final String part2 = "est\",\"value\":123}";
    final ByteBuffer byteBuffer1 = ByteBuffer.wrap(part1.getBytes(StandardCharsets.UTF_8));
    final ByteBuffer byteBuffer2 = ByteBuffer.wrap(part2.getBytes(StandardCharsets.UTF_8));
    final ApiEntityConsumer<TestEntity> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, TestEntity.class, 2048);

    // when
    // Start the stream with a supported content type (application/json)
    consumer.streamStart(ContentType.APPLICATION_JSON);
    // Feed the first part of the data
    consumer.data(byteBuffer1, false);
    // Feed the second part of the data
    consumer.data(byteBuffer2, true);
    // Generate the content (should parse and combine both parts)
    final ApiEntity<TestEntity> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Response.class);
    assertThat(entity.response().getName()).isEqualTo("test");
    assertThat(entity.response().getValue()).isEqualTo(123);
  }

  @Test
  void testRawApiEntityConsumerWithPartialData() throws IOException {
    // given
    final String part1 = "<xml>";
    final String part2 = "</xml>>";
    final ByteBuffer byteBuffer1 = ByteBuffer.wrap(part1.getBytes());
    final ByteBuffer byteBuffer2 = ByteBuffer.wrap(part2.getBytes());
    final ApiEntityConsumer<String> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, String.class, 2048);

    // when
    // Start the stream with a supported content type (text/xml)
    consumer.streamStart(ContentType.TEXT_XML);
    // Feed the first part of the data
    consumer.data(byteBuffer1, false);
    // Feed the second part of the data
    consumer.data(byteBuffer2, true);
    // Generate the content (should concatenate both parts)
    final ApiEntity<String> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Response.class);
    assertThat(entity.response()).isEqualTo(part1 + part2);
  }

  @Test
  void testRawApiEntityConsumerWithHtmlData() throws IOException {
    // given
    final String part1 = "<html>";
    final String part2 = "</html>>";
    final ByteBuffer byteBuffer1 = ByteBuffer.wrap(part1.getBytes());
    final ByteBuffer byteBuffer2 = ByteBuffer.wrap(part2.getBytes());
    final ApiEntityConsumer<String> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, String.class, 2048);

    // when
    // Start the stream with an unsupported content type
    consumer.streamStart(ContentType.TEXT_HTML);
    // Feed the first part of the data
    consumer.data(byteBuffer1, false);
    // Feed the second part of the data
    consumer.data(byteBuffer2, true);
    // Generate the content (should return raw data as ByteBuffer)
    final ApiEntity<String> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Response.class);
    assertThat(entity.response()).isEqualTo(part1 + part2);
  }

  @Test
  void testRawApiEntityConsumerWithPlainText() throws IOException {
    // given
    final String plain = "Just some plain text.";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(plain.getBytes());
    final ApiEntityConsumer<String> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, String.class, 2048);

    // when
    // Start the stream with an unsupported content type
    consumer.streamStart(ContentType.TEXT_PLAIN);
    consumer.data(byteBuffer, true);
    // Generate the content (should return raw data as ByteBuffer)
    final ApiEntity<String> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Response.class);
    assertThat(entity.response()).isEqualTo(plain);
  }

  @Test
  void testUnknownContentTypeWhenExpectedResponseIsNotAString() throws IOException {
    // given
    final String unsupportedData = "<unexpected/>";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(unsupportedData.getBytes());
    final ApiEntityConsumer<TestEntity> consumer =
        new ApiEntityConsumer<>(JSON_MAPPER, TestEntity.class, 2048);

    // when
    // Start the stream with a supported content type
    consumer.streamStart(ContentType.TEXT_XML);
    // Feed the data
    consumer.data(byteBuffer, true);
    // Generate the content (should return raw data as ByteBuffer)
    final ApiEntity<TestEntity> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Unknown.class);
    assertThat(StandardCharsets.UTF_8.decode(entity.unknown()).toString())
        .isEqualTo(unsupportedData);
  }

  @Test
  void testVoidTypeWithApplicationJson() throws IOException {
    // given

    final ApiEntityConsumer<Void> consumer = new ApiEntityConsumer<>(JSON_MAPPER, Void.class, 2048);

    // when
    // Start the stream with application/json content type
    consumer.streamStart(ContentType.APPLICATION_JSON);
    // Generate the content
    final ApiEntity<Void> entity = consumer.generateContent();

    // then
    assertThat(entity).isNull();
  }

  @Test
  void shouldProcessProblemDetailsEvenIfTheExpectedResponseWasVoid() throws IOException {
    // given
    final String problemDetailResponse =
        "{\"type\":\"about:blank\",\"title\":\"Something went wrong\",\"status\":400,\"detail\":\"Invalid request\",\"instance\":\"/v1/entity/123\"}";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(problemDetailResponse.getBytes());
    final ApiEntityConsumer<Void> consumer = new ApiEntityConsumer<>(JSON_MAPPER, Void.class, 2048);

    // when
    // Start the stream with application/problem+json content type
    consumer.streamStart(ContentType.APPLICATION_PROBLEM_JSON);
    // Feed the data
    consumer.data(byteBuffer, true);
    // Generate the content
    final ApiEntity<Void> entity = consumer.generateContent();

    // then
    assertThat(entity).isInstanceOf(Error.class);
    final ProblemDetail problemDetail = entity.problem();
    assertThat(problemDetail).isNotNull();
    assertThat(problemDetail.getType()).isEqualTo(URI.create("about:blank"));
    assertThat(problemDetail.getTitle()).isEqualTo("Something went wrong");
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("Invalid request");
    assertThat(problemDetail.getInstance()).isEqualTo("/v1/entity/123");
  }

  // Test entity class used for JSON serialization/deserialization
  static class TestEntity {
    private String name;
    private int value;

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public int getValue() {
      return value;
    }

    public void setValue(final int value) {
      this.value = value;
    }
  }
}
