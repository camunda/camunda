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
package io.camunda.operate.schema.util.elasticsearch;

import static io.camunda.operate.schema.IndexMapping.IndexMappingProperty.createIndexMappingProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.schema.util.SchemaTestHelper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticsearchCondition.class)
public class ElasticsearchSchemaTestHelper implements SchemaTestHelper {

  @Autowired private SchemaManager schemaManager;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperateProperties properties;

  @Override
  public void dropSchema() {
    final String elasticsearchObjectPrefix = properties.getElasticsearch().getIndexPrefix() + "-*";
    final Set<String> indexesToDelete = schemaManager.getIndexNames(elasticsearchObjectPrefix);
    if (!indexesToDelete.isEmpty()) {
      // fails if there are no matching indexes
      setReadOnly(elasticsearchObjectPrefix, false);
    }

    schemaManager.deleteIndicesFor(elasticsearchObjectPrefix);
    schemaManager.deleteTemplatesFor(elasticsearchObjectPrefix);
  }

  @Override
  public IndexMapping getTemplateMappings(final TemplateDescriptor template) {
    try {
      final String templateName = template.getTemplateName();
      final Map<String, ComposableIndexTemplate> indexTemplates =
          esClient
              .indices()
              .getIndexTemplate(
                  new GetComposableIndexTemplateRequest(templateName), RequestOptions.DEFAULT)
              .getIndexTemplates();

      if (indexTemplates.isEmpty()) {
        return null;
      } else if (indexTemplates.size() > 1) {
        throw new OperateRuntimeException(
            String.format(
                "Found more than one template matching name %s. Expected one.", templateName));
      }

      final Map<String, Object> mappingMetadata =
          (Map<String, Object>)
              objectMapper
                  .readValue(
                      indexTemplates.get(templateName).template().mappings().toString(),
                      new TypeReference<HashMap<String, Object>>() {})
                  .get("properties");
      return new IndexMapping()
          .setIndexName(templateName)
          .setProperties(
              mappingMetadata.entrySet().stream()
                  .map(p -> createIndexMappingProperty(p))
                  .collect(Collectors.toSet()));
    } catch (final ElasticsearchException e) {
      if (e.status().equals(RestStatus.NOT_FOUND)) {
        return null;
      }
      throw e;
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public void createIndex(
      final IndexDescriptor indexDescriptor,
      final String indexName,
      final String indexSchemaFilename) {
    schemaManager.createIndex(
        new AbstractIndexDescriptor() {
          @Override
          public String getIndexName() {
            return indexDescriptor.getIndexName();
          }

          @Override
          public String getFullQualifiedName() {
            return indexName;
          }
        },
        indexSchemaFilename);
  }

  @Override
  public void setReadOnly(final String indexName, final boolean readOnly) {
    final UpdateSettingsRequest updateSettingsRequest =
        new UpdateSettingsRequest()
            .indices(indexName)
            .settings(Map.of("index.blocks.read_only", readOnly));
    try {
      esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }
}
