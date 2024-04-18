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

import static io.camunda.operate.store.elasticsearch.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
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
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    final AbstractIndexDescriptor abstractIndexDescriptor1 = mock(AbstractIndexDescriptor.class);
    final AbstractIndexDescriptor abstractIndexDescriptor2 = mock(AbstractIndexDescriptor.class);
    final AbstractIndexDescriptor abstractIndexDescriptor3 = mock(AbstractIndexDescriptor.class);
    when(abstractIndexDescriptor1.getIndexName()).thenReturn("index1");
    when(abstractIndexDescriptor2.getIndexName()).thenReturn("index2");
    when(abstractIndexDescriptor3.getIndexName()).thenReturn("index3");
    when(abstractIndexDescriptor2.getDerivedIndexNamePattern()).thenReturn("index2*");
    when(abstractIndexDescriptor3.getDerivedIndexNamePattern()).thenReturn("index3*");
    when(abstractIndexDescriptor1.getFullQualifiedName()).thenReturn("index1");
    when(abstractIndexDescriptor2.getFullQualifiedName()).thenReturn("index2");
    when(abstractIndexDescriptor3.getFullQualifiedName()).thenReturn("index3");

    final TemplateDescriptor templateDescriptor1 = mock(TemplateDescriptor.class);
    when(templateDescriptor1.getAlias()).thenReturn("template1_alias");
    when(templateDescriptor1.getTemplateName()).thenReturn("template1_template");
    when(templateDescriptor1.getIndexPattern()).thenReturn("template1_*");

    when(templateDescriptor1.getSchemaClasspathFilename())
        .thenReturn("/schema/elasticsearch/create/template/operate-batch-operation.json");

    ReflectionTestUtils.setField(
        underTest, "templateDescriptors", Collections.singletonList(templateDescriptor1));

    ReflectionTestUtils.setField(
        underTest,
        "indexDescriptors",
        Arrays.asList(
            abstractIndexDescriptor1, abstractIndexDescriptor2, abstractIndexDescriptor3));

    final OperateElasticsearchProperties operateElasticsearchProperties =
        mock(OperateElasticsearchProperties.class);
    when(operateProperties.getElasticsearch()).thenReturn(operateElasticsearchProperties);
    when(operateElasticsearchProperties.getNumberOfReplicas()).thenReturn(3);
    when(operateElasticsearchProperties.getNumberOfShards()).thenReturn(5);
    when(retryElasticsearchClient.getIndexSettingsFor("index1", NUMBERS_OF_REPLICA))
        .thenReturn(Map.of(NUMBERS_OF_REPLICA, "1"));
    when(retryElasticsearchClient.getIndexSettingsFor("index2", NUMBERS_OF_REPLICA))
        .thenReturn(Map.of(NUMBERS_OF_REPLICA, "3"));
    when(retryElasticsearchClient.getIndexSettingsFor("index3", NUMBERS_OF_REPLICA))
        .thenReturn(Map.of(NUMBERS_OF_REPLICA, "2"));
    when(retryElasticsearchClient.setIndexSettingsFor(any(), anyString()))
        .thenReturn(true)
        .thenReturn(false);
    final Map<String, Integer> replicasMap = new HashMap<>();
    replicasMap.put("index1", 1);
    replicasMap.put("index2", 2);
    when(operateElasticsearchProperties.getNumberOfReplicasForIndices()).thenReturn(replicasMap);

    underTest.checkAndUpdateIndices();

    verify(retryElasticsearchClient, times(3)).setIndexSettingsFor(any(), anyString());
  }
}
