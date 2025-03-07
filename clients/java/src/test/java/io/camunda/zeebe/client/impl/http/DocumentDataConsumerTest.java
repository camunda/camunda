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
package io.camunda.zeebe.client.impl.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.protocol.rest.ProblemDetail;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.junit.jupiter.api.Test;

public class DocumentDataConsumerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testWithDocumentDataWithinBufferCapacity() throws IOException {
    // given
    final byte[] data = "Test content".getBytes();
    final DocumentDataConsumer<InputStream> consumer =
        new DocumentDataConsumer<>(data.length, objectMapper);
    final EntityDetails entityDetails = mock(EntityDetails.class);
    when(entityDetails.getContentType())
        .thenReturn(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
    when(entityDetails.getContentLength()).thenReturn((long) data.length);
    final Callback callback = spy(new Callback());

    // when
    consumer.streamStart(entityDetails, callback);

    // then
    // stream is returned immediately
    verify(callback).completed(any());
    assertThat(callback).isNotNull();

    consumer.consume(ByteBuffer.wrap(data));
    consumer.streamEnd(Collections.emptyList());

    final byte[] content = new byte[data.length];
    final int dataRead = callback.inputStream.read(content);
    assertThat(dataRead).isEqualTo(data.length);
    assertThat(content).isEqualTo(data);
    verifyNoMoreInteractions(callback);
  }

