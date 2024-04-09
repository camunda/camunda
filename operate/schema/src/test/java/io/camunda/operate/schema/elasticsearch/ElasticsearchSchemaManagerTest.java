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
package io.camunda.operate.schema.elasticsearch;

import static io.camunda.operate.schema.SchemaManager.NUMBERS_OF_REPLICA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchSchemaManagerTest {

  @Mock protected RetryElasticsearchClient retryElasticsearchClient;
  @Mock protected OperateProperties operateProperties;
  @Mock private List<AbstractIndexDescriptor> indexDescriptors;
  @Mock private List<TemplateDescriptor> templateDescriptors;
  @Mock private List<Integer> testList;
  @InjectMocks private ElasticsearchSchemaManager underTest;

  @Test
  public void testCheckAndUpdateIndices() {
    final OperateElasticsearchProperties operateElasticsearchProperties =
        mock(OperateElasticsearchProperties.class);
    when(operateElasticsearchProperties.getNumberOfReplicas()).thenReturn(5);
    when(operateProperties.getElasticsearch()).thenReturn(operateElasticsearchProperties);
    when(retryElasticsearchClient.getIndexNames("*"))
        .thenReturn(Set.of("index1", "index2", "index3"));

    final Map<String, String> indexMap1 = new HashMap<>();
    indexMap1.put(NUMBERS_OF_REPLICA, "5");
    final Map<String, String> indexMap2 = new HashMap<>();
    indexMap2.put(NUMBERS_OF_REPLICA, "3");
    final Map<String, String> indexMap3 = new HashMap<>();
    when(retryElasticsearchClient.getIndexSettingsFor("index1", NUMBERS_OF_REPLICA))
        .thenReturn(indexMap1);
    when(retryElasticsearchClient.getIndexSettingsFor("index2", NUMBERS_OF_REPLICA))
        .thenReturn(indexMap2);
    when(retryElasticsearchClient.getIndexSettingsFor("index3", NUMBERS_OF_REPLICA))
        .thenReturn(indexMap3);

    final Map<String, String> index2UpdatedSettings = new HashMap<>();
    index2UpdatedSettings.put(NUMBERS_OF_REPLICA, "5");
    final Map<String, String> index3UpdatedSettings = new HashMap<>();
    index3UpdatedSettings.put(NUMBERS_OF_REPLICA, "5");
    final Settings index2Settings = Settings.builder().loadFromMap(index2UpdatedSettings).build();
    final Settings index3Settings = Settings.builder().loadFromMap(index3UpdatedSettings).build();
    when(retryElasticsearchClient.setIndexSettingsFor(index2Settings, "index2")).thenReturn(true);
    when(retryElasticsearchClient.setIndexSettingsFor(index3Settings, "index3")).thenReturn(false);

    final ArchiverProperties archiverProperties = mock(ArchiverProperties.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);

    underTest.checkAndUpdateIndices();

    verify(retryElasticsearchClient, times(3)).getIndexSettingsFor(anyString(), anyString());
    verify(retryElasticsearchClient, times(3)).setIndexSettingsFor(any(), anyString());
  }
}
