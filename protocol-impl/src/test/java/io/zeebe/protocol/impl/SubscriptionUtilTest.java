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
package io.zeebe.protocol.impl;

import static io.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionHashCode;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class SubscriptionUtilTest {

  @Test
  public void shouldGetSubscriptionHashCode() {

    assertThat(getSubscriptionHashCode("a")).isEqualTo(97);
    assertThat(getSubscriptionHashCode("b")).isEqualTo(98);
    assertThat(getSubscriptionHashCode("c")).isEqualTo(99);
  }

  @Test
  public void shouldGetZeroSubscriptionHashCodeIfEmpty() {

    assertThat(getSubscriptionHashCode("")).isEqualTo(0);
    assertThat(getSubscriptionHashCode(new UnsafeBuffer())).isEqualTo(0);
  }

  @Test
  public void shouldGetSameHashCodeForBufferAndString() {
    final Random random = new Random();
    for (int t = 0; t < 1_000; t++) {

      final byte[] bytes = new byte[8];
      random.nextBytes(bytes);
      final String correlationKey = new String(bytes, StandardCharsets.UTF_8);

      assertThat(getSubscriptionHashCode(correlationKey))
          .describedAs(correlationKey)
          .isEqualTo(getSubscriptionHashCode(BufferUtil.wrapString(correlationKey)));
    }
  }
}
