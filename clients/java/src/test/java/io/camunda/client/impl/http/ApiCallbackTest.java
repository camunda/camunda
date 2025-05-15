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

import static org.mockito.Mockito.*;

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.impl.http.ApiResponseConsumer.ApiResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiCallbackTest {

  public static final int DEFAULT_REMAINING_RETRIES = 2;
  private CompletableFuture<String> response;
  private JsonResponseTransformer<String, String> transformer;
  private Predicate<StatusCode> retryPredicate;
  private Consumer<Integer> retryAction;
  private ApiCallback<String, String> apiCallback;

  @BeforeEach
  void setUp() {
    response = new CompletableFuture<>();
    transformer = mock(JsonResponseTransformer.class);
    retryPredicate = mock(Predicate.class);
    retryAction = mock(Consumer.class);
    apiCallback =
        new ApiCallback<>(
            response, transformer, retryPredicate, retryAction, DEFAULT_REMAINING_RETRIES);
  }

  @Test
  void shouldRetryWhenRetryPredicateIsTrueWithDecrementedRetries() {
    // given
    final ApiResponse<String> apiResponse = mock(ApiResponse.class);
    when(apiResponse.getCode()).thenReturn(500);
    when(retryPredicate.test(any())).thenReturn(true);

    // when
    apiCallback.completed(apiResponse);

    // then
    verify(retryAction, times(1)).accept(eq(DEFAULT_REMAINING_RETRIES - 1));
  }
}
