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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.impl.http.ApiResponseConsumer.ApiResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiCallbackTest {

  private CompletableFuture<String> response;
  private JsonResponseTransformer<String, String> transformer;
  private Predicate<StatusCode> retryPredicate;
  private Runnable retryAction;
  private ApiCallback<String, String> apiCallback;

  @BeforeEach
  void setUp() {
    response = new CompletableFuture<>();
    transformer = mock(JsonResponseTransformer.class);
    retryPredicate = mock(Predicate.class);
    retryAction = mock(Runnable.class);
    apiCallback = new ApiCallback<>(response, transformer, retryPredicate, retryAction);
  }

  @Test
  void shouldLimitRetries() {
    // Arrange
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(500);
    when(retryPredicate.test(any())).thenReturn(true);

    // Act
    apiCallback.completed(apiResponse);
    apiCallback.completed(apiResponse);
    apiCallback.completed(apiResponse);

    // Assert
    verify(retryAction, times(2)).run();
    assertTrue(response.isCompletedExceptionally());
  }
}
