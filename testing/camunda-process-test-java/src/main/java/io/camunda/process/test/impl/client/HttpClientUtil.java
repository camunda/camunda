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
package io.camunda.process.test.impl.client;

import java.util.Optional;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public class HttpClientUtil {

  public static String getResponseAsString(final ClassicHttpResponse response) {
    return Optional.ofNullable(response.getEntity())
        .map(
            entity -> {
              try {
                return EntityUtils.toString(entity);
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            })
        .orElse("<empty>");
  }
}
