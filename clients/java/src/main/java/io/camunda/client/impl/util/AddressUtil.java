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

import io.camunda.client.api.command.ClientException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class AddressUtil {
  public static final List<String> PLAINTEXT_SCHEMES = Arrays.asList("http", "grpc");
  public static final List<String> ENCRYPTED_SCHEMES = Arrays.asList("https", "grpcs");

  public static boolean isPlaintextConnection(final URI address) {
    if (address == null) {
      return true;
    }
    final String scheme = address.getScheme();
    if (PLAINTEXT_SCHEMES.contains(scheme)) {
      return true;
    } else if (ENCRYPTED_SCHEMES.contains(scheme)) {
      return false;
    } else {
      throw new ClientException(String.format("Unrecognized scheme '%s'", scheme));
    }
  }
}
