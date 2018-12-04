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
package io.zeebe.broker.exporter;

import static io.zeebe.broker.exporter.ExporterServiceNames.EXPORTER_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.clock.DefaultActorClock;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ExporterComponentTest {

  private final BrokerCfg brokerConfig = new BrokerCfg();
  private SystemContext context;
  private final ExporterComponent component = new ExporterComponent();

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ControlledActorSchedulerRule actorSchedulerRule =
      new ControlledActorSchedulerRule();
  private final ServiceContainerRule serviceContainerRule =
      new ServiceContainerRule(actorSchedulerRule);

  @Before
  public void setup() {
    context =
        spy(
            new SystemContext(
                brokerConfig,
                temporaryFolder.getRoot().getAbsolutePath(),
                new DefaultActorClock()));
    doAnswer(i -> serviceContainerRule.get()).when(context).getServiceContainer();
  }

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(actorSchedulerRule).around(serviceContainerRule);

  @Test
  public void shouldNotStartExporterServiceIfNoExportersAreConfigured() {
    // given
    brokerConfig.setExporters(new ArrayList<>());

    // when
    component.init(context);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(context.getServiceContainer().hasService(EXPORTER_MANAGER)).isFalse();
  }

  @Test
  public void shouldStartExporterServiceIfAtLeastOneExporterIsConfigured() {
    // given
    brokerConfig.setExporters(Arrays.asList(new ExporterCfg()));

    // when
    component.init(context);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(context.getServiceContainer().hasService(EXPORTER_MANAGER)).isTrue();
  }
}
