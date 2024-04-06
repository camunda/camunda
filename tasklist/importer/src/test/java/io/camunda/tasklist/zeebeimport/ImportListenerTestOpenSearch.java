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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.NoBeansTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.os.ImportBatchOpenSearch;
import io.camunda.tasklist.zeebeimport.os.ImportJobOpenSearch;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ImportJobOpenSearch.class,
      ImportListenerTestOpenSearch.TestImportListener.class
    })
@Conditional(OpenSearchCondition.class)
public class ImportListenerTestOpenSearch extends NoBeansTest {

  @MockBean private ImportBatchProcessorFactory importBatchProcessorFactory;

  @MockBean private ImportBatchProcessor importBatchProcessor;

  @MockBean private ImportPositionHolder importPositionHolder;

  @MockBean
  @Qualifier("zeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  @MockBean private RecordsReaderHolder recordsReaderHolder;

  @MockBean private TasklistProperties tasklistProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private TestImportListener importListener;

  @BeforeEach
  public void before() {
    importListener.cancel();
  }

  @Test
  public void testFinished() throws Exception {
    final ImportBatch importBatchOpenSearch =
        new ImportBatchOpenSearch(
            1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), "some_name");
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchOpenSearch, previousPosition);

    // mock import methods
    try {
      when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
          .thenReturn(importBatchProcessor);
      doNothing().when(importBatchProcessor).performImport(importBatchOpenSearch);
    } catch (PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFinishedCalled());
    assertFalse(importListener.isFailedCalled());
    assertEquals(importListener.getImportBatch(), importBatchOpenSearch);
  }

  @Test
  public void testFailed() throws Exception {
    final ImportBatchOpenSearch importBatchElasticSearch =
        new ImportBatchOpenSearch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), null);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchElasticSearch, previousPosition);
    // mock import methods
    try {
      doThrow(new PersistenceException())
          .when(importBatchProcessor)
          .performImport(importBatchElasticSearch);
    } catch (PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFailedCalled());
    assertFalse(importListener.isFinishedCalled());
    assertEquals(importListener.getImportBatch(), importBatchElasticSearch);
  }

  @Component
  static class TestImportListener implements ImportListener {

    private boolean finishedCalled = false;
    private boolean failedCalled = false;
    private ImportBatch importBatchElasticSearch;

    @Override
    public void finished(ImportBatch importBatchElasticSearch) {
      finishedCalled = true;
      this.importBatchElasticSearch = importBatchElasticSearch;
    }

    @Override
    public void failed(ImportBatch importBatchElasticSearch) {
      failedCalled = true;
      this.importBatchElasticSearch = importBatchElasticSearch;
    }

    public boolean isFinishedCalled() {
      return finishedCalled;
    }

    public boolean isFailedCalled() {
      return failedCalled;
    }

    public ImportBatch getImportBatch() {
      return importBatchElasticSearch;
    }

    public void cancel() {
      finishedCalled = false;
      failedCalled = false;
      importBatchElasticSearch = null;
    }
  }
}
