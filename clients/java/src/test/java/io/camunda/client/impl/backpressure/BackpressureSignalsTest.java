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
package io.camunda.client.impl.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.impl.backpressure.BackpressureSignals.Failure;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class BackpressureSignalsTest {

  @ParameterizedTest
  @CsvSource({
    "429, , , BACKPRESSURE",
    "429, Too Many Requests, , BACKPRESSURE",
    "503, RESOURCE_EXHAUSTED, , BACKPRESSURE",
    "503, RESOURCE_EXHAUSTED, partition is busy, BACKPRESSURE",
    "500, RESOURCE_EXHAUSTED, , BACKPRESSURE",
    "500, , 'Expected to handle the request, but RESOURCE_EXHAUSTED', BACKPRESSURE",
    "500, 'RESOURCE_EXHAUSTED from broker', , BACKPRESSURE"
  })
  void shouldRecognizeBackpressureOverHttp(
      final int status, final String title, final String detail, final Failure expected) {
    assertThat(BackpressureSignals.classifyHttp(status, title, detail)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "500, , ",
    "500, INTERNAL, 'Unexpected error occurred'",
    "503, , ",
    "503, UNAVAILABLE, ",
    "503, UNAVAILABLE, 'detail mentions RESOURCE_EXHAUSTED'",
    "503, Service Unavailable, ",
    "400, RESOURCE_EXHAUSTED, RESOURCE_EXHAUSTED",
    "401, , ",
    "404, , ",
    "409, , ",
    "502, , ",
    "504, , ",
    "200, , "
  })
  void shouldNotRetryAnythingElseOverHttp(
      final int status, final String title, final String detail) {
    assertThat(BackpressureSignals.classifyHttp(status, title, detail))
        .isEqualTo(Failure.PERMANENT);
  }

  @Test
  void shouldRecognizeBackpressureOverGrpc() {
    assertThat(BackpressureSignals.classifyGrpc(Status.RESOURCE_EXHAUSTED))
        .isEqualTo(Failure.BACKPRESSURE);
    assertThat(
            BackpressureSignals.classifyGrpc(
                Status.RESOURCE_EXHAUSTED.withDescription("Partition 1 is receiving too many")))
        .isEqualTo(Failure.BACKPRESSURE);
  }

  @Test
  void shouldNotTreatMessageSizeLimitsAsBackpressure() {
    assertThat(
            BackpressureSignals.classifyGrpc(
                Status.RESOURCE_EXHAUSTED.withDescription(
                    "gRPC message exceeds maximum size 4194304: 5000000")))
        .isEqualTo(Failure.PERMANENT);
  }

  @Test
  void shouldNotRetryOtherGrpcStatuses() {
    for (final Status.Code code : Status.Code.values()) {
      if (code != Status.Code.RESOURCE_EXHAUSTED) {
        assertThat(BackpressureSignals.classifyGrpc(code.toStatus()))
            .describedAs(code.name())
            .isEqualTo(Failure.PERMANENT);
      }
    }
  }
}
