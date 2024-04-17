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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.*;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      // make batch size smaller
      OperateProperties.PREFIX + ".zeebeElasticsearch.batchSize = 2"
    })
public class ImportWithMissedDataZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private TestSearchRepository searchRepository;

  @Autowired private ListViewReader listViewReader;

  @Test
  public void testProcessInstanceCreated() throws IOException {
    // having
    final String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");
    final List<Long> processInstanceKeys = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      processInstanceKeys.add(
          ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}"));
    }
    // remove "middle part" of Zeebe data
    // split by partitions
    final Map<Integer, List<Long>> keysByPartition =
        processInstanceKeys.stream()
            .collect(Collectors.groupingBy(a -> Protocol.decodePartitionId(a)));
    final List<Long> keysForDeletion = new ArrayList<>();
    for (final Map.Entry<Integer, List<Long>> partition : keysByPartition.entrySet()) {
      final AtomicInteger counter = new AtomicInteger();
      final Map<Integer, List<Long>> splittedKeys =
          partition.getValue().stream()
              .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / 2));
      for (int j = 1; j < splittedKeys.size() - 1; j++) {
        keysForDeletion.addAll(splittedKeys.get(j));
      }
    }

    // wait for Zeebe to export data
    sleepFor(3000L);
    searchRepository.deleteByTermsQuery(
        zeebeRule.getPrefix() + "*", "value.processInstanceKey", keysForDeletion);
    searchTestRule.refreshZeebeIndices();
    processInstanceKeys.removeAll(keysForDeletion);

    // when importer runs
    searchTestRule.processAllRecordsAndWait(processInstancesAreStartedCheck, processInstanceKeys);

    // then all expected instances are loaded - imported is not blocked
    final ListViewResponseDto listViewResponseDto =
        listViewReader.queryProcessInstances(
            createGetAllProcessInstancesRequest(
                q ->
                    q.setIds(
                        processInstanceKeys.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()))));

    assertThat(listViewResponseDto.getTotalCount()).isEqualTo(processInstanceKeys.size());
  }
}
