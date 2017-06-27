package io.zeebe.broker.it.startup;

import static io.zeebe.broker.it.util.TopicEventRecorder.incidentEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.regex.Pattern;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.event.IncidentEvent;
import io.zeebe.client.task.Task;
import io.zeebe.client.workflow.cmd.DeploymentResult;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import io.zeebe.test.util.TestFileUtil;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.time.ClockUtil;
import org.assertj.core.util.Files;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

public class BrokerRecoveryTest
{
    private static final ZeebeModelInstance WORKFLOW = wrap(Bpmn.createExecutableProcess("process")
                                                                .startEvent("start")
                                                                .serviceTask("task")
                                                                .endEvent("end")
                                                                .done())
           .taskDefinition("task", "foo", 3);

    private static final ZeebeModelInstance WORKFLOW_TWO_TASKS = wrap(Bpmn.createExecutableProcess("process")
                                                                          .startEvent("start")
                                                                          .serviceTask("task1")
                                                                          .serviceTask("task2")
                                                                          .endEvent("end")
                                                                          .done())
          .taskDefinition("task1", "foo", 3)
          .taskDefinition("task2", "bar", 3);

    private static final ZeebeModelInstance WORKFLOW_INCIDENT = wrap(Bpmn.createExecutableProcess("process")
                                                                         .startEvent()
                                                                         .serviceTask("task")
                                                                         .done())
           .taskDefinition("task", "test", 3)
           .ioMapping("task")
               .input("$.foo", "$.foo")
               .done();