  @Test
  void testWithDocumentDataExceedingBufferCapacity() throws IOException {
    // given
    final byte[] data = "Test content".getBytes();
    final DocumentDataConsumer<InputStream> consumer =
        new DocumentDataConsumer<>(data.length - 1, objectMapper);
    final EntityDetails entityDetails = mock(EntityDetails.class);
    when(entityDetails.getContentType())
        .thenReturn(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
    when(entityDetails.getContentLength()).thenReturn((long) data.length);
    final Callback callback = spy(new Callback());

    // when
    consumer.streamStart(entityDetails, callback);

    // then
    // stream is returned immediately
    verify(callback).completed(any());
    assertThat(callback).isNotNull();

    final AtomicInteger reportedCapacity = spy(new AtomicInteger());
    consumer.updateCapacity(reportedCapacity::set);

    assertThat(reportedCapacity.get()).isEqualTo(data.length - 1);
    // error is thrown when data exceeds buffer capacity
    assertThrows(IOException.class, () -> consumer.consume(ByteBuffer.wrap(data)));

    final byte[] partialData = Arrays.copyOf(data, reportedCapacity.get());
    consumer.consume(ByteBuffer.wrap(partialData));

    consumer.updateCapacity(reportedCapacity::set);
    verify(reportedCapacity, times(1)).set(anyInt()); // capacity wasn't updated immediately

    final byte[] content = new byte[reportedCapacity.get()];
    final int dataRead = callback.inputStream.read(content);

    assertThat(dataRead).isEqualTo(reportedCapacity.get());
    assertThat(content).isEqualTo(partialData);
    assertThat(reportedCapacity.get()).isEqualTo(data.length - 1); // capacity is updated now

    final byte[] remainingData = Arrays.copyOfRange(data, reportedCapacity.get(), data.length);
    consumer.consume(ByteBuffer.wrap(remainingData));
    consumer.streamEnd(Collections.emptyList());
    final byte[] remainingContent = new byte[remainingData.length];
    final int remainingDataRead = callback.inputStream.read(remainingContent);
    assertThat(remainingDataRead).isEqualTo(remainingData.length);
    assertThat(remainingContent).isEqualTo(remainingData);

    verifyNoMoreInteractions(callback);
  }

  @Test
  void testProblemDetail() throws IOException {

    // given
    final String problemDetailResponse =
        "{\"type\":\"about:blank\",\"title\":\"Something went wrong\",\"status\":400,\"detail\":\"Invalid request\",\"instance\":\"/v1/entity/123\"}";
    final ByteBuffer byteBuffer = ByteBuffer.wrap(problemDetailResponse.getBytes());
    final DocumentDataConsumer<InputStream> consumer =
        new DocumentDataConsumer<>(256, objectMapper);
    final EntityDetails entityDetails = mock(EntityDetails.class);
    when(entityDetails.getContentType())
        .thenReturn(ContentType.APPLICATION_PROBLEM_JSON.getMimeType());
    final Callback callback = spy(new Callback());

    // when
    // Start the stream with content type application/problem+json
    consumer.streamStart(entityDetails, callback);
    verifyNoInteractions(callback); // stream is not returned immediately

    // Feed the data
    consumer.consume(byteBuffer);
    consumer.streamEnd(Collections.emptyList());

    verify(callback).completed(any());
    assertThat(callback.problemDetail).isNotNull();

    // then
    final ProblemDetail problemDetail = callback.problemDetail;
    assertThat(problemDetail).isNotNull();
    assertThat(problemDetail.getType()).isEqualTo(URI.create("about:blank"));
    assertThat(problemDetail.getTitle()).isEqualTo("Something went wrong");
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("Invalid request");
    assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/v1/entity/123"));
  }

  @Test
  void canReadSingleCharsFromStream() throws IOException {
    // given
    final byte[] data = "Test content".getBytes();
    final DocumentDataConsumer<InputStream> consumer =
        new DocumentDataConsumer<>(data.length, objectMapper);
    final EntityDetails entityDetails = mock(EntityDetails.class);
    when(entityDetails.getContentType())
        .thenReturn(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
    when(entityDetails.getContentLength()).thenReturn((long) data.length);
    final Callback callback = spy(new Callback());

    // when
    consumer.streamStart(entityDetails, callback);

    // then
    // stream is returned immediately
    verify(callback).completed(any());
    assertThat(callback).isNotNull();

    consumer.consume(ByteBuffer.wrap(data));
    consumer.streamEnd(Collections.emptyList());

    for (final byte b : data) {
      final byte[] content = new byte[1];
      final int dataRead = callback.inputStream.read(content);
      assertThat(dataRead).isEqualTo(1);
      assertThat(content[0]).isEqualTo(b);
    }

    assertThat(callback.inputStream.read()).isEqualTo(-1);

    verifyNoMoreInteractions(callback);
  }

  @Test
  void canReadMultipleCharsFromStream() throws IOException {
    // given
    final byte[] data = "Test content".getBytes();
    final DocumentDataConsumer<InputStream> consumer =
        new DocumentDataConsumer<>(data.length, objectMapper);
    final EntityDetails entityDetails = mock(EntityDetails.class);
    when(entityDetails.getContentType())
        .thenReturn(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
    when(entityDetails.getContentLength()).thenReturn((long) data.length);
    final Callback callback = spy(new Callback());

    // when
    consumer.streamStart(entityDetails, callback);

    // then
    // stream is returned immediately
    verify(callback).completed(any());
    assertThat(callback).isNotNull();

    consumer.consume(ByteBuffer.wrap(data));
    consumer.streamEnd(Collections.emptyList());

    final byte[] content = new byte[data.length];
    final int dataRead = callback.inputStream.read(content);
    assertThat(dataRead).isEqualTo(data.length);
    assertThat(content).isEqualTo(data);
    assertThat(callback.inputStream.read()).isEqualTo(-1);

    verifyNoMoreInteractions(callback);
  }

  @Test
  void canConsumeArbitraryContentType() throws IOException {
    // given
    final byte[] data = "Test content".getBytes();
    final DocumentDataConsumer<InputStream> consumer =
        new DocumentDataConsumer<>(data.length, objectMapper);
    final EntityDetails entityDetails = mock(EntityDetails.class);
    when(entityDetails.getContentType()).thenReturn("application/pdf");
    when(entityDetails.getContentLength()).thenReturn((long) data.length);
    final Callback callback = spy(new Callback());

    // when
    consumer.streamStart(entityDetails, callback);

    // then
    // stream is returned immediately
    verify(callback).completed(any());
    assertThat(callback).isNotNull();

    consumer.consume(ByteBuffer.wrap(data));
    consumer.streamEnd(Collections.emptyList());

    final byte[] content = new byte[data.length];
    final int dataRead = callback.inputStream.read(content);
    assertThat(dataRead).isEqualTo(data.length);
    assertThat(content).isEqualTo(data);
    assertThat(callback.inputStream.read()).isEqualTo(-1);

    verifyNoMoreInteractions(callback);
  }

  static class Callback implements FutureCallback<ApiEntity<InputStream>> {

    public InputStream inputStream;
    public ProblemDetail problemDetail;

    @Override
    public void completed(final ApiEntity<InputStream> result) {
      if (result instanceof ApiEntity.Response) {
        inputStream = result.response();
      } else if (result instanceof ApiEntity.Error) {
        problemDetail = result.problem();
      } else {
        throw new IllegalStateException("Unexpected result type: " + result.getClass());
      }
    }

    @Override
    public void failed(final Exception ex) {}

    @Override
    public void cancelled() {}
  }
}
