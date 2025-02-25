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
package io.camunda.operate.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.TestUtil;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = OperateProperties.PREFIX + ".database=opensearch")
public class OpensearchBatchRequestIT extends OperateAbstractIT {

  @Autowired RichOpenSearchClient richOpenSearchClient;

  @Autowired ProcessIndex processIndex;

  @Autowired SchemaManager schemaManager;

  @Autowired OperateProperties operateProperties;
  private String indexPrefix;

  @Before
  public void setUp() {
    indexPrefix = "test-batch-request-" + TestUtil.createRandomString(5);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
    schemaManager.createSchema();
  }

  @After
  public void cleanUp() {
    schemaManager.deleteIndicesFor(indexPrefix + "*");
  }

  @Test
  public void canUseRichClient() {
    assertThat(richOpenSearchClient).isNotNull();
    assertThat(searchForProcessEntity(matchAll())).isEmpty();
  }

  @Test
  public void shouldAdd() throws PersistenceException {
    // given
    final var batchRequest = richOpenSearchClient.batch().newBatchRequest();
    batchRequest
        .add(
            processIndex.getFullQualifiedName(),
            new ProcessEntity()
                .setId("1")
                .setBpmnProcessId("bpmnProcessId")
                .setVersion(1)
                .setBpmnXml("xml")
                .setResourceName("resource")
                .setName("name1"))
        .add(
            processIndex.getFullQualifiedName(),
            new ProcessEntity()
                .setId("2")
                .setBpmnProcessId("bpmnProcessId2")
                .setVersion(1)
                .setBpmnXml("xml")
                .setResourceName("resource")
                .setName("name2"))
        .executeWithRefresh();
    // when
    final var foundProcesses = searchForProcessEntity(matchAll());
    // then
    assertThat(foundProcesses.size()).isEqualTo(2);
    final var firstFoundProcess = foundProcesses.get(0);
    assertThat(firstFoundProcess.getName()).isEqualTo("name1");
    assertThat(firstFoundProcess.getId()).isEqualTo("1");
  }

  @Test
  public void shouldUpdateWithIdAndOperateEntity() throws PersistenceException {
    // given
    shouldAdd();
    final var newProcessEntity =
        new ProcessEntity()
            .setId("1")
            .setBpmnProcessId("bpmnProcessId")
            .setVersion(1)
            .setBpmnXml("xml")
            .setResourceName("resource")
            .setName("newName");
    // when
    newBatchRequest()
        .update(processIndex.getFullQualifiedName(), "1", newProcessEntity)
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("newName");
  }

  @Test
  public void shouldUpdateWithIdAndFields() throws PersistenceException {
    // given
    shouldAdd();
    // when
    newBatchRequest()
        .update(processIndex.getFullQualifiedName(), "1", Map.of("name", "newName"))
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("newName");
  }

  @Test
  public void shouldUpdateWithScript() throws PersistenceException {
    // given
    shouldAdd();
    // when
    final var script = "ctx._source.name += params.secondName;";
    final Map<String, Object> parameters = Map.of("secondName", "-anotherName");
    newBatchRequest()
        .updateWithScript(processIndex.getFullQualifiedName(), "1", script, parameters)
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("name1-anotherName");
  }

  @Test
  public void shouldUpsert() throws PersistenceException {
    // given
    shouldAdd();
    // when
    final var processEntity =
        new ProcessEntity()
            .setId("5")
            .setBpmnProcessId("bpmnProcessId")
            .setVersion(1)
            .setBpmnXml("xml")
            .setResourceName("resource")
            .setName("name5");
    newBatchRequest()
        .upsert(processIndex.getFullQualifiedName(), "5", processEntity, Map.of())
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 5L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("name5");
  }

  private BatchRequest newBatchRequest() {
    return richOpenSearchClient.batch().newBatchRequest();
  }

  private List<ProcessEntity> searchForProcessEntity(Query query) {
    return richOpenSearchClient
        .doc()
        .search(
            searchRequestBuilder(processIndex.getFullQualifiedName()).query(query),
            ProcessEntity.class)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .toList();
  }
}