    public TemporaryFolder tempFolder = new TemporaryFolder();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> brokerConfig(tempFolder.getRoot().getAbsolutePath()));

    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties properties = new Properties();

        properties.put(ClientProperties.CLIENT_TASK_EXECUTION_AUTOCOMPLETE, false);

        return properties;
    });

    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(tempFolder)
        .around(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(60 * 1000);

    protected static InputStream brokerConfig(String path)
    {
        final String canonicallySeparatedPath = path.replaceAll(Pattern.quote(File.separator), "/");

        return TestFileUtil.readAsTextFileAndReplace(
                BrokerRecoveryTest.class.getClassLoader().getResourceAsStream("recovery-broker.cfg.toml"),
                StandardCharsets.UTF_8,
                Collections.singletonMap("\\$\\{brokerFolder\\}", canonicallySeparatedPath));
    }

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldCreateWorkflowInstanceAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        // when
        restartBroker();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Ignore("Recovery fails because of missing deployment event - see #332")
    @Test
    public void shouldContinueWorkflowInstanceAtTaskAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        // when
        restartBroker();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(Task::complete)
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Ignore("Recovery fails because of missing deployment event - see #332")
    @Test
    public void shouldContinueWorkflowInstanceWithLockedTaskAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();
        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(recordingTaskHandler)
            .open();

        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        // when
        restartBroker();

        final Task task = recordingTaskHandler.getHandledTasks().get(0);
        task.complete();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Ignore("Recovery fails because of missing deployment event - see #332")
    @Test
    public void shouldContinueWorkflowInstanceAtSecondTaskAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW_TWO_TASKS)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(Task::complete)
            .open();

        waitUntil(() -> eventRecorder.getTaskEvents(taskEvent("CREATED")).size() > 1);

        // when
        restartBroker();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("bar")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(Task::complete)
            .open();

        // then
        waitUntil(() -> eventRecorder.getTaskEvents(taskEvent("COMPLETED")).size() > 1);
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Test
    public void shouldDeployNewWorkflowVersionAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        // when
        restartBroker();

        final DeploymentResult deploymentResult = clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        // then
        assertThat(deploymentResult.isDeployed());
        assertThat(deploymentResult.getDeployedWorkflows().get(0).getVersion()).isEqualTo(2);

        final WorkflowInstance workflowInstanceV1 = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .version(1)
            .execute();

        final WorkflowInstance workflowInstanceV2 = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .latestVersion()
            .execute();

        // then
        assertThat(workflowInstanceV1.getVersion()).isEqualTo(1);
        assertThat(workflowInstanceV2.getVersion()).isEqualTo(2);
    }

    @Test
    public void shouldLockAndCompleteStandaloneTaskAfterRestart()
    {
        // given
        clientRule.taskTopic().create()
                .taskType("foo")
                .execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        // when
        restartBroker();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(Task::complete)
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldCompleteStandaloneTaskAfterRestart()
    {
        // given
        clientRule.taskTopic().create()
            .taskType("foo")
            .execute();

        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();
        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(recordingTaskHandler)
            .open();

        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        // when
        restartBroker();

        final Task task = recordingTaskHandler.getHandledTasks().get(0);
        task.complete();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldNotReceiveLockedTasksAfterRestart()
    {
        // given
        clientRule.taskTopic().create()
            .taskType("foo")
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();
        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofSeconds(5))
            .lockOwner("test")
            .handler(taskHandler)
            .open();

        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        // when
        restartBroker();

        taskHandler.clear();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofMinutes(10L))
            .lockOwner("test")
            .handler(taskHandler)
            .open();

        // then
        TestUtil.doRepeatedly(() -> null)
            .whileConditionHolds((o) -> taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).isEmpty();
    }

    @Test
    @Ignore
    public void shouldReceiveLockExpiredTasksAfterRestart()
    {
        // given
        clientRule.taskTopic().create()
            .taskType("foo")
            .execute();

        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();
        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofSeconds(5))
            .lockOwner("test")
            .handler(recordingTaskHandler)
            .open();

        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        // when
        restartBroker(() ->
        {
            final Instant now = ClockUtil.getCurrentTime();
            ClockUtil.setCurrentTime(now.plusSeconds(60));
        });

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("LOCK_EXPIRED")));

        recordingTaskHandler.clear();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofMinutes(10L))
            .lockOwner("test")
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        final Task task = recordingTaskHandler.getHandledTasks().get(0);
        task.complete();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Ignore("Recovery fails because of missing deployment event - see #332")
    @Test
    public void shouldResolveIncidentAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW_INCIDENT)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        final IncidentEvent incident = eventRecorder.getSingleIncidentEvent(incidentEvent("CREATED")).getEvent();

        // when
        restartBroker();

        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(incident.getWorkflowInstanceKey())
            .activityInstanceKey(incident.getActivityInstanceKey())
            .payload("{\"foo\":\"bar\"}")
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVED")));
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));
    }

    @Ignore("Recovery fails because of missing deployment event - see #332")
    @Test
    public void shouldResolveFailedIncidentAfterRestart()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW_INCIDENT)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        final IncidentEvent incident = eventRecorder.getSingleIncidentEvent(incidentEvent("CREATED")).getEvent();

        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(incident.getWorkflowInstanceKey())
            .activityInstanceKey(incident.getActivityInstanceKey())
            .payload("{\"x\":\"y\"}")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVE_FAILED")));

        // when
        restartBroker();

        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(incident.getWorkflowInstanceKey())
            .activityInstanceKey(incident.getActivityInstanceKey())
            .payload("{\"foo\":\"bar\"}")
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVED")));
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));
    }

    protected void restartBroker()
    {
        restartBroker(() ->
        { });
    }

    protected void restartBroker(Runnable onStop)
    {
        eventRecorder.stopRecordingEvents();
        clientRule.getClient().disconnect();
        brokerRule.stopBroker();

        // delete snapshot and index files to trigger recovery
        final File configDir = new File(tempFolder.getRoot().getAbsolutePath());
        final File snapshotDir = new File(configDir, "snapshot");
        // final File indexDir = new File(configDir, "index");

        assertThat(snapshotDir).exists().isDirectory();
        // assertThat(indexDir).exists().isDirectory();

        Files.delete(snapshotDir);
        // Files.delete(indexDir);

        onStop.run();

        brokerRule.startBroker();
        clientRule.getClient().connect();
        eventRecorder.startRecordingEvents();
    }

}
