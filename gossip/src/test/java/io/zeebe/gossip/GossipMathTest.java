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
package io.zeebe.gossip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.junit.Test;

public class GossipMathTest {

  @Test
  public void shouldHaveTheSameResultLikeCeilLog2() {
    // given
    final Function<Integer, Integer> ceilLog2 =
        (num) -> (int) Math.ceil((Math.log(num + 1) / Math.log(2)));
    final int workCount = 1_000_000;

    for (int i = 0; i < workCount; i++) {
      final int gossipResult = GossipMath.ceilLog2(i);
      final int logResult = ceilLog2.apply(i);

      assertThat(gossipResult).isEqualTo(logResult);
    }
  }
}
