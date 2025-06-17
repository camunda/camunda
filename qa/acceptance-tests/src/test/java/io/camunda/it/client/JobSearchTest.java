package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitUntilJobHasBeenActivated;
import static io.camunda.it.util.TestHelper.waitUntilJobWorkerHasCompletedJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class JobSearchTest {

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static CamundaClient camundaClient;
  private static Job JOB_TYPE_A;

  @BeforeAll
  static void beforeAll() {
    final List<String> processes = List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn");

    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.add(
                deployProcessAndWaitForIt(camundaClient, String.format("process/%s", process))));

    DEPLOYED_PROCESSES.forEach(
        process -> {
          if ("service_tasks_v2".equals(process.getBpmnProcessId())) {
            startProcessInstance(camundaClient, process.getBpmnProcessId(), "{\"path\": 222}");
          } else {
            startProcessInstance(camundaClient, process.getBpmnProcessId());
          }
        });

    waitForProcessInstancesToStart(camundaClient, DEPLOYED_PROCESSES.size());

    final var listenerActivatedJob =
        camundaClient
            .newActivateJobsCommand()
            .jobType("taskA_execution_listener")
            .maxJobsToActivate(1)
            .timeout(Duration.ofSeconds(10))
            .send()
            .join()
            .getJobs()
            .getFirst();

    waitUntilJobHasBeenActivated(camundaClient, listenerActivatedJob.getKey(), 1);

    camundaClient.newCompleteCommand(listenerActivatedJob.getKey()).send().join();

    waitUntilJobWorkerHasCompletedJob(camundaClient, listenerActivatedJob.getKey(), 1);

    final var activatedJob =
        camundaClient
            .newActivateJobsCommand()
            .jobType("taskA")
            .maxJobsToActivate(1)
            .workerName("worker1")
            .timeout(Duration.ofSeconds(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    waitUntilJobHasBeenActivated(camundaClient, activatedJob.getKey(), 1);

    camundaClient.newCompleteCommand(activatedJob.getKey()).send().join();

    waitUntilJobWorkerHasCompletedJob(camundaClient, activatedJob.getKey(), 1);

    JOB_TYPE_A =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type(o -> o.eq("taskA")))
            .send()
            .join()
            .items()
            .getFirst();
  }

  @Test
  void shouldReturnAllJobsByDefault() {
    // given
    final var result = camundaClient.newJobSearchRequest().send().join();
    // then
    assertThat(result.items())
        .hasSize(4)
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(
            List.of("taskA", "taskA_execution_listener", "taskB", "taskC"));
  }

  @Test
  void shouldSearchJobByKey() {
    // given

    // final var job = camundaClient.newJobSearchRequest().send().join().items().getFirst();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.jobKey(JOB_TYPE_A.getJobKey()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(JOB_TYPE_A.getJobKey());
  }

  @Test
  void shouldSearchJobByType() {
    // when
    final var result =
        camundaClient.newJobSearchRequest().filter(f -> f.type(o -> o.eq("taskA"))).send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskA");
  }

  @Test
  void shouldSearchJobByWorker() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.worker(o -> o.eq("worker1")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskA");
    assertThat(result.items().getFirst().getWorker()).isEqualTo("worker1");
  }

  @Test
  void shouldSearchJobByState() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.state(o -> o.eq(JobState.COMPLETED)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(List.of("taskA", "taskA_execution_listener"));
    assertThat(result.items().getFirst().getState()).isEqualTo(JobState.COMPLETED);
  }

  @Test
  void shouldSearchJobByKind() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.kind(o -> o.eq(JobKind.EXECUTION_LISTENER)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskA_execution_listener");
    assertThat(result.items().getFirst().getKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
  }

  @Test
  void shouldSearchJobByListenerEventType() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.listenerEventType(o -> o.eq(ListenerEventType.START)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getType()).isEqualTo("taskA_execution_listener");
    assertThat(result.items().getFirst().getListenerEventType()).isEqualTo(ListenerEventType.START);
  }

  @Test
  void shouldSearchJobByProcessDefinitionId() {
    // given
    final var processDefinitionId =
        DEPLOYED_PROCESSES.stream()
            .filter(p -> "service_tasks_v1".equals(p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow()
            .getBpmnProcessId();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processDefinitionId(o -> o.eq(processDefinitionId)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(processDefinitionId);
  }

  @Test
  void shouldSearchJobByProcessDefinitionKey() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processDefinitionKey(o -> o.eq(JOB_TYPE_A.getProcessDefinitionKey())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(List.of("taskA", "taskB", "taskA_execution_listener"));
    assertThat(result.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(JOB_TYPE_A.getProcessDefinitionKey());
  }

  @Test
  void shouldSearchJobByProcessInstanceKey() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(o -> o.eq(JOB_TYPE_A.getProcessInstanceKey())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(List.of("taskA", "taskB", "taskA_execution_listener"));
    assertThat(result.items().getFirst().getProcessInstanceKey())
        .isEqualTo(JOB_TYPE_A.getProcessInstanceKey());
  }

  @Test
  void shouldSearchJobByElementId() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.elementId(o -> o.eq(JOB_TYPE_A.getElementId())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(List.of("taskA", "taskA_execution_listener"));
    assertThat(result.items().getFirst().getElementId()).isEqualTo(JOB_TYPE_A.getElementId());
  }

  @Test
  void shouldSearchJobByElementInstanceKey() {

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.elementInstanceKey(o -> o.eq(JOB_TYPE_A.getElementInstanceKey())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(List.of("taskA", "taskA_execution_listener"));
    assertThat(result.items().getFirst().getElementInstanceKey())
        .isEqualTo(JOB_TYPE_A.getElementInstanceKey());
  }

  @Test
  void shouldSearchJobByTenantId() {
    // given
    final var tenantId = "<default>";

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.tenantId(o -> o.eq(tenantId)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(4);
    assertThat(result.items())
        .extracting(Job::getType)
        .containsExactlyInAnyOrderElementsOf(
            List.of("taskA", "taskA_execution_listener", "taskB", "taskC"));
  }

  @Test
  void shouldSortJobsByKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getJobKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.jobKey().asc()).send().join();

    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.jobKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getJobKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());

    assertThat(resultDesc.items())
        .extracting(Job::getJobKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByType() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getType)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.type().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.type().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByWorker() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getWorker)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.worker().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.worker().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getWorker)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getWorker)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByState() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getState)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.state().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getState)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getState)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByKind() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getKind)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.kind().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.kind().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getKind)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getKind)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByListenerEventType() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getListenerEventType)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.listenerEventType().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.listenerEventType().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getListenerEventType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getListenerEventType)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByProcessDefinitionId() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getProcessDefinitionId)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.processDefinitionId().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.processDefinitionId().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getProcessDefinitionId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getProcessDefinitionId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByProcessDefinitionKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getProcessDefinitionKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.processDefinitionKey().asc()).send().join();
    final var resultDesc =
        camundaClient
            .newJobSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getProcessDefinitionKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getProcessDefinitionKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByProcessInstanceKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getProcessInstanceKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.processInstanceKey().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.processInstanceKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getProcessInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getProcessInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByElementId() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getElementId)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementId().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementId().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getElementId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getElementId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByElementInstanceKey() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getElementInstanceKey)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementInstanceKey().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.elementInstanceKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getElementInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getElementInstanceKey)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSortJobsByTenantId() {
    // given
    final var all =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getTenantId)
            .toList();
    // when
    final var resultAsc =
        camundaClient.newJobSearchRequest().sort(s -> s.tenantId().asc()).send().join();
    final var resultDesc =
        camundaClient.newJobSearchRequest().sort(s -> s.tenantId().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Job::getTenantId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items())
        .extracting(Job::getTenantId)
        .containsExactlyElementsOf(all.stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Test
  void shouldSearchByFromLimit() {
    // given
    final var allJobs =
        camundaClient.newJobSearchRequest().send().join().items().stream()
            .map(Job::getJobKey)
            .toList();
    final int limit = 2;

    // when
    final var result = camundaClient.newJobSearchRequest().page(p -> p.limit(limit)).send().join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(Job::getJobKey).containsAll(allJobs.subList(0, 2));
  }

  @Test
  void shouldSearchAfterSecondItem() {
    // given
    final var allJobs = camundaClient.newJobSearchRequest().send().join().items();
    final var secondItemKey = allJobs.get(1).getJobKey();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(secondItemKey)))
            .send()
            .join();

    // then
    assertThat(result.items())
        .extracting(Job::getJobKey)
        .containsExactlyElementsOf(
            allJobs.stream().map(Job::getJobKey).toList().subList(2, allJobs.size()));
  }

  @Test
  void shouldSearchBeforeSecondItem() {
    // given
    final var allJobs = camundaClient.newJobSearchRequest().send().join().items();
    final var secondItemKey = allJobs.get(1).getJobKey();

    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .page(p -> p.searchBefore(Collections.singletonList(secondItemKey)))
            .send()
            .join();

    // then
    assertThat(result.items())
        .extracting(Job::getJobKey)
        .containsExactlyElementsOf(allJobs.stream().map(Job::getJobKey).toList().subList(0, 1));
  }
}
