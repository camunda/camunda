/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import java.lang.invoke.MethodHandles;
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

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
