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
package io.camunda.client.util;

import io.camunda.client.CredentialsProvider.CredentialsApplier;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TestCredentialsApplier implements CredentialsApplier {
  private final List<Credential> credentials = new CopyOnWriteArrayList<>();

  @Override
  public void put(final String key, final String value) {
    credentials.add(new Credential(key, value));
  }

  public List<Credential> getCredentials() {
    return credentials;
  }

  public static final class Credential {
    private final String key;
    private final String value;

    public Credential(final String key, final String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Credential that = (Credential) o;
      return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }
  }
}
