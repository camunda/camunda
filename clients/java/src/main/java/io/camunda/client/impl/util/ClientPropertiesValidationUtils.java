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
package io.camunda.client.impl.util;

import java.net.URI;

public final class ClientPropertiesValidationUtils {

  private ClientPropertiesValidationUtils() {}

  /**
   * Validates that the provided address is an absolute URI.
   *
   * <p>We use {@code URI.getHost() == null} to check for absolute URIs because:
   *
   * <ul>
   *   <li>For absolute URIs (with a scheme) (e.g., "https://example.com"), {@code URI.getHost()}
   *       returns the hostname (e.g., "example.com").
   *   <li>For relative URIs (without a scheme) (e.g., "example.com"), {@code URI.getHost()} returns
   *       {@code null}.
   * </ul>
   *
   * throws IllegalArgumentException if the provided address is not an absolute URI
   */
  public static void checkIfUriIsAbsolute(final URI address, final String propertyName) {
    if (address != null && address.getHost() == null) {
      throw new IllegalArgumentException(
          String.format("'%s' must be an absolute URI", propertyName));
    }
  }
}
