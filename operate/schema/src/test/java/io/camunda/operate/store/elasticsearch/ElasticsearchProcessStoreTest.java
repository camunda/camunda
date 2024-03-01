/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchProcessStoreTest {

  @Mock private ProcessIndex processIndex;

  @Mock private ListViewTemplate listViewTemplate;

  private List<ProcessInstanceDependant> processInstanceDependantTemplates = new LinkedList<>();

  @Mock private ObjectMapper objectMapper;

  @Mock private RestHighLevelClient esClient;

  @Mock private TenantAwareElasticsearchClient tenantAwareClient;

  @Mock private OperateProperties operateProperties;

  private ElasticsearchProcessStore underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new ElasticsearchProcessStore(
            processIndex,
            listViewTemplate,
            processInstanceDependantTemplates,
            objectMapper,
            operateProperties,
            esClient,
            tenantAwareClient);
  }

  @Test
  public void testExceptionDuringGetDistinctCountFor() throws IOException {
    when(tenantAwareClient.search(any())).thenThrow(new IOException());
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    Optional<Long> result = underTest.getDistinctCountFor("foo");

    assertThat(result).isNotNull();
    assertThat(result.isEmpty());
  }

  @Test
  public void testGetProcessByKeyTooManyResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(2L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(NotFoundException.class, () -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testGetProcessByKeyNoResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(NotFoundException.class, () -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testGetProcessByKeyWithException() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(OperateRuntimeException.class, () -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testGetDiagramByKeyNoResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(NotFoundException.class, () -> underTest.getDiagramByKey(123L));
  }

  @Test
  public void testGetDiagramByKeyTooManyResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(2L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(NotFoundException.class, () -> underTest.getDiagramByKey(123L));
  }

  @Test
  public void testGetDiagramByKeyWithException() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(OperateRuntimeException.class, () -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testExceptionDuringGetProcessesGrouped() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class,
        () -> underTest.getProcessesGrouped(DEFAULT_TENANT_ID, Set.of("demoProcess")));
  }

  @Test
  public void testExceptionDuringGetProcessesIdsToProcessesWithFields() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class,
        () ->
            underTest.getProcessesIdsToProcessesWithFields(
                Set.of("demoProcess", "demoProcess-1"), 10, "name", "bpmnProcessId", "key"));
  }

  @Test
  public void testGetProcessInstanceListViewByKeyTooManyResults() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(2L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(NotFoundException.class, () -> underTest.getProcessInstanceListViewByKey(123L));
  }

  @Test
  public void testGetProcessInstanceListViewByKeyNoResults() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(NotFoundException.class, () -> underTest.getProcessInstanceListViewByKey(123L));
  }

  @Test
  public void testGetProcessInstanceListViewByKeyWithException() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class, () -> underTest.getProcessInstanceListViewByKey(123L));
  }

  @Test
  public void testExceptionDuringGetCoreStatistics() throws IOException {
    when(listViewTemplate.getFullQualifiedName()).thenReturn("listViewIndexPath");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class, () -> underTest.getCoreStatistics(Set.of("demoProcess")));
  }

  @Test
  public void testGetProcessInstanceTreePathByIdNoResults() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");

    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThrows(
        NotFoundException.class,
        () -> underTest.getProcessInstanceTreePathById("PI_2251799813685251"));
  }

  @Test
  public void testExceptionDuringGetProcessInstanceTreePathById() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class,
        () -> underTest.getProcessInstanceTreePathById("PI_2251799813685251"));
  }

  @Test
  public void testExceptionDuringDeleteProcessInstanceFromTreePath() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class,
        () -> underTest.deleteProcessInstanceFromTreePath("2251799813685251"));
  }

  @Test
  public void testGetProcessInstancesByProcessAndStatesWithNullStates() {
    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> underTest.getProcessInstancesByProcessAndStates(123L, null, 10, null));
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'states' is needed to search by states.");
  }

  @Test
  public void testGetProcessInstancesByProcessAndStatesWithEmptyStates() {
    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> underTest.getProcessInstancesByProcessAndStates(123L, Set.of(), 10, null));
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'states' is needed to search by states.");
  }

  @Test
  public void testExceptionDuringGetProcessInstancesByProcessAndStates() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () ->
                underTest.getProcessInstancesByProcessAndStates(
                    123L, Set.of(ProcessInstanceState.COMPLETED), 10, null));
    assertThat(exception.getMessage())
        .contains("Failed to search process instances by processDefinitionKey");
  }

  @Test
  public void testGetProcessInstancesByParentKeysWithNullKeys() {
    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> underTest.getProcessInstancesByParentKeys(null, 10, null));
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
  }

  @Test
  public void testGetProcessInstancesByParentKeysWithEmptyKeys() {
    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> underTest.getProcessInstancesByParentKeys(Set.of(), 10, null));
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
  }

  @Test
  public void testExceptionDuringGetProcessInstancesByParentKeys() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any(), any())).thenThrow(new IOException());

    Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> underTest.getProcessInstancesByParentKeys(Set.of(123L), 10, null));
    assertThat(exception.getMessage())
        .contains("Failed to search process instances by parentProcessInstanceKeys");
  }

  @Test
  public void testDeleteProcessInstancesAndDependantsWithNullKey() {
    long deleted = underTest.deleteProcessInstancesAndDependants(null);
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testDeleteProcessInstancesAndDependantsWithEmptyKey() {
    long deleted = underTest.deleteProcessInstancesAndDependants(Set.of());
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testExceptionDuringDeleteProcessInstancesAndDependants() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(esClient.deleteByQuery(any(), eq(RequestOptions.DEFAULT))).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class,
        () -> underTest.deleteProcessInstancesAndDependants(Set.of(123L)));
  }

  @Test
  public void testDeleteProcessDefinitionsByKeysWithNullKey() {
    Long[] keys = null;
    long deleted = underTest.deleteProcessDefinitionsByKeys(keys);
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testDeleteProcessDefinitionsByKeysWithEmptyKey() {
    long deleted = underTest.deleteProcessDefinitionsByKeys(new Long[0]);
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testExceptionDuringDeleteProcessDefinitionsByKeys() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(esClient.deleteByQuery(any(), eq(RequestOptions.DEFAULT))).thenThrow(new IOException());

    assertThrows(
        OperateRuntimeException.class, () -> underTest.deleteProcessDefinitionsByKeys(123L, 234L));
  }

  @Test
  public void testRefreshIndicesWithNullIndex() {
    String[] indices = null;
    Exception exception =
        assertThrows(OperateRuntimeException.class, () -> underTest.refreshIndices(indices));
    assertThat(exception.getMessage())
        .isEqualTo("Refresh indices needs at least one index to refresh.");
  }

  @Test
  public void testRefreshIndicesWithEmptyIndexArray() {
    Exception exception =
        assertThrows(OperateRuntimeException.class, () -> underTest.refreshIndices(new String[0]));
    assertThat(exception.getMessage())
        .isEqualTo("Refresh indices needs at least one index to refresh.");
  }
}
