/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.REPORT_SHARE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.writer.SharingWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class SharingWriterES implements SharingWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SharingWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public SharingWriterES(
      final OptimizeElasticsearchClient esClient,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public ReportShareRestDto saveReportShare(final ReportShareRestDto createSharingDto) {
    LOG.debug("Writing new report share to Elasticsearch");
    final String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, REPORT_SHARE_INDEX_NAME)
                          .id(id)
                          .document(createSharingDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)) {
        final String message =
            "Could not write report share to Elasticsearch. "
                + "Maybe the connection to Elasticsearch got lost?";
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "Could not create report share.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    LOG.debug(
        "report share with id [{}] for resource [{}] has been created",
        id,
        createSharingDto.getReportId());
    return createSharingDto;
  }

  @Override
  public DashboardShareRestDto saveDashboardShare(final DashboardShareRestDto createSharingDto) {
    LOG.debug("Writing new dashboard share to Elasticsearch");
    final String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, DASHBOARD_SHARE_INDEX_NAME)
                          .id(id)
                          .document(createSharingDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)) {
        final String message =
            "Could not write dashboard share to Elasticsearch. "
                + "Maybe the connection to Elasticsearch got lost?";
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "Could not create dashboard share.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    LOG.debug(
        "dashboard share with id [{}] for resource [{}] has been created",
        id,
        createSharingDto.getDashboardId());
    return createSharingDto;
  }

  @Override
  public void updateDashboardShare(final DashboardShareRestDto updatedShare) {
    final String id = updatedShare.getId();
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, DASHBOARD_SHARE_INDEX_NAME)
                          .id(id)
                          .document(updatedShare)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)
          && !indexResponse.result().equals(Result.Updated)) {
        final String message = "Could not write dashboard share to Elasticsearch.";
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update dashboard share with id [%s] for resource [%s].",
              id, updatedShare.getDashboardId());
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    LOG.debug(
        "dashboard share with id [{}] for resource [{}] has been updated",
        id,
        updatedShare.getDashboardId());
  }

  @Override
  public void deleteReportShare(final String shareId) {
    LOG.debug("Deleting report share with id [{}]", shareId);
    final DeleteResponse deleteResponse;
    try {
      deleteResponse =
          esClient.delete(
              OptimizeDeleteRequestBuilderES.of(
                  d ->
                      d.optimizeIndex(esClient, REPORT_SHARE_INDEX_NAME)
                          .id(shareId)
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      final String reason = String.format("Could not delete report share with id [%s].", shareId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete report share with id [%s]. Report share does not exist. "
                  + "Maybe it was already deleted by someone else?",
              shareId);
      LOG.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteDashboardShare(final String shareId) {
    LOG.debug("Deleting dashboard share with id [{}]", shareId);
    final DeleteResponse deleteResponse;
    try {
      deleteResponse =
          esClient.delete(
              OptimizeDeleteRequestBuilderES.of(
                  d ->
                      d.optimizeIndex(esClient, DASHBOARD_SHARE_INDEX_NAME)
                          .id(shareId)
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      final String reason =
          String.format("Could not delete dashboard share with id [%s].", shareId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete dashboard share with id [%s]. Dashboard share does not exist. "
                  + "Maybe it was already deleted by someone else?",
              shareId);
      LOG.error(message);
      throw new NotFoundException(message);
    }
  }
}
