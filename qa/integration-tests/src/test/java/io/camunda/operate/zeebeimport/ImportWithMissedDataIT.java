/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.*;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.zeebe.protocol.Protocol;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplication.class},
    properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
        OperateProperties.PREFIX + ".multiTenancy.enabled = true",
        //make batch size smaller
        OperateProperties.PREFIX + ".zeebeElasticsearch.batchSize = 2"})
public class ImportWithMissedDataIT extends OperateZeebeAbstractIT {

  @Autowired
  private TestSearchRepository searchRepository;

  @Autowired
  private ListViewReader listViewReader;

  @Test
  public void testProcessInstanceCreated() throws IOException {
    // having
    String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");
    List<Long> processInstanceKeys = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}"));
    }
    //remove "middle part" of Zeebe data
    //split by partitions
    Map<Integer, List<Long>> keysByPartition = processInstanceKeys.stream()
        .collect(Collectors.groupingBy(a -> Protocol.decodePartitionId(a)));
    List<Long> keysForDeletion = new ArrayList<>();
    for (Map.Entry<Integer, List<Long>> partition: keysByPartition.entrySet()) {
      AtomicInteger counter = new AtomicInteger();
      Map<Integer, List<Long>> splittedKeys = partition.getValue().stream()
          .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / 2));
      for (int j = 1; j < splittedKeys.size() - 1; j ++) {
        keysForDeletion.addAll(splittedKeys.get(j));
      }
    }

    //wait for Zeebe to export data
    sleepFor(3000L);
    searchRepository.deleteByTermsQuery(zeebeRule.getPrefix() + "*", "value.processInstanceKey", keysForDeletion);
    searchTestRule.refreshZeebeIndices();
    processInstanceKeys.removeAll(keysForDeletion);

    //when importer runs
    searchTestRule.processAllRecordsAndWait(processInstancesAreStartedCheck, processInstanceKeys);

    //then all expected instances are loaded - imported is not blocked
    ListViewResponseDto listViewResponseDto = listViewReader.queryProcessInstances(
        createGetAllProcessInstancesRequest(q -> q.setIds(processInstanceKeys.stream().map(String::valueOf).collect(
            Collectors.toList()))));

    assertThat(listViewResponseDto.getTotalCount()).isEqualTo(processInstanceKeys.size());
  }


}
