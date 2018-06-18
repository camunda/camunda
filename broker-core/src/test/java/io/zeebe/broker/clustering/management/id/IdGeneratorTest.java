/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.management.id;

import static io.zeebe.broker.clustering.orchestration.ClusterOrchestrationLayerServiceNames.ID_GENERATOR_SERVICE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.clustering.orchestration.id.IdGenerator;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class IdGeneratorTest {
  @Rule
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("zeebe.no-default-topic.cfg.toml");

  private IdGenerator idGenerator;

  @Before
  public void setUp() throws Exception {
    idGenerator = getIdGenerator();
  }

  private IdGenerator getIdGenerator() throws Exception {
    final Injector<IdGenerator> idGeneratorInjector = new Injector<>();
    brokerRule
        .getBroker()
        .getBrokerContext()
        .getServiceContainer()
        .createService(ServiceName.newServiceName("test-id-generator", Void.class), () -> null)
        .dependency(ID_GENERATOR_SERVICE_NAME, idGeneratorInjector)
        .install()
        .get(1, TimeUnit.SECONDS);

    return idGeneratorInjector.getValue();
  }

  @Test
  public void shouldGenerateNewIds() {
    // when
    final ActorFuture<Integer> id1 = idGenerator.nextId();
    final ActorFuture<Integer> id2 = idGenerator.nextId();
    final ActorFuture<Integer> id3 = idGenerator.nextId();

    // then
    assertThat(id1.join()).isEqualTo(1);
    assertThat(id2.join()).isEqualTo(2);
    assertThat(id3.join()).isEqualTo(3);
  }

  @Test
  public void shouldGenerateNewIdsAfterRestart() throws Exception {
    // given
    idGenerator.nextId().join();
    idGenerator.nextId().join();
    idGenerator.nextId().join();

    brokerRule.restartBroker();

    idGenerator = getIdGenerator();

    // when
    final ActorFuture<Integer> id1 = idGenerator.nextId();
    final ActorFuture<Integer> id2 = idGenerator.nextId();
    final ActorFuture<Integer> id3 = idGenerator.nextId();

    // then
    assertThat(id1.join()).isEqualTo(4);
    assertThat(id2.join()).isEqualTo(5);
    assertThat(id3.join()).isEqualTo(6);
  }
}
