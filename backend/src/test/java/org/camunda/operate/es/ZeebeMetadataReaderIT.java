package org.camunda.operate.es;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.ZeebeMetadataReader;
import org.camunda.operate.es.writer.ElasticsearchBulkProcessor;
import org.camunda.operate.es.writer.PersistenceException;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.protocol.Protocol;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests elasticsearch queries for reading of metadata (maximal position per partition).
 */
@Ignore
public class ZeebeMetadataReaderIT extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  private Map<Integer, Long> positionPerPartitionIdMap = new HashMap<>();

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private ZeebeMetadataReader zeebeMetadataReader;

  private Random random = new Random();

  @Before
  public void starting() {
    createData();
  }

  @Test
  @Ignore //TODO logic will be changed
  public void testSelectMetadata() {
    final Map<Integer, Long> map = zeebeMetadataReader.getPositionPerPartitionMap();
    final Set<Map.Entry<Integer, Long>> entries = positionPerPartitionIdMap.entrySet();
    assertThat(map).containsExactly(entries.toArray(new Map.Entry[entries.size()]));
  }


  private void createData() {

    //fill map
    positionPerPartitionIdMap.put(Protocol.SYSTEM_PARTITION, 12L);
    positionPerPartitionIdMap.put(Protocol.SYSTEM_PARTITION + 1, 123L);
    positionPerPartitionIdMap.put(Protocol.SYSTEM_PARTITION + 2, 1234L);
    positionPerPartitionIdMap.put(Protocol.SYSTEM_PARTITION + 3, 12345L);
    positionPerPartitionIdMap.put(Protocol.SYSTEM_PARTITION + 4, 123456L);

    //create the data
    List<OperateEntity> operateEntities = new ArrayList<>();
    for (Map.Entry<Integer, Long> entry: positionPerPartitionIdMap.entrySet()) {
      if (entry.getKey() == Protocol.SYSTEM_PARTITION) {
        //create workflows
        operateEntities.add(createWorkflow((int)entry.getValue().longValue(), (int)entry.getValue().longValue()));      //the one with max position
        operateEntities.add(createWorkflow((int)entry.getValue().longValue()));
      } else {
        //create workflow instances
        operateEntities.add(createWorkflowInstance(entry.getKey(), (int)entry.getValue().longValue(), (int)entry.getValue().longValue()));      //the one with max position
        operateEntities.add(createWorkflowInstance(entry.getKey(), (int)entry.getValue().longValue()));
        operateEntities.add(createWorkflowInstance(entry.getKey(), (int)entry.getValue().longValue()));
        operateEntities.add(createWorkflowInstance(entry.getKey(), (int)entry.getValue().longValue()));
        operateEntities.add(createWorkflowInstance(entry.getKey(), (int)entry.getValue().longValue()));
      }
    }
    //persist instances
    try {
      elasticsearchBulkProcessor.persistOperateEntities(operateEntities);
    } catch (PersistenceException e) {
      throw new RuntimeException(e);
    }
    elasticsearchTestRule.refreshIndexesInElasticsearch();
  }

  private WorkflowInstanceEntity createWorkflowInstance(Integer partitionId, Integer maxPosition) {
    return createWorkflowInstance(partitionId, maxPosition, null);
  }

  private WorkflowInstanceEntity createWorkflowInstance(Integer partitionId, Integer maxPosition, Integer position) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess");
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    workflowInstance.setPartitionId(partitionId);
    if (position != null) {
      workflowInstance.setPosition(position);
    } else {
      //random, but less than max
      workflowInstance.setPosition(maxPosition - random.nextInt(maxPosition - 1));
    }
    return workflowInstance;
  }

  private WorkflowEntity createWorkflow(Integer maxPosition) {
    return createWorkflow(maxPosition, null);
  }

  private WorkflowEntity createWorkflow(Integer maxPosition, Integer position) {
    WorkflowEntity workflow = new WorkflowEntity();
    workflow.setId(UUID.randomUUID().toString());
    workflow.setName(UUID.randomUUID().toString());
    workflow.setVersion(1);
    workflow.setPartitionId(Protocol.SYSTEM_PARTITION);
    if (position != null) {
      workflow.setPosition(position);
    } else {
      //random, but less than max
      workflow.setPosition(maxPosition - random.nextInt(maxPosition - 1));
    }
    return workflow;
  }

}
