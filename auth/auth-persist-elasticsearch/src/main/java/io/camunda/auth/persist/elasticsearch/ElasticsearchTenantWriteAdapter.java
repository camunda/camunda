/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.auth.domain.model.AuthTenant;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.TenantWritePort;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link TenantWritePort}. */
public class ElasticsearchTenantWriteAdapter implements TenantWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchTenantWriteAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-tenant";
  private static final String DEFAULT_MEMBER_INDEX_NAME = "camunda-auth-tenant-member";

  private final ElasticsearchClient client;
  private final String indexName;
  private final String memberIndexName;

  public ElasticsearchTenantWriteAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME, DEFAULT_MEMBER_INDEX_NAME);
  }

  public ElasticsearchTenantWriteAdapter(
      final ElasticsearchClient client, final String indexName, final String memberIndexName) {
    this.client = client;
    this.indexName = indexName;
    this.memberIndexName = memberIndexName;
  }

  @Override
  public void save(final AuthTenant tenant) {
    LOG.debug("Indexing tenant with tenantId={} into index={}", tenant.tenantId(), indexName);
    try {
      client.index(request -> request.index(indexName).id(tenant.tenantId()).document(tenant));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to index tenant with tenantId=" + tenant.tenantId(), e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error indexing tenant with tenantId=" + tenant.tenantId(), e);
    }
  }

  @Override
  public void deleteById(final String tenantId) {
    LOG.debug("Deleting tenant by tenantId={} from index={}", tenantId, indexName);
    try {
      client.delete(request -> request.index(indexName).id(tenantId));
    } catch (final ElasticsearchException e) {
      LOG.warn("Failed to delete tenant with tenantId={}: {}", tenantId, e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error deleting tenant with tenantId=" + tenantId + " from index=" + indexName, e);
    }
  }

  @Override
  public void addMember(final String tenantId, final String memberId, final MemberType memberType) {
    final String docId = tenantId + "_" + memberId;
    LOG.debug(
        "Adding member memberId={} memberType={} to tenant tenantId={} in index={}",
        memberId,
        memberType,
        tenantId,
        memberIndexName);
    try {
      final Map<String, String> document =
          Map.of("tenantId", tenantId, "memberId", memberId, "memberType", memberType.name());
      client.index(request -> request.index(memberIndexName).id(docId).document(document));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to add member memberId=" + memberId + " to tenant tenantId=" + tenantId, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error adding member memberId=" + memberId + " to tenant tenantId=" + tenantId, e);
    }
  }

  @Override
  public void removeMember(
      final String tenantId, final String memberId, final MemberType memberType) {
    final String docId = tenantId + "_" + memberId;
    LOG.debug(
        "Removing member memberId={} from tenant tenantId={} in index={}",
        memberId,
        tenantId,
        memberIndexName);
    try {
      client.delete(request -> request.index(memberIndexName).id(docId));
    } catch (final ElasticsearchException e) {
      LOG.warn(
          "Failed to remove member memberId={} from tenant tenantId={}: {}",
          memberId,
          tenantId,
          e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error removing member memberId=" + memberId + " from tenant tenantId=" + tenantId,
          e);
    }
  }
}
