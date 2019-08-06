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
package io.zeebe.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.zeebe.client.CredentialsProvider;
import java.io.File;
import java.io.IOException;

public class ZeebeClientCredentialsProvider implements CredentialsProvider {

  public static final String INVALID_PATH_ERROR_MSG =
      "Expected valid path to Zeebe credentials but '%s' is either invalid or does not point to a file.";
  private static final TypeReference<ZeebeClientAuthInfo> TYPE_REF =
      new TypeReference<ZeebeClientAuthInfo>() {};
  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
  private static final Key<String> HEADER_AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private final String zeebeCredentialsPath;

  public ZeebeClientCredentialsProvider(String zeebeCredentialsPath) {
    if (!isValidCredentialsPath(zeebeCredentialsPath)) {
      throw new IllegalArgumentException(
          String.format(INVALID_PATH_ERROR_MSG, zeebeCredentialsPath));
    }

    this.zeebeCredentialsPath = zeebeCredentialsPath;
  }

  private boolean isValidCredentialsPath(String zeebeCredentialsPath) {
    if (zeebeCredentialsPath == null || zeebeCredentialsPath.isEmpty()) {
      return false;
    }

    final File credentialsFile = new File(zeebeCredentialsPath);
    return credentialsFile.exists();
  }

  /**
   * Adds a JSON Web Token (JWT) to the Authorization header of a gRPC call. The JWT is obtained
   * from a provided Zeebe credentials YAML file.
   */
  @Override
  public void applyCredentials(Metadata headers) {
    try {
      final ZeebeClientCredentials credentials = getAccessCredentials();

      headers.put(
          HEADER_AUTH_KEY,
          String.format(
              "%s %s", credentials.getTokenType().trim(), credentials.getAccessToken().trim()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ZeebeClientCredentials getAccessCredentials() throws IOException {
    JsonNode node = MAPPER.readTree(new File(zeebeCredentialsPath));

    // NOTE: currently the token structure doesn't have other fields but when it does this could be
    // improved
    node = node.get("endpoint").get("auth");
    final ZeebeClientAuthInfo clientAuthInfo = MAPPER.readValue(node.traverse(), TYPE_REF);

    return clientAuthInfo.getCredentials();
  }
}
