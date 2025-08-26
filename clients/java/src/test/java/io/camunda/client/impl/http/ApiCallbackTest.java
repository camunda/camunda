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
import static org.mockito.Mockito.*;

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.impl.http.ApiResponseConsumer.ApiResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiCallbackTest {

  public static final int DEFAULT_REMAINING_RETRIES = 2;
  private CompletableFuture<String> response;
  private JsonResponseAndStatusCodeTransformer<String, String> transformer;
  private Predicate<StatusCode> retryPredicate;
  private Predicate<Integer> successPredicate;
  private Runnable retryAction;
  private ApiCallback<String, String> apiCallback;

  @BeforeEach
  void setUp() {
    response = new CompletableFuture<>();
    transformer = mock(JsonResponseAndStatusCodeTransformer.class);
    retryPredicate = mock(Predicate.class);
    successPredicate = mock(Predicate.class);
    retryAction = mock(Runnable.class);
    apiCallback =
        new ApiCallback<>(
            response, transformer, null, retryPredicate, retryAction, DEFAULT_REMAINING_RETRIES);
  }

  @Test
  void shouldRetryWhenRetryPredicateIsTrue() {
    // given
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(500);
    when(retryPredicate.test(any())).thenReturn(true);

    // when
    apiCallback.completed(apiResponse);

    // then
    verify(retryAction, times(1)).run();
  }

  @Test
  void shouldNotRetryWhenRetryPredicateIsFalse() {
    // given
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(400);
    when(retryPredicate.test(any())).thenReturn(false);

    // when
    apiCallback.completed(apiResponse);

    // then
    verifyNoInteractions(retryAction);
    assertThat(response.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldNotRetryWhenNoRetriesLeft() {
    // given
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(500);
    when(retryPredicate.test(any())).thenReturn(true);

    // Exhaust retries
    for (int i = 0; i < DEFAULT_REMAINING_RETRIES; i++) {
      apiCallback.completed(apiResponse);
    }

    // when: another call, no retries left
    apiCallback.completed(apiResponse);

    // then: no new retry, future is exceptionally completed
    verify(retryAction, times(DEFAULT_REMAINING_RETRIES)).run();
    assertThat(response.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldReuseSameApiCallbackInstanceAcrossRetries() {
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(503);
    when(retryPredicate.test(any())).thenReturn(true);

    // First retry
    apiCallback.completed(apiResponse);
    verify(retryAction, times(1)).run();
    reset(retryAction);

    // Second retry
    apiCallback.completed(apiResponse);
    verify(retryAction, times(1)).run();
    reset(retryAction);

    // No retries left - should NOT call retryAction again
    apiCallback.completed(apiResponse);
    verifyNoInteractions(retryAction);
    assertThat(response.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldFailGracefullyAfterRetriesExhausted() {
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(500);
    when(retryPredicate.test(any())).thenReturn(true);

    // Exhaust retries
    for (int i = 0; i < DEFAULT_REMAINING_RETRIES; i++) {
      apiCallback.completed(apiResponse);
    }

    // Final attempt - should complete exceptionally, no further retry
    apiCallback.completed(apiResponse);
    assertThat(response.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldUseStatusCodeTransformerWhenNoResponseBody() throws Exception {
    // given
    when(successPredicate.test(503)).thenReturn(true);
    when(transformer.transform(null, 503)).thenReturn("DOWN");

    apiCallback =
        new ApiCallback<>(
            response,
            transformer,
            successPredicate,
            retryPredicate,
            retryAction,
            DEFAULT_REMAINING_RETRIES);

    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(503);
    when(apiResponse.entity()).thenReturn(null);

    // when
    apiCallback.completed(apiResponse);

    // then
    assertThat(response.isCompletedExceptionally()).isFalse();
    assertThat(response.get()).isEqualTo("DOWN");
    verify(transformer).transform(null, 503);
  }

  @Test
  void shouldRetryWithStatusCodeTransformerWhenRetryPredicateIsTrue() {
    // given
    when(successPredicate.test(500)).thenReturn(false);
    when(retryPredicate.test(any())).thenReturn(true);

    apiCallback =
        new ApiCallback<>(
            response,
            transformer,
            successPredicate,
            retryPredicate,
            retryAction,
            DEFAULT_REMAINING_RETRIES);

    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(500);
    when(apiResponse.entity()).thenReturn(null);

    // when
    apiCallback.completed(apiResponse);

    // then
    verify(retryAction, times(1)).run();
    assertThat(response.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldFailWithStatusCodeTransformerWhenRetryPredicateIsFalse() {
    // given
    when(successPredicate.test(400)).thenReturn(false);
    when(retryPredicate.test(any())).thenReturn(false);

    apiCallback =
        new ApiCallback<>(
            response,
            transformer,
            successPredicate,
            retryPredicate,
            retryAction,
            DEFAULT_REMAINING_RETRIES);

    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(400);
    when(apiResponse.getReasonPhrase()).thenReturn("Bad Request");
    when(apiResponse.entity()).thenReturn(null);

    // when
    apiCallback.completed(apiResponse);

    // then
    verifyNoInteractions(retryAction);
    assertThat(response.isCompletedExceptionally()).isTrue();
  }
}
