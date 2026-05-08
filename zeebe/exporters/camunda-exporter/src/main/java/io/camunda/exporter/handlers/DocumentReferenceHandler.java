/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.DocumentReferenceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentReferenceHandler
    implements ExportHandler<DocumentReferenceEntity, VariableRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentReferenceHandler.class);

  // The discriminator field is injected by the REST layer when a client uploads a document.
  // Its presence as a top-level key (or as a key in every element of a top-level array) is the
  // contract that identifies a variable as a document reference.  The value is always "camunda"
  // for first-party document references; future third-party store adapters may use a different
  // value, but the key itself is stable.
  private static final String DOCUMENT_TYPE_DISCRIMINATOR = "camunda.document.type";

  // MIGRATED intent carries the same value as CREATED/UPDATED but signals a process-instance
  // migration event.  The variable content doesn't change, so re-indexing it would just produce
  // a duplicate write with no new information.  We filter it out here to avoid that overhead.
  private static final Set<VariableIntent> SUPPORTED_INTENTS =
      Set.of(VariableIntent.CREATED, VariableIntent.UPDATED);

  private final String indexName;
  private final ObjectMapper objectMapper;

  // Single-record cache: the exporter processes records strictly sequentially on one thread.
  // handlesRecord(), generateIds(), and updateEntity() are all called for the same record in
  // order — handlesRecord first, then generateIds, then updateEntity once per id.  Caching the
  // parsed JsonNode list here avoids re-deserializing the same (potentially large) JSON string
  // up to (2 + N) times for an N-document array variable.
  private Long cachedRecordKey = null;
  private List<JsonNode> cachedDocRefs = Collections.emptyList();

  public DocumentReferenceHandler(final String indexName, final ObjectMapper objectMapper) {
    this.indexName = indexName;
    this.objectMapper = objectMapper;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<DocumentReferenceEntity> getEntityType() {
    return DocumentReferenceEntity.class;
  }

  // Note: this method mutates the per-instance cache fields (cachedRecordKey, cachedDocRefs) so
  // the parse result can be reused by generateIds() and updateEntity() during the same lifecycle
  // pass.  Do not call it speculatively expecting predicate purity.
  @Override
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    if (!SUPPORTED_INTENTS.contains(record.getIntent())) {
      return false;
    }
    final String value = record.getValue().getValue();
    // Fast-path: most variables are not document references, and many can be large JSON objects
    // or arrays.  A substring scan is O(n) but avoids any allocation; if the discriminator is
    // absent we can discard the record without touching the JSON parser at all.
    if (!value.contains(DOCUMENT_TYPE_DISCRIMINATOR)) {
      return false;
    }
    final List<JsonNode> docRefs = parseDocumentRefs(value);
    if (docRefs.isEmpty()) {
      return false;
    }
    // Populate the cache so generateIds() and updateEntity() can reuse this parse result.
    cachedRecordKey = record.getKey();
    cachedDocRefs = docRefs;
    return true;
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    final List<String> ids = new ArrayList<>();
    for (final JsonNode node : getDocRefs(record)) {
      if (node.has("documentId")) {
        ids.add(generateId(record.getKey(), node.get("documentId").asText()));
      }
    }
    return ids;
  }

  @Override
  public DocumentReferenceEntity createNewEntity(final String id) {
    return new DocumentReferenceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final DocumentReferenceEntity entity) {
    final var recordValue = record.getValue();
    entity
        .setVariableKey(record.getKey())
        .setVariableName(recordValue.getName())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setProcessDefinitionId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPosition(record.getPosition())
        .setPartitionId(record.getPartitionId());

    final var rootKey = recordValue.getRootProcessInstanceKey();
    if (rootKey > 0) {
      entity.setRootProcessInstanceKey(rootKey);
    }

    // The entity id encodes which document to populate when the variable holds an array.
    // Format is "{variableKey}_{documentId}" — we strip the prefix to recover the documentId.
    final String docId = extractDocumentId(entity.getId(), record.getKey());

    JsonNode docNode = null;
    for (final JsonNode node : getDocRefs(record)) {
      if (node.has("documentId") && docId.equals(node.get("documentId").asText())) {
        docNode = node;
        break;
      }
    }

    if (docNode != null) {
      entity.setDocumentId(docNode.get("documentId").asText());
      entity.setStoreId(getTextOrNull(docNode, "storeId"));
      entity.setContentHash(getTextOrNull(docNode, "contentHash"));
      final JsonNode metadata = docNode.get("metadata");
      if (metadata != null) {
        entity.setFileName(getTextOrNull(metadata, "fileName"));
        entity.setContentType(getTextOrNull(metadata, "contentType"));
        entity.setExpiresAt(getTextOrNull(metadata, "expiresAt"));
        if (metadata.has("size") && !metadata.get("size").isNull()) {
          entity.setSize(metadata.get("size").asLong());
        }
        if (metadata.has("customProperties") && !metadata.get("customProperties").isNull()) {
          entity.setCustomProperties(metadata.get("customProperties").toString());
        }
      }
    } else {
      LOG.warn(
          "Document reference with id='{}' not found in variable record key={}",
          docId,
          record.getKey());
    }
  }

  @Override
  public void flush(final DocumentReferenceEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  // Returns the cached parsed list if the key matches, otherwise re-parses and warms the cache.
  // The re-parse path exists as a safety net: in practice handlesRecord() is always called first
  // for every record the handler processes, so the cache will be warm.  Updating the cache on
  // miss ensures any subsequent calls in the same lifecycle pass (e.g. updateEntity per array
  // element) reuse the same parse result.
  private List<JsonNode> getDocRefs(final Record<VariableRecordValue> record) {
    if (cachedRecordKey != null && cachedRecordKey == record.getKey()) {
      return cachedDocRefs;
    }
    final List<JsonNode> parsed = parseDocumentRefs(record.getValue().getValue());
    cachedRecordKey = record.getKey();
    cachedDocRefs = parsed;
    return parsed;
  }

  private List<JsonNode> parseDocumentRefs(final String value) {
    try {
      final JsonNode root = objectMapper.readTree(value);
      if (root.isArray()) {
        final List<JsonNode> refs = new ArrayList<>();
        for (final JsonNode node : root) {
          if (isDocumentReferenceNode(node)) {
            refs.add(node);
          }
        }
        return refs;
      } else if (isDocumentReferenceNode(root)) {
        return List.of(root);
      }
    } catch (final Exception e) {
      LOG.debug(
          "Failed to parse document reference from variable record value: {}", e.getMessage());
    }
    return Collections.emptyList();
  }

  private boolean isDocumentReferenceNode(final JsonNode node) {
    return node.isObject() && node.has(DOCUMENT_TYPE_DISCRIMINATOR);
  }

  private String generateId(final long variableKey, final String documentId) {
    return variableKey + "_" + documentId;
  }

  private String extractDocumentId(final String entityId, final long variableKey) {
    final String prefix = variableKey + "_";
    return entityId.startsWith(prefix) ? entityId.substring(prefix.length()) : entityId;
  }

  private String getTextOrNull(final JsonNode node, final String field) {
    final JsonNode f = node.get(field);
    return (f != null && !f.isNull()) ? f.asText() : null;
  }
}
