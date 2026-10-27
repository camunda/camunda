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
import io.netty.util.NetUtil;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import org.apache.hc.core5.net.URIBuilder;

public final class AddressUtil {
  public static final List<String> PLAINTEXT_SCHEMES = Arrays.asList("http", "grpc");
  public static final List<String> ENCRYPTED_SCHEMES = Arrays.asList("https", "grpcs");

  private AddressUtil() {}

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

  public static String composeGatewayAddress(final URI grpcAddress) {
    final int port = grpcAddress.getPort();
    if (port == -1) {
      return grpcAddress.getHost();
    }
    return String.format("%s:%d", grpcAddress.getHost(), port);
  }

  public static URI composeGrpcAddress(
      final InetSocketAddress gatewayAddress, final boolean plaintext) {
    return composeGrpcAddress(NetUtil.toSocketAddressString(gatewayAddress), plaintext);
  }

  public static URI composeGrpcAddress(final String gatewayAddress, final boolean plaintext) {
    final String composedGrpcAddress = String.format("%s://%s", scheme(plaintext), gatewayAddress);
    return URI.create(composedGrpcAddress);
  }

  public static String scheme(final boolean plaintext) {
    return plaintext ? PLAINTEXT_SCHEMES.get(0) : ENCRYPTED_SCHEMES.get(0);
  }

  public static URI composeAddress(final boolean plaintext, final URI address) {
    final String scheme = scheme(plaintext);
    try {
      return new URIBuilder(address).setScheme(scheme).build();
    } catch (final URISyntaxException e) {
      throw new ClientException("Error while composing address", e);
    }
  }
}
