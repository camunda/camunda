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
package io.camunda.operate.schema;

import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import java.util.Map;
import java.util.Set;

public interface SchemaManager {

  String REFRESH_INTERVAL = "index.refresh_interval";
  String NO_REFRESH = "-1";
  String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  String NO_REPLICA = "0";

  String OPERATE_DELETE_ARCHIVED_INDICES = "operate_delete_archived_indices";
  String INDEX_LIFECYCLE_NAME = "index.lifecycle.name";
  String DELETE_PHASE = "delete";

  void createSchema();

  void createDefaults();

  void createIndex(IndexDescriptor indexDescriptor, String indexClasspathResource);

  void createTemplate(TemplateDescriptor templateDescriptor, String templateClasspathResource);

  boolean setIndexSettingsFor(Map<String, ?> settings, String indexPattern);

  String getOrDefaultRefreshInterval(String indexName, String defaultValue);

  String getOrDefaultNumbersOfReplica(String indexName, String defaultValue);

  void refresh(final String indexPattern);

  boolean isHealthy();

  Set<String> getIndexNames(final String indexPattern);

  Set<String> getAliasesNames(final String indexPattern);

  long getNumberOfDocumentsFor(final String... indexPatterns);

  boolean deleteIndicesFor(final String indexPattern);

  boolean deleteTemplatesFor(final String deleteTemplatePattern);

  void removePipeline(String pipelineName);

  boolean addPipeline(String name, String pipelineDefinition);

  Map<String, String> getIndexSettingsFor(String s, String... fields);

  String getIndexPrefix();

  Map<String, IndexMapping> getIndexMappings(String indexNamePattern);

  void updateSchema(Map<IndexDescriptor, Set<IndexMappingProperty>> newFields);

  IndexMapping getExpectedIndexFields(IndexDescriptor indexDescriptor);
}
