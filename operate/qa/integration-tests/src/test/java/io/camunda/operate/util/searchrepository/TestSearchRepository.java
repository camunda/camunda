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
package io.camunda.operate.util.searchrepository;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface TestSearchRepository {
  boolean isConnected();

  boolean isZeebeConnected();

  boolean createIndex(String indexName, Map<String, ?> mapping) throws Exception;

  boolean createOrUpdateDocumentFromObject(String indexName, String docId, Object data)
      throws IOException;

  boolean createOrUpdateDocumentFromObject(
      String indexName, String docId, Object data, String routing) throws IOException;

  String createOrUpdateDocumentFromObject(String indexName, Object data) throws IOException;

  boolean createOrUpdateDocument(String indexName, String docId, Map<String, ?> doc)
      throws IOException;

  boolean createOrUpdateDocument(String name, String id, Map<String, ?> doc, String routing)
      throws IOException;

  String createOrUpdateDocument(String indexName, Map<String, ?> doc) throws IOException;

  void deleteById(String index, String id) throws IOException;

  Set<String> getFieldNames(String indexName) throws IOException;

  boolean hasDynamicMapping(String indexName, DynamicMappingType dynamicMappingType)
      throws IOException;

  List<String> getAliasNames(String indexName) throws IOException;

  <R> List<R> searchAll(String index, Class<R> clazz) throws IOException;

  <R> List<R> searchJoinRelation(String index, String joinRelation, Class<R> clazz, int size)
      throws IOException;

  <A, R> List<R> searchTerm(String index, String field, A value, Class<R> clazz, int size)
      throws IOException;

  List<Long> searchIds(String index, String idFieldName, List<Long> ids, int size)
      throws IOException;

  void deleteByTermsQuery(String index, String fieldName, List<Long> values) throws IOException;

  void update(String index, String id, Map<String, Object> fields) throws IOException;

  List<VariableEntity> getVariablesByProcessInstanceKey(String index, Long processInstanceKey);

  void reindex(String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams)
      throws IOException;

  boolean ilmPolicyExists(String policyName) throws IOException;

  IndexSettings getIndexSettings(String indexName) throws IOException;

  List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids)
      throws IOException;

  List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids)
      throws IOException;

  Optional<List<Long>> getIds(
      String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex)
      throws IOException;

  record IndexSettings(Integer shards, Integer replicas) {}

  enum DynamicMappingType {
    Strict,
    True
  }
}
