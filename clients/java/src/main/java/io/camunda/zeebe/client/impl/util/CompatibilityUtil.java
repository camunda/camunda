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
package io.camunda.zeebe.client.impl.util;

import io.camunda.client.CredentialsProvider.StatusCode;
import java.util.function.Predicate;

public final class CompatibilityUtil {

  public static Predicate<StatusCode> convert(
      final Predicate<io.camunda.zeebe.client.CredentialsProvider.StatusCode> source) {
    return statusCode -> source.test(convert(statusCode));
  }

  public static io.camunda.zeebe.client.CredentialsProvider.StatusCode convert(
      final StatusCode statusCode) {
    return new io.camunda.zeebe.client.CredentialsProvider.StatusCode() {
      @Override
      public int code() {
        return statusCode.code();
      }

      @Override
      public boolean isUnauthorized() {
        return statusCode.isUnauthorized();
      }
    };
  }
}
