package org.camunda.bpm.broker.it.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.regex.Pattern;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.broker.it.process.ServiceTaskTest;
import org.camunda.bpm.broker.it.taskqueue.TaskSubscriptionTest.RecordingTaskHandler;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.camunda.tngp.test.util.TestFileUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

@Ignore
public class BrokerRestartTest
{
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> persistentBrokerConfig(tempFolder.getRoot().getAbsolutePath()));

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(tempFolder)
        .around(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected TngpClient client;

    protected static InputStream persistentBrokerConfig(String path)
    {
        final String canonicallySeparatedPath = path.replaceAll(Pattern.quote(File.separator), "/");

        return TestFileUtil.readAsTextFileAndReplace(
                BrokerRestartTest.class.getClassLoader().getResourceAsStream("persistent-broker.cfg.toml"),
                StandardCharsets.UTF_8,
                Collections.singletonMap("\\$\\{brokerFolder\\}", canonicallySeparatedPath));
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
        TestUtil.doRepeatedly(() ->
            client.workflows()
                .start()
                .workflowDefinitionKey("anId")
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        TestUtil.doRepeatedly(() ->
                client.tasks()
                    .pollAndLock()
                    .taskQueueId(0)
                    .taskType("foo")
                    .lockTime(1234L)
                    .execute())
            .until((b) -> !b.getLockedTasks().isEmpty());

        // when
        restartBroker();

        // then
        final LockedTasksBatch tasksBatchAfterRestart = TestUtil.doRepeatedly(() ->
                client.tasks()
                    .pollAndLock()
                    .taskQueueId(0)
                    .taskType("foo")
                    .lockTime(1234L)
                    .execute())
            .whileConditionHolds((b) -> b.getLockedTasks().isEmpty());

        assertThat(tasksBatchAfterRestart.getLockedTasks()).isEmpty();

    }

    @Test
    public void shouldCompleteTaskAfterRestart()
    {
        // given
        client.workflows()
            .start()
            .workflowDefinitionKey("anId")
            .execute();

        final LockedTasksBatch taskBatch = TestUtil.doRepeatedly(() ->
                client.tasks()
                    .pollAndLock()
                    .taskQueueId(0)
                    .taskType("foo")
                    .lockTime(1234L)
                    .execute())
            .until((b) -> !b.getLockedTasks().isEmpty());

        final long taskId = taskBatch.getLockedTasks().get(0).getId();

        // when
        restartBroker();

        // then
        final Long result = client.tasks().complete()
                .topicId(0)
                .taskKey(taskId)
                .execute();

        assertThat(result).isEqualTo(taskId);
    }

    @Test
    public void shouldCreateWorkflowInstanceAfterRestart()
    {
        // when
        restartBroker();

        // then
        final WorkflowInstance wfInstance = client.workflows()
            .start()
            .workflowDefinitionKey("anId")
            .execute();

        assertThat(wfInstance).isNotNull();
    }

    @Test
    public void shouldContinueAscendingWfRuntimeIdGenerationAfterRestart()
    {
        // given
        final WorkflowInstance wfInstance = client.workflows()
            .start()
            .workflowDefinitionKey("anId")
            .execute();

        restartBroker();

        // when
        final WorkflowInstance wfInstance2 = client.workflows()
            .start()
            .workflowDefinitionKey("anId")
            .execute();

        // then
        assertThat(wfInstance2.getId()).isGreaterThan(wfInstance.getId());
    }

    @Test
    public void shouldContinueAscendingTaskIdGenerationAfterRestart()
    {
        // given
        final Long taskId = client.tasks()
            .create()
            .topicId(0)
            .taskType("foo")
            .execute();

        restartBroker();

        // when
        final Long task2Id = client.tasks()
            .create()
            .topicId(0)
            .taskType("foo")
            .execute();

        // then
        assertThat(task2Id).isGreaterThan(taskId);
    }

    @Test
    public void shouldNotReceiveTasksAfterRestartWithSubscription()
    {
        // given
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        client.tasks().newSubscription()
            .topicId(0)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(10L))
            .handler(taskHandler)
            .open();

        client.tasks().create()
            .taskType("foo")
            .execute();

        TestUtil.waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        // when
        restartBroker();

        // then
        taskHandler.clear();

        client.tasks().newSubscription()
            .topicId(0)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(10L))
            .handler(taskHandler)
            .open();

        TestUtil.doRepeatedly(() -> null)
            .whileConditionHolds((o) -> taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).isEmpty();
    }


    protected void restartBroker()
    {
        client.disconnect();
        brokerRule.restartBroker();
        client.connect();
    }


}
