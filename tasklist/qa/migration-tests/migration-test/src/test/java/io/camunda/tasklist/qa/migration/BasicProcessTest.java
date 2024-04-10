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
package io.camunda.tasklist.qa.migration;

import static io.camunda.tasklist.schema.templates.TaskTemplate.BPMN_PROCESS_ID;
import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.qa.migration.util.AbstractMigrationTest;
import io.camunda.tasklist.qa.migration.v810.BasicProcessDataGenerator;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BasicProcessTest extends AbstractMigrationTest {

  private final String bpmnProcessId = BasicProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> taskIds;

  @BeforeEach
  public void findTaskIds() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    if (taskIds == null) {
      sleepFor(5_000);
      final SearchRequest searchRequest = new SearchRequest(taskTemplate.getAlias());
      // task list
      searchRequest.source().query(termQuery(BPMN_PROCESS_ID, bpmnProcessId));
      try {
        taskIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(taskIds).hasSize(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT);
    }
  }

  @Test
  public void testImportPositions() {
    final List<ImportPositionEntity> importPositions =
        entityReader.getEntitiesFor(importPositionIndex.getAlias(), ImportPositionEntity.class);
    assertThat(importPositions.isEmpty())
        .describedAs("There should exists at least 1 ImportPosition")
        .isFalse();
  }

  @Test
  public void testTasks() throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(new SearchSourceBuilder().query(termQuery(BPMN_PROCESS_ID, bpmnProcessId)));
    final List<TaskEntity> tasks = entityReader.searchEntitiesFor(searchRequest, TaskEntity.class);
    assertThat(tasks).hasSize(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT);
    // 1.3.0-0_task_clean_assignee.json is not anymore part in the migration path
    // assertThat(tasks).extracting(ASSIGNEE).containsOnlyNulls();
  }

  @Test
  public void testUsers() {
    final List<UserEntity> users =
        entityReader.getEntitiesFor(userIndex.getAlias(), UserEntity.class);
    assertThat(users.size()).isEqualTo(3);
    assertThat(users).extracting(UserIndex.USER_ID).contains("demo", "act", "view");
  }
}
