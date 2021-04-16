/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.Request
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.indices.GetIndexTemplatesRequest
import org.elasticsearch.client.indices.GetMappingsRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.repositories.fs.FsRepository
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.nested.Nested
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ElasticClient {
  private static final Logger log = LoggerFactory.getLogger(OptimizeWrapper.class);
  private static final String[] SETTINGS_FILTER = [
    "index.analysis.*", "index.number_of_shards", "index.number_of_replicas",
    "index.max_ngram_diff", "index.mapping.*", "index.refresh_interval"
  ]
  public static final String SNAPSHOT_REPOSITORY_NAME = "my_backup"
  public static final String SNAPSHOT_NAME = "snapshot_1"
  public static final String OPTIMIZE_INDEX_PREFIX = "optimize"
  // include all Optimize indices except the update log index
  public static final String DEFAULT_OPTIMIZE_INDEX_PATTERN =
    "${OPTIMIZE_INDEX_PREFIX}-*,-${OPTIMIZE_INDEX_PREFIX}-${UpdateLogEntryIndex.INDEX_NAME}*"

  String name
  RestHighLevelClient client;

  ElasticClient(String name, int port = 9200, String host = "localhost") {
    this.name = name
    this.client = new RestHighLevelClient(
      RestClient.builder(
        new HttpHost(host, port, "http"))
        .setRequestConfigCallback(
          new RestClientBuilder.RequestConfigCallback() {
            @Override
            RequestConfig.Builder customizeRequestConfig(
              RequestConfig.Builder requestConfigBuilder) {
              return requestConfigBuilder
                .setConnectTimeout(5000)
              // some requests like creating snapshots might take a while and we do them blocking
                .setSocketTimeout(0);
            }
          })
    )
  }

  def close() {
    this.client.close()
  }

  def refreshAll() {
    log.info("Refreshing all indices of ${name} Elasticsearch...");
    client.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT)
    log.info("Successfully refreshed all indices of ${name} Elasticsearch!");
  }

  def cleanIndicesAndTemplates() {
    log.info("Wiping all indices & templates from ${name} Elasticsearch...");
    client.indices().delete(new DeleteIndexRequest("_all"), RequestOptions.DEFAULT)
    client.indices().deleteTemplate(new DeleteIndexTemplateRequest("*"), RequestOptions.DEFAULT)
    log.info("Successfully wiped all indices & templates from ${name} Elasticsearch!");
  }

  def getSettings(String indexPattern = DEFAULT_OPTIMIZE_INDEX_PATTERN) {
    return client.indices().getSettings(
      new GetSettingsRequest().indices(indexPattern).names(SETTINGS_FILTER), RequestOptions.DEFAULT
    ).getIndexToSettings()
  }

  def getMappings(String indexPattern = DEFAULT_OPTIMIZE_INDEX_PATTERN) {
    return client.indices().getMapping(new GetMappingsRequest().indices(indexPattern), RequestOptions.DEFAULT)
      .mappings()
  }

  def getAliases(String indexPattern = DEFAULT_OPTIMIZE_INDEX_PATTERN) {
    return client.indices().getAlias(new GetAliasesRequest().indices(indexPattern), RequestOptions.DEFAULT)
      .getAliases()
  }

  def getTemplates(String templatesPattern = DEFAULT_OPTIMIZE_INDEX_PATTERN) {
    return client.indices().getIndexTemplate(new GetIndexTemplatesRequest(templatesPattern), RequestOptions.DEFAULT)
      .getIndexTemplates()
  }

  def createSnapshotRepository() {
    log.info("Creating snapshot repository on ${name} Elasticsearch...");
    def settings = Settings.builder()
      .put(FsRepository.LOCATION_SETTING.getKey(), "/var/tmp")
      .put(FsRepository.COMPRESS_SETTING.getKey(), true)
      .build()
    client.snapshot().createRepository(
      new PutRepositoryRequest(SNAPSHOT_REPOSITORY_NAME).settings(settings).type(FsRepository.TYPE),
      RequestOptions.DEFAULT
    )
    log.info("Done creating snapshot repository on ${name} Elasticsearch!");
  }

  def createSnapshot(Boolean waitForCompletion = true) {
    log.info("Creating snapshot on ${name} Elasticsearch...");
    // using low level client for compatibility here, see https://github.com/elastic/elasticsearch/pull/57661
    final Request createSnapshotRequest = new Request(
      "PUT", "/_snapshot/" + SNAPSHOT_REPOSITORY_NAME + "/" + SNAPSHOT_NAME
    );
    createSnapshotRequest.addParameter("wait_for_completion", String.valueOf(waitForCompletion));
    createSnapshotRequest.setJsonEntity("{\"include_global_state\":true}");
    final Response response = client.getLowLevelClient().performRequest(createSnapshotRequest);
    if (!HttpURLConnection.HTTP_OK.equals(response.getStatusLine().getStatusCode())) {
      throw new RuntimeException("Failed Creating Snapshot, statusCode: ${response.getStatusLine().getStatusCode()}")
    }
    if (waitForCompletion) {
      log.info("Done creating snapshot on ${name} Elasticsearch!");
    } else {
      log.info("Done starting asynchronous snapshot operation on ${name} Elasticsearch!");
    }
  }

  def restoreSnapshot() {
    log.info("Restoring snapshot on ${name} Elasticsearch...");
    client.snapshot().restore(
      new RestoreSnapshotRequest(SNAPSHOT_REPOSITORY_NAME, SNAPSHOT_NAME)
        .includeGlobalState(true)
        .waitForCompletion(true),
      RequestOptions.DEFAULT
    )
    log.info("Done restoring snapshot on ${name} Elasticsearch!");
  }

  def deleteSnapshot() {
    log.info("Deleting snapshot on ${name} Elasticsearch...");
    client.snapshot().delete(new DeleteSnapshotRequest(SNAPSHOT_REPOSITORY_NAME, SNAPSHOT_NAME), RequestOptions.DEFAULT)
    log.info("Done deleting snapshot on ${name} Elasticsearch!");
  }

  long getDocumentCount(String indexName) {
    client.count(new CountRequest(prefixIndexName(indexName)), RequestOptions.DEFAULT).getCount()
  }

  long getNestedDocumentCount(String indexName, String nestedPath) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .aggregation(AggregationBuilders.nested(nestedPath, nestedPath))
    def searchResponse = client.search(
      new SearchRequest().indices(prefixIndexName(indexName)).source(searchSourceBuilder),
      RequestOptions.DEFAULT
    )
    Nested aggregation = (Nested) searchResponse.getAggregations()[0]
    return aggregation.getDocCount()
  }

  private static String prefixIndexName(String indexName) {
    "${OPTIMIZE_INDEX_PREFIX}-$indexName".toString()
  }

}
