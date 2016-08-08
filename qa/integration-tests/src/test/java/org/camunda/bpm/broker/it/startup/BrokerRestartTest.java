package org.camunda.bpm.broker.it.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.broker.it.process.ServiceTaskTest;
import org.camunda.tngp.broker.test.util.TestFileUtil;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class BrokerRestartTest
{
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> foo(tempFolder.getRoot().getAbsolutePath()));

    public ClientRule clientRule = new ClientRule();



    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(tempFolder)
        .around(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected TngpClient client;

    // TODO: refactor this
    protected static InputStream foo(String path)
    {
        // TODO: this is ugly
        return TestFileUtil.readAsTextFileAndReplace(
                BrokerRestartTest.class.getClassLoader().getResourceAsStream("persistent-broker.cfg.toml"),
                StandardCharsets.UTF_8,
                Collections.singletonMap("\\$\\{brokerFolder\\}", path));
    }

    @Before
    public void deployBpmnProcess()
    {
        client = clientRule.getClient();

        clientRule.getClient()
            .workflows()
            .deploy()
            .bpmnModelInstance(ServiceTaskTest.oneTaskProcess("foo"))
            .execute();
    }

    @Test
    public void shouldNotReReadWorkflowExecutionEvents()
    {
        // given
        client.workflows()
            .start()
            .workflowDefinitionKey("anId")
            .execute();

        final LockedTasksBatch tasksBatch = TestUtil.doRepeatedly(() ->
                client.tasks()
                    .pollAndLock()
                    .taskQueueId(0)
                    .taskType("foo")
                    .lockTime(1234L)
                    .execute())
            .until((b) -> !b.getLockedTasks().isEmpty());

        final LockedTask task = tasksBatch.getLockedTasks().get(0);

        client.tasks().complete().taskQueueId(0).taskId(task.getId());

        // when
        client.disconnect();
        brokerRule.restartBroker();
        client.connect();

        // then
        final LockedTasksBatch tasksBatchAfterRestart = TestUtil.doRepeatedly(() ->
                client.tasks()
                    .pollAndLock()
                    .taskQueueId(0)
                    .taskType("foo")
                    .lockTime(1234L)
                    .execute())
            .until((b) -> !b.getLockedTasks().isEmpty());

        assertThat(tasksBatchAfterRestart.getLockedTasks()).isEmpty();

    }

    // TODO: test that I can still create new workflow instances after restart (or that I can complete the existing one)

}
