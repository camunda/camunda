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
import io.camunda.auth.domain.model.AuthRole;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.RoleWritePort;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link RoleWritePort}. */
public class ElasticsearchRoleWriteAdapter implements RoleWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRoleWriteAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-role";
  private static final String DEFAULT_MEMBER_INDEX_NAME = "camunda-auth-role-member";

  private final ElasticsearchClient client;
  private final String indexName;
  private final String memberIndexName;

  public ElasticsearchRoleWriteAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME, DEFAULT_MEMBER_INDEX_NAME);
  }

  public ElasticsearchRoleWriteAdapter(
      final ElasticsearchClient client,
      final String indexName,
      final String memberIndexName) {
    this.client = client;
    this.indexName = indexName;
    this.memberIndexName = memberIndexName;
  }

  @Override
  public void save(final AuthRole role) {
    LOG.debug("Indexing role with roleId={} into index={}", role.roleId(), indexName);
    try {
      client.index(
          request -> request.index(indexName).id(role.roleId()).document(role));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to index role with roleId=" + role.roleId(), e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error indexing role with roleId=" + role.roleId(), e);
    }
  }

  @Override
  public void deleteById(final String roleId) {
    LOG.debug("Deleting role by roleId={} from index={}", roleId, indexName);
    try {
      client.delete(request -> request.index(indexName).id(roleId));
    } catch (final ElasticsearchException e) {
      LOG.warn("Failed to delete role with roleId={}: {}", roleId, e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error deleting role with roleId=" + roleId + " from index=" + indexName, e);
    }
  }

  @Override
  public void addMember(
      final String roleId, final String memberId, final MemberType memberType) {
    final String docId = roleId + "_" + memberId;
    LOG.debug(
        "Adding member memberId={} memberType={} to role roleId={} in index={}",
        memberId,
        memberType,
        roleId,
        memberIndexName);
    try {
      final Map<String, String> document =
          Map.of("roleId", roleId, "memberId", memberId, "memberType", memberType.name());
      client.index(
          request -> request.index(memberIndexName).id(docId).document(document));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to add member memberId=" + memberId + " to role roleId=" + roleId, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error adding member memberId=" + memberId + " to role roleId=" + roleId, e);
    }
  }

  @Override
  public void removeMember(
      final String roleId, final String memberId, final MemberType memberType) {
    final String docId = roleId + "_" + memberId;
    LOG.debug(
        "Removing member memberId={} from role roleId={} in index={}",
        memberId,
        roleId,
        memberIndexName);
    try {
      client.delete(request -> request.index(memberIndexName).id(docId));
    } catch (final ElasticsearchException e) {
      LOG.warn(
          "Failed to remove member memberId={} from role roleId={}: {}",
          memberId,
          roleId,
          e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error removing member memberId=" + memberId + " from role roleId=" + roleId, e);
    }
  }
}
