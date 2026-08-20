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
package io.camunda.zeebe.protocol.record.value.deployment;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableResource.Builder.class)
public interface Resource extends ResourceMetadataValue {
  /**
   * @return returns the corresponding Resource content as a UTF-8 string. Note: this conversion is
   *     lossy for non-UTF-8 binary content. Prefer {@link #getResourceBytes()} for binary-safe
   *     access.
   */
  String getResourceProp();

  /**
   * @return returns the raw bytes of the corresponding Resource content. The default implementation
   *     derives bytes from {@link #getResourceProp()} using UTF-8 and is therefore lossy for
   *     non-UTF-8 binary content. {@link
   *     io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord} overrides this with
   *     a binary-safe implementation.
   */
  default byte[] getResourceBytes() {
    return getResourceProp().getBytes(StandardCharsets.UTF_8);
  }
}
