/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version25.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

@Slf4j
@RequiredArgsConstructor
public class Version25WriterUtil {

  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);


  public String createCombinedProcessReport(final String name, List<String> childReports) {
    return createCombinedProcessReport(name, childReports, "demo");
  }

  @SneakyThrows
  public String createCombinedProcessReport(final String name,
                                            final List<String> childReports,
                                            final String owner) {
    final String id = IdGenerator.getNextId();
    final String dataJsonString = SchemaUpgradeUtil.readClasspathFileAsString(
      "steps/collection_entities/25-combined-reports-data-fixture");

    final ObjectNode dataNode = (ObjectNode) objectMapper.readTree(dataJsonString);

    final ArrayNode reports = dataNode.putArray("reports");
    for (String reportId : childReports) {
      final ObjectNode reportNode = objectMapper.createObjectNode();
      reportNode.put("id", reportId);
      reportNode.put("color", "#1991c8");
      reports.add(reportNode);
    }

    XContentBuilder contentBuilder = buildReportContent(id, dataNode.toString(), name, owner, true, ReportType.PROCESS);

    IndexRequest indexRequest = new IndexRequest(COMBINED_REPORT_INDEX_NAME, COMBINED_REPORT_INDEX_NAME, id);
    indexRequest.source(contentBuilder);

    performIndexRequest(indexRequest);
    return id;
  }

  private void performIndexRequest(final IndexRequest indexRequest) throws IOException {
    final IndexResponse index = esClient.index(indexRequest, RequestOptions.DEFAULT);
    if (!index.getResult().equals(IndexResponse.Result.CREATED)) {
      throw new UpgradeRuntimeException("Testdata could not be created!");
    }
    esClient.getHighLevelClient().indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
  }

  @SneakyThrows
  public String createSingleDecisionReport(final String name) {

    final String id = IdGenerator.getNextId();

    final String dataJsonString = SchemaUpgradeUtil.readClasspathFileAsString(
      "steps/collection_entities/25-single-decision-report-data-fixture");

    XContentBuilder contentBuilder = buildReportContent(id, dataJsonString, name, false, ReportType.DECISION);

    IndexRequest indexRequest = new IndexRequest(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      SINGLE_DECISION_REPORT_INDEX_NAME,
      id
    );
    indexRequest.source(contentBuilder);

    performIndexRequest(indexRequest);
    return id;
  }

  public String createSingleProcessReport(final String name) {

    return createSingleProcessReport(name, "demo");
  }

  @SneakyThrows
  public String createSingleProcessReport(final String name, final String userId) {

    final String id = IdGenerator.getNextId();
    final String dataJsonString = SchemaUpgradeUtil.readClasspathFileAsString(
      "steps/collection_entities/25-single-process-report-data-fixture");

    XContentBuilder contentBuilder = buildReportContent(id, dataJsonString, name, userId, false, ReportType.PROCESS);

    IndexRequest indexRequest = new IndexRequest(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      id
    );
    indexRequest.source(contentBuilder);

    performIndexRequest(indexRequest);
    return id;
  }

  @SneakyThrows
  public String createDashboard(final String name, List<String> childReports) {
    final String id = IdGenerator.getNextId();
    XContentBuilder contentBuilder = buildDashboardContent(id, name, childReports);

    IndexRequest indexRequest = new IndexRequest(DASHBOARD_INDEX_NAME, DASHBOARD_INDEX_NAME, id);
    indexRequest.source(contentBuilder);

    performIndexRequest(indexRequest);
    return id;
  }

  @SneakyThrows
  public String createCollection(final String name, List<String> entities) {
    final String id = IdGenerator.getNextId();
    XContentBuilder contentBuilder = buildCollectionContent(id, name, entities);

    IndexRequest indexRequest = new IndexRequest(COLLECTION_INDEX_NAME, COLLECTION_INDEX_NAME, id);
    indexRequest.source(contentBuilder);

    performIndexRequest(indexRequest);
    return id;
  }

  private XContentBuilder buildCollectionContent(final String id,
                                                 final String name,
                                                 final List<String> entityIds) throws IOException {

    final String userId = "demo";
    final String now = dateTimeFormatter.format(OffsetDateTime.now());

    XContentBuilder contentBuilder = XContentFactory.jsonBuilder().prettyPrint();

    contentBuilder.startObject()
      .field("id", id)
      .field("owner", userId)
      .field("lastModifier", userId)
      .field("name", name)
      .field("lastModified", now)
      .field("created", now);

    contentBuilder.startObject("data")
      .startObject("configuration").endObject();
    contentBuilder.startArray("entities");
    for (String entityId : entityIds) {
      contentBuilder.value(entityId);
    }
    contentBuilder.endArray()
      .endObject()
      .endObject();
    return contentBuilder;
  }


  private XContentBuilder buildDashboardContent(final String id,
                                                final String name,
                                                final List<String> reportIds) throws IOException {


    final String userId = "demo";
    final String now = dateTimeFormatter.format(OffsetDateTime.now());

    XContentBuilder contentBuilder = XContentFactory.jsonBuilder().prettyPrint();

    contentBuilder.startObject()
      .field("id", id)
      .field("owner", userId)
      .field("lastModifier", userId)
      .field("name", name)
      .field("lastModified", now)
      .field("created", now);
    contentBuilder.startArray("reports");
    for (String reportId : reportIds) {
      contentBuilder.startObject()
        .field("id", reportId)
        .field("configuration").nullValue()
        .startObject("position").field("x", 0).field("y", 0).endObject()
        .startObject("dimensions").field("width", 1).field("height", 1).endObject()
        .endObject();
    }
    contentBuilder.endArray();
    contentBuilder.endObject();
    return contentBuilder;
  }

  private XContentBuilder buildReportContent(final String id,
                                             final String dataJsonString,
                                             final String name,
                                             final boolean combined,
                                             final ReportType reportType) throws IOException {

    return buildReportContent(id, dataJsonString, name, "demo", combined, reportType);
  }


  private XContentBuilder buildReportContent(final String id,
                                             final String dataJsonString,
                                             final String name,
                                             final String userId,
                                             final boolean combined,
                                             final ReportType reportType) throws IOException {

    final String now = dateTimeFormatter.format(OffsetDateTime.now());

    XContentBuilder contentBuilder = XContentFactory.jsonBuilder().prettyPrint();
    try (XContentParser contentParser = XContentFactory.xContent(XContentType.JSON)
      .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, dataJsonString)) {

      contentBuilder.startObject();
      contentBuilder.field("id", id);
      contentBuilder.field("owner", userId);
      contentBuilder.field("lastModifier", userId);
      contentBuilder.field("name", name);
      contentBuilder.field("lastModified", now);
      contentBuilder.field("created", now);
      contentBuilder.field("combined", combined);
      contentBuilder.field("reportType", reportType.getId());

      // fixture data part
      contentBuilder.field("data");
      contentBuilder.copyCurrentStructure(contentParser);
      contentBuilder.endObject();
    }
    return contentBuilder;
  }
}
