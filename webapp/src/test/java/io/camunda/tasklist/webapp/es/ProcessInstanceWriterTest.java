package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("checkstyle:FinalLocalVariable")
class ProcessInstanceWriterTest {

  public static final String PROCESS_INSTANCE_INDEX = "process_instance_index";
  public static final String TASK_TEMPLATE_INDEX = "task_template_index";
  public static final String TASK_TEMPLATE_PATTERN = "task_template_pattern";
  public static final String FLOW_NODE_INDEX = "flow_node_index";
  public static final String TASK_VARIABLE_TEMPLATE_PATTERN = "task_variable_template_pattern";
  @Mock FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Mock TaskTemplate taskTemplate;
  @Mock ProcessInstanceIndex processInstanceIndex;
  @Mock RetryElasticsearchClient retryElasticsearchClient;
  @Mock TaskReaderWriter taskReaderWriter;
  @Mock TaskVariableTemplate taskVariableTemplate;
  private final ProcessInstanceWriter subject = new ProcessInstanceWriter();

  @BeforeEach
  public void setUp() {
    subject.taskReaderWriter = taskReaderWriter;
    subject.processInstanceIndex = processInstanceIndex;
    subject.retryElasticsearchClient = retryElasticsearchClient;
    subject.taskTemplate = taskTemplate;
    subject.taskVariableTemplate = taskVariableTemplate;
    subject.processInstanceDependants = List.of(flowNodeInstanceIndex);

    when(processInstanceIndex.getFullQualifiedName()).thenReturn(PROCESS_INSTANCE_INDEX);
    when(taskTemplate.getIndexPattern()).thenReturn(TASK_TEMPLATE_PATTERN);
    lenient().when(flowNodeInstanceIndex.getFullQualifiedName()).thenReturn(FLOW_NODE_INDEX);
  }

  @Test
  void fallbackDeleteFromArchive() throws IOException {
    // Given: can't delete from runtime index
    final String instanceId = "instance ID";
    when(retryElasticsearchClient.deleteDocument(PROCESS_INSTANCE_INDEX, instanceId))
        .thenReturn(false);
    when(taskReaderWriter.getTaskIdsByProcessInstanceId(instanceId))
        .thenReturn(List.of("dependantId"));

    // When
    subject.deleteProcessInstance(instanceId);

    // Then: Should try to delete from archive index
    final String indexName =
        ElasticsearchUtil.whereToSearch(taskTemplate, ElasticsearchUtil.QueryType.ONLY_ARCHIVE);
    final TermsQueryBuilder query = termsQuery(PROCESS_INSTANCE_ID, instanceId);
    verify(retryElasticsearchClient).deleteDocumentsByQuery(indexName, query);
  }

  @Test
  void whenDeleteFromRuntimeDontDeleteFromArchive() throws IOException {
    // Given: it can delete from runtime index
    final String instanceId = "instance ID";
    when(retryElasticsearchClient.deleteDocument(PROCESS_INSTANCE_INDEX, instanceId))
        .thenReturn(true);
    when(taskReaderWriter.getTaskIdsByProcessInstanceId(instanceId))
        .thenReturn(List.of("dependantId"));
    lenient()
        .when(
            retryElasticsearchClient.deleteDocumentsByQuery(
                FLOW_NODE_INDEX, termQuery(PROCESS_INSTANCE_ID, instanceId)))
        .thenReturn(true);

    // When
    final boolean result = subject.deleteProcessInstance(instanceId);

    // Then: Should NOT try to delete from archive index
    assertTrue(result);
    final String indexName =
        ElasticsearchUtil.whereToSearch(taskTemplate, ElasticsearchUtil.QueryType.ONLY_ARCHIVE);
    final TermsQueryBuilder query = termsQuery(PROCESS_INSTANCE_ID, instanceId);
    verify(retryElasticsearchClient, times(0)).deleteDocumentsByQuery(indexName, query);
  }
}
