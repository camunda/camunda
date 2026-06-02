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
package io.camunda.client.impl.secret;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.secret.SecretsClient;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a {@link JsonMapper} and transparently resolves {@code camunda.secrets.*} references found
 * in deserialized variable payloads.
 *
 * <p>References are detected via substring match so concatenated forms such as {@code "Bearer
 * camunda.secrets.TOKEN"} are also resolved. Each {@code fromJson*} call performs at most one batch
 * round-trip to the gateway. Serialization ({@code toJson}) and validation are delegated unchanged.
 */
public final class SecretResolvingJsonMapper implements JsonMapper {

  private static final Pattern REFERENCE_PATTERN =
      Pattern.compile("camunda\\.secrets\\.[A-Za-z0-9_]+");

  private final JsonMapper delegate;
  private final SecretsClient secretsClient;

  public SecretResolvingJsonMapper(final JsonMapper delegate, final SecretsClient secretsClient) {
    this.delegate = delegate;
    this.secretsClient = secretsClient;
  }

  @Override
  public <T> T fromJson(final String json, final Class<T> typeClass) {
    if (json == null || !containsReference(json)) {
      return delegate.fromJson(json, typeClass);
    }
    final Set<String> refs = collectReferences(json);
    final Map<String, String> resolved = secretsClient.resolve(new ArrayList<String>(refs));
    if (resolved.isEmpty()) {
      return delegate.fromJson(json, typeClass);
    }
    return delegate.fromJson(spliceJson(json, resolved), typeClass);
  }

  @Override
  public Map<String, Object> fromJsonAsMap(final String json) {
    final Map<String, Object> map = delegate.fromJsonAsMap(json);
    return resolveInMap(map);
  }

  @Override
  public Map<String, String> fromJsonAsStringMap(final String json) {
    final Map<String, String> map = delegate.fromJsonAsStringMap(json);
    final Set<String> refs = new HashSet<String>();
    for (final String value : map.values()) {
      collectReferences(value, refs);
    }
    if (refs.isEmpty()) {
      return map;
    }
    final Map<String, String> resolved = secretsClient.resolve(new ArrayList<String>(refs));
    if (resolved.isEmpty()) {
      return map;
    }
    final Map<String, String> result = new HashMap<String, String>(map.size());
    for (final Map.Entry<String, String> entry : map.entrySet()) {
      result.put(entry.getKey(), spliceString(entry.getValue(), resolved));
    }
    return result;
  }

  @Override
  public String toJson(final Object value) {
    return delegate.toJson(value);
  }

  @Override
  public String validateJson(final String propertyName, final String jsonInput) {
    return delegate.validateJson(propertyName, jsonInput);
  }

  @Override
  public String validateJson(final String propertyName, final InputStream jsonInput) {
    return delegate.validateJson(propertyName, jsonInput);
  }

  private Map<String, Object> resolveInMap(final Map<String, Object> map) {
    final Set<String> refs = new HashSet<String>();
    collectReferences(map, refs);
    if (refs.isEmpty()) {
      return map;
    }
    final Map<String, String> resolved = secretsClient.resolve(new ArrayList<String>(refs));
    if (resolved.isEmpty()) {
      return map;
    }
    return spliceMap(map, resolved);
  }

  // ---- detection ----------------------------------------------------------

  private static boolean containsReference(final String s) {
    return s != null && s.contains("camunda.secrets.");
  }

  private static Set<String> collectReferences(final String json) {
    final Set<String> refs = new HashSet<String>();
    final Matcher matcher = REFERENCE_PATTERN.matcher(json);
    while (matcher.find()) {
      refs.add(matcher.group());
    }
    return refs;
  }

  private static void collectReferences(final Object value, final Set<String> refs) {
    if (value instanceof String) {
      final Matcher matcher = REFERENCE_PATTERN.matcher((String) value);
      while (matcher.find()) {
        refs.add(matcher.group());
      }
    } else if (value instanceof Map) {
      for (final Object v : ((Map<?, ?>) value).values()) {
        collectReferences(v, refs);
      }
    } else if (value instanceof Iterable) {
      for (final Object v : (Iterable<?>) value) {
        collectReferences(v, refs);
      }
    }
  }

  // ---- splicing -----------------------------------------------------------

  private static String spliceString(final String value, final Map<String, String> resolved) {
    if (value == null || !value.contains("camunda.secrets.")) {
      return value;
    }
    final Matcher matcher = REFERENCE_PATTERN.matcher(value);
    final StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      final String ref = matcher.group();
      final String replacement = resolved.get(ref);
      matcher.appendReplacement(
          buffer, Matcher.quoteReplacement(replacement != null ? replacement : ref));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  @SuppressWarnings("unchecked")
  private static Object spliceValue(final Object value, final Map<String, String> resolved) {
    if (value instanceof String) {
      return spliceString((String) value, resolved);
    }
    if (value instanceof Map) {
      return spliceMap((Map<String, Object>) value, resolved);
    }
    if (value instanceof List) {
      final List<?> list = (List<?>) value;
      final List<Object> out = new ArrayList<Object>(list.size());
      for (final Object v : list) {
        out.add(spliceValue(v, resolved));
      }
      return out;
    }
    return value;
  }

  private static Map<String, Object> spliceMap(
      final Map<String, Object> map, final Map<String, String> resolved) {
    final Map<String, Object> out = new HashMap<String, Object>(map.size());
    for (final Map.Entry<String, Object> entry : map.entrySet()) {
      out.put(entry.getKey(), spliceValue(entry.getValue(), resolved));
    }
    return out;
  }

  private static String spliceJson(final String json, final Map<String, String> resolved) {
    // The reference itself contains only [A-Za-z0-9_.] characters and is always embedded inside a
    // JSON string literal. The resolved replacement value may contain any character, so it must be
    // JSON-string-escaped before substitution to keep the surrounding document valid.
    final Matcher matcher = REFERENCE_PATTERN.matcher(json);
    final StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      final String ref = matcher.group();
      final String replacement = resolved.get(ref);
      matcher.appendReplacement(
          buffer, Matcher.quoteReplacement(replacement != null ? jsonEscape(replacement) : ref));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String jsonEscape(final String value) {
    final StringBuilder sb = new StringBuilder(value.length() + 4);
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
          break;
      }
    }
    return sb.toString();
  }
}
