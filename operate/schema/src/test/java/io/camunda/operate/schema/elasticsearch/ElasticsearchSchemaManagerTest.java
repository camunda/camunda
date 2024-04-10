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
import static io.camunda.operate.schema.SchemaManager.REFRESH_INTERVAL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    when(operateElasticsearchProperties.getRefreshInterval()).thenReturn("2s");
    when(operateProperties.getElasticsearch()).thenReturn(operateElasticsearchProperties);

    final Map<String, String> updatedSettings = new HashMap<>();
    updatedSettings.put(NUMBERS_OF_REPLICA, "5");
    updatedSettings.put(REFRESH_INTERVAL, "2s");

    final Settings indexSettings = Settings.builder().loadFromMap(updatedSettings).build();
    when(retryElasticsearchClient.setIndexSettingsFor(indexSettings, "*")).thenReturn(true);

    underTest.checkAndUpdateIndices();

    verify(retryElasticsearchClient, times(1)).setIndexSettingsFor(any(), anyString());
  }
}
