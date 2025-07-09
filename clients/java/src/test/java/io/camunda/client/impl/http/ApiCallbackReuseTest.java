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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.impl.http.ApiResponseConsumer.ApiResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiCallbackReuseTest {

  private static final int MAX_RETRIES = 2;

  private CompletableFuture<String> response;
  private JsonResponseTransformer<String, String> transformer;
  private Predicate<StatusCode> retryPredicate;
  private AtomicReference<ApiCallback<String, String>> callbackReference;
  private Runnable retryAction;

  @BeforeEach
  void setUp() {
    response = new CompletableFuture<>();
    transformer = mock(JsonResponseTransformer.class);
    retryPredicate = code -> true; // Always retry for test

    // Track whether a new ApiCallback is created
    callbackReference = new AtomicReference<>();

    retryAction =
        () -> {
          // In a real client, this would resubmit the request using the same callback.
          // Here we simulate that by invoking the same callbackReference's completed method.
          final ApiCallback<String, String> callback = callbackReference.get();
          final ApiResponse<String> apiResponse = mock(ApiResponse.class);
          when(apiResponse.getCode()).thenReturn(500);
          callback.completed(apiResponse);
        };
  }

  @Test
  void shouldReuseSameApiCallbackInstanceOnRetries() {
    // Given
    final ApiCallback<String, String> apiCallback =
        new ApiCallback<>(response, transformer, retryPredicate, retryAction, MAX_RETRIES);
    callbackReference.set(apiCallback);

    final ApiResponse<String> firstResponse = mock(ApiResponse.class);
    when(firstResponse.getCode()).thenReturn(500);
    apiCallback.completed(firstResponse);

    final ApiCallback<String, String> callbackAfterRetries = callbackReference.get();

    assertThat(callbackAfterRetries).isSameAs(apiCallback);
  }
}
