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
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchISMOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.indices.IndexSettings;

@ExtendWith(MockitoExtension.class)
public class OpensearchSchemaManagerTest {

  @Mock OperateProperties operateProperties;
  @Mock RichOpenSearchClient richOpenSearchClient;
  @Mock List<TemplateDescriptor> templateDescriptors;
  @Mock List<IndexDescriptor> indexDescriptors;

  @InjectMocks OpensearchSchemaManager underTest;

  @Test
  public void testCheckAndUpdateIndices() {
    final OperateOpensearchProperties operateOpensearchProperties =
        mock(OperateOpensearchProperties.class);
    when(operateOpensearchProperties.getNumberOfReplicas()).thenReturn(5);
    when(operateOpensearchProperties.getRefreshInterval()).thenReturn("2s");
    when(operateProperties.getOpensearch()).thenReturn(operateOpensearchProperties);

    final IndexSettings indexSettings1 = mock(IndexSettings.class);
    final IndexSettings indexSettings2 = mock(IndexSettings.class);
    final IndexSettings indexSettings3 = mock(IndexSettings.class);
    final Time time = mock(Time.class);
    when(time.time()).thenReturn("1s");

    when(indexSettings1.numberOfReplicas()).thenReturn("5");
    when(indexSettings1.refreshInterval()).thenReturn(time);
    when(indexSettings2.numberOfReplicas()).thenReturn("3");
    when(indexSettings2.refreshInterval()).thenReturn(time);
    when(indexSettings3.numberOfReplicas()).thenReturn(null);
    when(indexSettings3.refreshInterval()).thenReturn(null);

    final OpenSearchIndexOperations openSearchIndexOperations =
        mock(OpenSearchIndexOperations.class);
    when(richOpenSearchClient.index()).thenReturn(openSearchIndexOperations);
    when(openSearchIndexOperations.getIndexNamesWithRetries("*"))
        .thenReturn(Set.of("index1", "index2", "index3"));
    when(openSearchIndexOperations.getIndexSettingsWithRetries("index1"))
        .thenReturn(indexSettings1);
    when(openSearchIndexOperations.getIndexSettingsWithRetries("index2"))
        .thenReturn(indexSettings2);
    when(openSearchIndexOperations.getIndexSettingsWithRetries("index3"))
        .thenReturn(indexSettings3);

    when(openSearchIndexOperations.setIndexSettingsFor(any(), anyString()))
        .thenReturn(true)
        .thenReturn(false);

    final OpenSearchISMOperations openSearchISMOperations = mock(OpenSearchISMOperations.class);
    final ArchiverProperties archiverProperties = mock(ArchiverProperties.class);
    // Retention Policy
    when(richOpenSearchClient.ism()).thenReturn(openSearchISMOperations);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(openSearchISMOperations.getPolicy(OPERATE_DELETE_ARCHIVED_INDICES))
        .thenReturn(new LinkedHashMap<>());
    when(archiverProperties.isIlmEnabled()).thenReturn(true);

    final Map<String, Object> mockPolicies = new HashMap<>();
    final LinkedHashMap<String, String> indexPolicy = new LinkedHashMap<>();
    mockPolicies.put("index1", indexPolicy);
    mockPolicies.put("index2", indexPolicy);
    mockPolicies.put("index3", indexPolicy);

    when(openSearchISMOperations.getAttachedPolicy("*")).thenReturn(mockPolicies);
    when(openSearchISMOperations.addPolicyToIndex(anyString(), anyString())).thenReturn(null);

    underTest.checkAndUpdateIndices();

    verify(openSearchIndexOperations, times(3)).getIndexSettingsWithRetries(anyString());
    verify(openSearchIndexOperations, times(2)).setIndexSettingsFor(any(), anyString());
    verify(openSearchISMOperations, times(3)).addPolicyToIndex(anyString(), anyString());
  }
}
