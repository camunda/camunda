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

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionHashCode;
import static io.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionPartitionId;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class SubscriptionUtilTest {

  @Test
  public void shouldGetSubscriptionHashCode() {
    assertThat(getSubscriptionHashCode(wrapString("a"))).isEqualTo(97);
    assertThat(getSubscriptionHashCode(wrapString("b"))).isEqualTo(98);
    assertThat(getSubscriptionHashCode(wrapString("c"))).isEqualTo(99);
    assertThat(getSubscriptionHashCode(wrapString("foobar"))).isEqualTo(-1268878963);
  }

  @Test
  public void shouldGetZeroSubscriptionHashCodeIfEmpty() {
    assertThat(getSubscriptionHashCode(new UnsafeBuffer())).isEqualTo(0);
  }

  @Test
  public void shouldGetPartitionIdForCorrelationKey() {
    assertThat(getSubscriptionPartitionId(wrapString("a"), 10)).isEqualTo(7 + START_PARTITION_ID);
    assertThat(getSubscriptionPartitionId(wrapString("b"), 3)).isEqualTo(2 + START_PARTITION_ID);
    assertThat(getSubscriptionPartitionId(wrapString("c"), 11)).isEqualTo(0 + START_PARTITION_ID);
    assertThat(getSubscriptionPartitionId(wrapString("foobar"), 100))
        .isEqualTo(63 + START_PARTITION_ID);
  }
}
