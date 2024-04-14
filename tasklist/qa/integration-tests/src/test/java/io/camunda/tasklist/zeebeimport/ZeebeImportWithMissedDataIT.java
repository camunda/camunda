/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.zeebe.protocol.Protocol;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ZeebeImportWithMissedDataIT extends TasklistZeebeIntegrationTest {

  private static final int IMPORTER_BATCH_SIZE = 2;

  @Autowired private TaskStore taskStore;

  @DynamicPropertySource
  protected static void registerProperties(final DynamicPropertyRegistry registry) {
    // make batch size smaller
    if (IS_ELASTIC) {
      registry.add(
          TasklistProperties.PREFIX + ".zeebeElasticsearch.batchSize", () -> IMPORTER_BATCH_SIZE);
    } else {
      registry.add(
          TasklistProperties.PREFIX + ".zeebeOpensearch.batchSize", () -> IMPORTER_BATCH_SIZE);
    }
  }

  @Test
  public void testTasksAreImported() throws Exception {
    // having
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "utFlowNode";
    tester.createAndDeploySimpleProcess(processId, flowNodeBpmnId).waitUntil().processIsDeployed();
    final List<Long> processInstanceKeys =
        IntStream.range(0, 20)
            .mapToLong(
                i -> Long.valueOf(ZeebeTestUtil.startProcessInstance(zeebeClient, processId, null)))
            .boxed()
            .toList();

    // split by partitions
    final var keysByPartition =
        processInstanceKeys.stream()
            .collect(Collectors.groupingBy(k -> Protocol.decodePartitionId(k)));

    // wait for Zeebe to export data
    Thread.sleep(10000L);

    // split processInstanceKeys by batch size and remove "middle part" of Zeebe data
    final List<Long> keysForDeletion = new ArrayList<>();
    for (final var partition : keysByPartition.values()) {
      final AtomicInteger counter = new AtomicInteger();
      final Map<Integer, List<Long>> splittedKeys =
          partition.stream()
              .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / IMPORTER_BATCH_SIZE));
      for (int i = 1; i < splittedKeys.size() - 1; i++) {
        keysForDeletion.addAll(splittedKeys.get(i));
      }
    }
    // delete all Zeebe records related to processInstanceKey=keysForDeletion
    databaseTestExtension.deleteByTermsQuery(
        zeebeExtension.getPrefix() + "*", "value.processInstanceKey", keysForDeletion, Long.class);

    // refresh Zeebe indices
    databaseTestExtension.refreshZeebeIndices();

    // trigger Tasklist importer
    tester
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, processInstanceKeys.size() - keysForDeletion.size());

    // then all expected tasks are loaded - importer is not blocked
    final List<TaskSearchView> taskEntities =
        processInstanceKeys.stream()
            .map(String::valueOf)
            .flatMap(
                processInstanceKey ->
                    taskStore
                        .getTasks(
                            new TaskQuery()
                                .setProcessInstanceId(processInstanceKey)
                                .setPageSize(50))
                        .stream())
            .toList();

    assertThat(taskEntities).isNotEmpty();
    assertThat(
            taskEntities.stream()
                .map(TaskSearchView::getProcessInstanceId)
                .map(Long::valueOf)
                .collect(Collectors.toSet()))
        .isEqualTo(new HashSet<>(CollectionUtils.subtract(processInstanceKeys, keysForDeletion)));
  }
}
