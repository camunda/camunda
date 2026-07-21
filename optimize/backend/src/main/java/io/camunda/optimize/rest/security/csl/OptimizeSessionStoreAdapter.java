/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.service.db.DatabaseConstants.WEB_SESSION_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * SPIKE (ADR-0036): Elasticsearch-backed {@link SessionStorePort} for Optimize. This is what CSL's
 * stateful webapp chain persists server-side sessions through, replacing the stateless cookie. It
 * writes to the {@code web-session} index (see {@code WebSessionIndex}) and mirrors the
 * terminated-user-session store's use of {@link OptimizeElasticsearchClient}.
 *
 * <p>Active only under Elasticsearch and when {@code optimize.security.csl.enabled=true}. CSL only
 * calls it when {@code camunda.security.session.persistent.enabled=true}; otherwise CSL uses an
 * in-memory session. An OpenSearch equivalent (mirroring the ES/OS reader/writer split) is a
 * follow-up; this spike is ES-only.
 */
@Component
@Conditional(ElasticSearchCondition.class)
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
public class OptimizeSessionStoreAdapter implements SessionStorePort {

  // Page size for the getAll scroll; retrieveAllScrollResults pages through the rest.
  private static final int SCROLL_PAGE_SIZE = 1000;

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public OptimizeSessionStoreAdapter(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public PersistentSession get(final String sessionId) {
    try {
      final var response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME).id(sessionId)),
              WebSessionDto.class);
      return response.found() ? toPersistentSession(response.source()) : null;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read web session " + sessionId, e);
    }
  }

  @Override
  public void upsert(final PersistentSession session) {
    final WebSessionDto dto = toDto(session);
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME)
                      .id(session.id())
                      .refresh(Refresh.True)
                      .document(dto)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not persist web session " + session.id(), e);
    }
  }

  @Override
  public void delete(final String sessionId) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d -> d.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME).id(sessionId)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not delete web session " + sessionId, e);
    }
  }

  @Override
  public List<PersistentSession> getAll() {
    final int scrollTimeout =
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME)
                    .size(SCROLL_PAGE_SIZE)
                    .query(q -> q.matchAll(MatchAllQuery.of(m -> m)))
                    .scroll(Time.of(t -> t.time(scrollTimeout + "s"))));
    final SearchResponse<WebSessionDto> response;
    try {
      response = esClient.search(searchRequest, WebSessionDto.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read web sessions", e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
            response, WebSessionDto.class, objectMapper, esClient, scrollTimeout)
        .stream()
        .map(this::toPersistentSession)
        .toList();
  }

  private PersistentSession toPersistentSession(final WebSessionDto dto) {
    return new PersistentSession(
        dto.getId(),
        dto.getCreationTime(),
        dto.getLastAccessedTime(),
        dto.getMaxInactiveIntervalInSeconds(),
        dto.getAttributes() != null ? dto.getAttributes() : Map.of());
  }

  private WebSessionDto toDto(final PersistentSession session) {
    return new WebSessionDto(
        session.id(),
        session.creationTime(),
        session.lastAccessedTime(),
        session.maxInactiveIntervalInSeconds(),
        session.attributes());
  }
}
