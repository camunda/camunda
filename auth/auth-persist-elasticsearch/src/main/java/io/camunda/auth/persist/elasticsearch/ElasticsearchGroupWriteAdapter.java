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
import io.camunda.auth.domain.model.AuthGroup;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.GroupWritePort;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link GroupWritePort}. */
public class ElasticsearchGroupWriteAdapter implements GroupWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchGroupWriteAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-group";
  private static final String DEFAULT_MEMBER_INDEX_NAME = "camunda-auth-group-member";

  private final ElasticsearchClient client;
  private final String indexName;
  private final String memberIndexName;

  public ElasticsearchGroupWriteAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME, DEFAULT_MEMBER_INDEX_NAME);
  }

  public ElasticsearchGroupWriteAdapter(
      final ElasticsearchClient client, final String indexName, final String memberIndexName) {
    this.client = client;
    this.indexName = indexName;
    this.memberIndexName = memberIndexName;
  }

  @Override
  public void save(final AuthGroup group) {
    LOG.debug("Indexing group with groupId={} into index={}", group.groupId(), indexName);
    try {
      client.index(request -> request.index(indexName).id(group.groupId()).document(group));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to index group with groupId=" + group.groupId(), e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error indexing group with groupId=" + group.groupId(), e);
    }
  }

  @Override
  public void deleteById(final String groupId) {
    LOG.debug("Deleting group by groupId={} from index={}", groupId, indexName);
    try {
      client.delete(request -> request.index(indexName).id(groupId));
    } catch (final ElasticsearchException e) {
      LOG.warn("Failed to delete group with groupId={}: {}", groupId, e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error deleting group with groupId=" + groupId + " from index=" + indexName, e);
    }
  }

  @Override
  public void addMember(final String groupId, final String memberId, final MemberType memberType) {
    final String docId = groupId + "_" + memberId;
    LOG.debug(
        "Adding member memberId={} memberType={} to group groupId={} in index={}",
        memberId,
        memberType,
        groupId,
        memberIndexName);
    try {
      final Map<String, String> document =
          Map.of("groupId", groupId, "memberId", memberId, "memberType", memberType.name());
      client.index(request -> request.index(memberIndexName).id(docId).document(document));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to add member memberId=" + memberId + " to group groupId=" + groupId, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error adding member memberId=" + memberId + " to group groupId=" + groupId, e);
    }
  }

  @Override
  public void removeMember(
      final String groupId, final String memberId, final MemberType memberType) {
    final String docId = groupId + "_" + memberId;
    LOG.debug(
        "Removing member memberId={} from group groupId={} in index={}",
        memberId,
        groupId,
        memberIndexName);
    try {
      client.delete(request -> request.index(memberIndexName).id(docId));
    } catch (final ElasticsearchException e) {
      LOG.warn(
          "Failed to remove member memberId={} from group groupId={}: {}",
          memberId,
          groupId,
          e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error removing member memberId=" + memberId + " from group groupId=" + groupId, e);
    }
  }
}
