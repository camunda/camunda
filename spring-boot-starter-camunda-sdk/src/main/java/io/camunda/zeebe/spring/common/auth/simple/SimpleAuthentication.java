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
package io.camunda.zeebe.spring.common.auth.simple;

import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.Product;
import java.util.*;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleAuthentication implements Authentication {

  private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthentication.class);

  private final SimpleConfig simpleConfig;
  private final Map<Product, String> tokens = new HashMap<>();

  private final String authUrl;

  public SimpleAuthentication(final String simpleUrl, final SimpleConfig simpleConfig) {
    this.simpleConfig = simpleConfig;
    authUrl = simpleUrl + "/api/login";
  }

  public SimpleConfig getSimpleConfig() {
    return simpleConfig;
  }

  public static SimpleAuthenticationBuilder builder() {
    return new SimpleAuthenticationBuilder();
  }

  private String retrieveToken(final Product product, final SimpleCredential simpleCredential) {
    try (final CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpPost request = buildRequest(simpleCredential);
      final String cookie =
          client.execute(
              request,
              response -> {
                final Header[] cookieHeaders = response.getHeaders("Set-Cookie");
                String cookieCandidate = null;
                final String cookiePrefix = product.toString().toUpperCase() + "-SESSION";
                for (final Header cookieHeader : cookieHeaders) {
                  if (cookieHeader.getValue().startsWith(cookiePrefix)) {
                    cookieCandidate = response.getHeader("Set-Cookie").getValue();
                    break;
                  }
                }
                return cookieCandidate;
              });
      if (cookie == null) {
        throw new RuntimeException("Unable to authenticate due to missing Set-Cookie");
      }
      tokens.put(product, cookie);
    } catch (final Exception e) {
      LOG.error("Authenticating for " + product + " failed due to " + e);
      throw new RuntimeException("Unable to authenticate", e);
    }
    return tokens.get(product);
  }

  private HttpPost buildRequest(final SimpleCredential simpleCredential) {
    final HttpPost httpPost = new HttpPost(authUrl);
    final List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("username", simpleCredential.getUser()));
    params.add(new BasicNameValuePair("password", simpleCredential.getPassword()));
    httpPost.setEntity(new UrlEncodedFormEntity(params));
    return httpPost;
  }

  @Override
  public Map.Entry<String, String> getTokenHeader(final Product product) {
    final String token;
    if (tokens.containsKey(product)) {
      token = tokens.get(product);
    } else {
      final SimpleCredential simpleCredential = simpleConfig.getProduct(product);
      token = retrieveToken(product, simpleCredential);
    }

    return new AbstractMap.SimpleEntry<>("Cookie", token);
  }

  @Override
  public void resetToken(final Product product) {
    tokens.remove(product);
  }
}
