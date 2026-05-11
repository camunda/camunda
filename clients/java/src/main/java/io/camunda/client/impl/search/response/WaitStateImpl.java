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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.WaitState;
import io.camunda.client.api.search.response.WaitStateDetails;
import java.util.Objects;

public final class WaitStateImpl implements WaitState {

  private final String type;
  private final WaitStateDetails details;

  public WaitStateImpl(final io.camunda.client.protocol.rest.WaitState item) {
    type = item.getType();
    details = new WaitStateDetailsImpl(item.getDetails());
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public WaitStateDetails getDetails() {
    return details;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, details);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WaitStateImpl that = (WaitStateImpl) o;
    return Objects.equals(type, that.type) && Objects.equals(details, that.details);
  }
}
