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
package io.camunda.spring.client;

import io.camunda.client.impl.command.StreamUtil;
import java.io.IOException;
import java.io.InputStream;

public class TestUtil {
  public static byte[] getBytes(final String filename) {
    try (final InputStream is = TestUtil.class.getResourceAsStream(filename)) {
      return StreamUtil.readInputStream(is);
    } catch (final IOException e) {
      throw new AssertionError("Failed to read bytes of file: " + filename, e);
    }
  }
}
