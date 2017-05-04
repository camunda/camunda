package org.camunda.tngp.client.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule.TEST_PARTITION_ID;
import static org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule.TEST_TOPIC_NAME;

import org.camunda.tngp.client.IncidentTopicClient;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;


public class ResolveIncidentTest
{
    private static final String PAYLOAD = "{ \"foo\" : \"bar\" }";

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private IncidentTopicClient incidentTopicClient;

    @Before
    public void setUp()
    {
        this.incidentTopicClient = clientRule
                .getClient()
                .incidentTopic(TEST_TOPIC_NAME, TEST_PARTITION_ID);
    }

    @Test
    public void shouldResolveIncident()
    {
        // given
        brokerRule.onExecuteCommandRequest().respondWith()
            .topicName(TEST_TOPIC_NAME)
            .partitionId(TEST_PARTITION_ID)
            .key(2L)
            .event()
                .put("eventType", "RESOLVED")
                .done()
            .register();

        // when
        final IncidentResolveResult result = incidentTopicClient
            .resolve()
                .incidentKey(2L)
                .modifiedPayload(PAYLOAD)
                .execute();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIncidentKey()).isEqualTo(2L);
        assertThat(result.isIncidentResolved()).isTrue();
    }

    @Test
    public void shouldFailToResolveIncident()
    {
        // given
        brokerRule.onExecuteCommandRequest().respondWith()
            .topicName(TEST_TOPIC_NAME)
            .partitionId(TEST_PARTITION_ID)
            .key(2L)
            .event()
                .put("eventType", "RESOLVE_FAILED")
                .put("errorMessage", "failure")
                .done()
            .register();

        // when
        final IncidentResolveResult result = incidentTopicClient
                .resolve()
                    .incidentKey(2L)
                    .modifiedPayload(PAYLOAD)
                    .execute();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIncidentKey()).isEqualTo(2L);
        assertThat(result.isIncidentResolved()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("failure");
    }

    @Test
    public void shouldRejectResolveCommand()
    {
        // given
        brokerRule.onExecuteCommandRequest().respondWith()
            .topicName(TEST_TOPIC_NAME)
            .partitionId(TEST_PARTITION_ID)
            .key(2L)
            .event()
                .put("eventType", "RESOLVE_REJECTED")
                .done()
            .register();

        // when
        final IncidentResolveResult result = incidentTopicClient
                .resolve()
                    .incidentKey(2L)
                    .modifiedPayload(PAYLOAD)
                    .execute();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIncidentKey()).isEqualTo(2L);
        assertThat(result.isIncidentResolved()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Incident not found or processing a previous request.");
    }

    @Test
    public void shouldFailIfIncidentKeyMissing()
    {
        // then
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("incident key must be greater than 0");

        // when
        incidentTopicClient
            .resolve()
                .modifiedPayload(PAYLOAD)
                .execute();
    }

    @Test
    public void shouldFailIfPayloadMissing()
    {
        // then
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("payload must not be null");

        // when
        incidentTopicClient
            .resolve()
                .incidentKey(2L)
                .execute();
    }

}
