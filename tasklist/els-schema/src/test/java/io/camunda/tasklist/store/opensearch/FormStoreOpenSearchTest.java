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
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormStoreOpenSearchTest {
  @Mock private FormIndex formIndex = new FormIndex();

  @Mock private TaskTemplate taskTemplate = new TaskTemplate();

  @Mock private ProcessIndex processIndex = new ProcessIndex();

  @Mock private TenantAwareOpenSearchClient tenantAwareClient;

  @InjectMocks private FormStoreOpenSearch instance;

  @Test
  void getFormWhenFormNotFound() throws IOException {
    when(formIndex.getIndexName()).thenReturn(FormIndex.INDEX_NAME);

    final var formSearchResponse = mock(SearchResponse.class);
    when(taskTemplate.getFullQualifiedName()).thenReturn("tasklist-task-x.0.0");
    when(processIndex.getFullQualifiedName()).thenReturn("tasklist-process-x.0.0");
    when(tenantAwareClient.search(
            (SearchRequest.Builder) any(SearchRequest.Builder.class), (Class<Object>) any()))
        .thenReturn(formSearchResponse);
    final var hitsMetadata = mock(HitsMetadata.class);
    when(formSearchResponse.hits()).thenReturn(hitsMetadata);
    final var totalHits = mock(TotalHits.class);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(totalHits.value()).thenReturn(0L);

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("form with id id1 was not found");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    when(tenantAwareClient.search(any(SearchRequest.Builder.class), eq(FormEntity.class)))
        .thenThrow(new IOException("some IO exception"));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("some IO exception")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void getForm() throws IOException {
    final var providedFormEntity =
        new FormEntity()
            .setId("id1")
            .setProcessDefinitionId("processDefId1")
            .setBpmnId("bpmnId1")
            .setSchema("");
    final var formSearchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(SearchRequest.Builder.class), eq(FormEntity.class)))
        .thenReturn(formSearchResponse);
    final var hitsMetadata = mock(HitsMetadata.class);
    when(formSearchResponse.hits()).thenReturn(hitsMetadata);
    final var totalHits = mock(TotalHits.class);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(totalHits.value()).thenReturn(1L);
    final var hit = mock(Hit.class);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    when(hit.source()).thenReturn(providedFormEntity);

    // when
    final var result = instance.getForm("id1", "processDefId1", null);

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
