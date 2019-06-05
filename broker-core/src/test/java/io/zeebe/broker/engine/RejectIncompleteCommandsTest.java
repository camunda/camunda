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
package io.zeebe.broker.engine;

import static io.zeebe.protocol.intent.MessageIntent.PUBLISH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandRequestBuilder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class RejectIncompleteCommandsTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();

  private static final ClientApiRule API_RULE = new ClientApiRule(BROKER_RULE::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(API_RULE);

  @Test
  public void shouldFailToPublishMessageWithoutName() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .done();

    assertThatThrownBy(request::sendAndAwait)
        .hasMessageContaining("Property 'name' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutCorrelationKey() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("timeToLive", 1_000)
            .done();

    assertThatThrownBy(request::sendAndAwait)
        .hasMessageContaining("Property 'correlationKey' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutTimeToLive() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .done();

    assertThatThrownBy(() -> request.sendAndAwait())
        .hasMessageContaining("Property 'timeToLive' has no valid value");
  }
}
