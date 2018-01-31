package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.protocol.Protocol;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class LargeDeploymentTest
{
    public static final int CREATION_TIMES = 1_000;

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule(() -> {
        final Properties p = new Properties();
        p.setProperty(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC, "180");

        return p;
    }, true);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule)
                                          .around(clientRule);

    @Test
    public void shouldCreateBunchOfDeployments()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        // when
        long lastDeploymentKey = -1L;
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            final DeploymentEvent deploymentEvent = workflowService.deploy(clientRule.getDefaultTopic())
                                                           .addResourceFromClasspath("workflows/extended-order-process.bpmn")
                                                           .execute();

            // then
            final EventMetadata metadata = deploymentEvent.getMetadata();
            assertThat(metadata.getKey()).isGreaterThan(lastDeploymentKey);
            lastDeploymentKey = metadata.getKey();
            assertThat(metadata.getPartitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
            assertThat(deploymentEvent.getState()).isEqualTo("CREATED");
        }
    }
}
