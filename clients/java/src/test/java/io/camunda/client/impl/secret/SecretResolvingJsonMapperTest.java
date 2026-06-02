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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.client.api.secret.SecretsClient;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SecretResolvingJsonMapperTest {

  private static SecretResolvingJsonMapper mapperWith(final Map<String, String> resolutions) {
    return new SecretResolvingJsonMapper(
        new CamundaObjectMapper(), new RecordingClient(resolutions));
  }

  @Test
  public void shouldResolveStandaloneReferenceInMap() {
    final SecretResolvingJsonMapper mapper =
        mapperWith(singletonMap("camunda.secrets.TOKEN", "topsecret"));

    final Map<String, Object> result =
        mapper.fromJsonAsMap("{\"token\":\"camunda.secrets.TOKEN\"}");

    assertThat(result).containsEntry("token", "topsecret");
  }

  @Test
  public void shouldResolveReferenceInsideConcatenation() {
    final SecretResolvingJsonMapper mapper =
        mapperWith(singletonMap("camunda.secrets.TOKEN", "topsecret"));

    final Map<String, Object> result =
        mapper.fromJsonAsMap("{\"auth\":\"Bearer camunda.secrets.TOKEN-end\"}");

    assertThat(result).containsEntry("auth", "Bearer topsecret-end");
  }

  @Test
  public void shouldResolveNestedReferenceInMap() {
    final SecretResolvingJsonMapper mapper =
        mapperWith(singletonMap("camunda.secrets.API_KEY", "ABC"));

    final Map<String, Object> result =
        mapper.fromJsonAsMap(
            "{\"credentials\":{\"key\":\"camunda.secrets.API_KEY\"},\"plain\":\"x\"}");

    @SuppressWarnings("unchecked")
    final Map<String, Object> creds = (Map<String, Object>) result.get("credentials");
    assertThat(creds).containsEntry("key", "ABC");
    assertThat(result).containsEntry("plain", "x");
  }

  @Test
  public void shouldResolveReferenceInListEntries() {
    final Map<String, String> resolutions = new HashMap<String, String>();
    resolutions.put("camunda.secrets.A", "1");
    resolutions.put("camunda.secrets.B", "2");
    final SecretResolvingJsonMapper mapper = mapperWith(resolutions);

    final Map<String, Object> result =
        mapper.fromJsonAsMap("{\"items\":[\"camunda.secrets.A\",\"Bearer camunda.secrets.B\"]}");

    @SuppressWarnings("unchecked")
    final List<Object> items = (List<Object>) result.get("items");
    assertThat(items).containsExactly("1", "Bearer 2");
  }

  @Test
  public void shouldLeaveMissingReferenceUnchanged() {
    final SecretResolvingJsonMapper mapper = mapperWith(new HashMap<String, String>());

    final Map<String, Object> result =
        mapper.fromJsonAsMap("{\"token\":\"camunda.secrets.UNKNOWN\"}");

    assertThat(result).containsEntry("token", "camunda.secrets.UNKNOWN");
  }

  @Test
  public void shouldNotCallSecretsClientWhenNoReferences() {
    final SecretsClient client = spy(new RecordingClient(new HashMap<String, String>()));
    final SecretResolvingJsonMapper mapper =
        new SecretResolvingJsonMapper(new CamundaObjectMapper(), client);

    mapper.fromJsonAsMap("{\"plain\":\"hello\",\"num\":1,\"nested\":{\"k\":\"v\"}}");

    verify(client, never()).resolve(anyList());
  }

  @Test
  public void shouldBatchAllReferencesIntoSingleResolveCall() {
    final Map<String, String> resolutions = new HashMap<String, String>();
    resolutions.put("camunda.secrets.A", "1");
    resolutions.put("camunda.secrets.B", "2");
    resolutions.put("camunda.secrets.C", "3");
    final SecretsClient client = spy(new RecordingClient(resolutions));
    final SecretResolvingJsonMapper mapper =
        new SecretResolvingJsonMapper(new CamundaObjectMapper(), client);

    mapper.fromJsonAsMap(
        "{\"a\":\"camunda.secrets.A\",\"b\":\"camunda.secrets.B\",\"c\":\"camunda.secrets.C\"}");

    verify(client, times(1)).resolve(anyList());
  }

  @Test
  public void shouldResolveReferenceWhenDeserializingToTypedPojo() {
    final SecretResolvingJsonMapper mapper =
        mapperWith(singletonMap("camunda.secrets.TOKEN", "topsecret"));

    final Credentials result =
        mapper.fromJson("{\"token\":\"Bearer camunda.secrets.TOKEN\"}", Credentials.class);

    assertThat(result.getToken()).isEqualTo("Bearer topsecret");
  }

  @Test
  public void shouldHandleValueWithJsonSpecialCharacters() {
    final SecretResolvingJsonMapper mapper =
        mapperWith(singletonMap("camunda.secrets.TOKEN", "a\"b\\c\nd"));

    final Credentials result =
        mapper.fromJson("{\"token\":\"camunda.secrets.TOKEN\"}", Credentials.class);

    assertThat(result.getToken()).isEqualTo("a\"b\\c\nd");
  }

  private static Map<String, String> singletonMap(final String key, final String value) {
    final Map<String, String> map = new HashMap<String, String>();
    map.put(key, value);
    return map;
  }

  public static final class Credentials {
    private String token;

    public String getToken() {
      return token;
    }

    public void setToken(final String token) {
      this.token = token;
    }
  }

  private static class RecordingClient implements SecretsClient {
    private final Map<String, String> resolutions;

    RecordingClient(final Map<String, String> resolutions) {
      this.resolutions = resolutions;
    }

    @Override
    public Map<String, String> resolve(final List<String> references) {
      final Map<String, String> out = new HashMap<String, String>();
      for (final String ref : Arrays.asList(references.toArray(new String[0]))) {
        if (resolutions.containsKey(ref)) {
          out.put(ref, resolutions.get(ref));
        }
      }
      return out;
    }
  }
}
