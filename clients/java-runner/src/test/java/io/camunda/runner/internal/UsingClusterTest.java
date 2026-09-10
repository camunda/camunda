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
package io.camunda.runner.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.runner.Cluster;
import io.camunda.runner.LiveBpmn;
import org.junit.jupiter.api.Test;

final class UsingClusterTest {

  @Test
  void shouldNotCloseUserProvidedClient() {
    // given
    final CamundaClient client = mock(CamundaClient.class);
    final Cluster cluster = LiveBpmn.cluster().using(client);

    // when
    cluster.close();

    // then
    verify(client, never()).close();
    assertThat(cluster.ownsClient()).isFalse();
    assertThat(cluster.client()).isSameAs(client);
  }
}
