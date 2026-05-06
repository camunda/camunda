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

  @Override
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    if (!SUPPORTED_INTENTS.contains(record.getIntent())) {
      return false;
    }
    return isDocumentReference(record.getValue().getValue());
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    final List<String> ids = new ArrayList<>();
    try {
      final JsonNode root = objectMapper.readTree(record.getValue().getValue());
      if (root.isArray()) {
        for (final JsonNode node : root) {
          if (isDocumentReferenceNode(node) && node.has("documentId")) {
            ids.add(generateId(record.getKey(), node.get("documentId").asText()));
          }
        }
      } else if (isDocumentReferenceNode(root) && root.has("documentId")) {
        ids.add(generateId(record.getKey(), root.get("documentId").asText()));
      }
    } catch (final Exception e) {
      LOG.debug(
          "Failed to parse document reference ids from variable record key={}: {}",
          record.getKey(),
          e.getMessage());
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

    try {
      final JsonNode root = objectMapper.readTree(recordValue.getValue());
      JsonNode docNode = null;
      if (root.isArray()) {
        for (final JsonNode node : root) {
          if (isDocumentReferenceNode(node)
              && node.has("documentId")
              && docId.equals(node.get("documentId").asText())) {
            docNode = node;
            break;
          }
        }
      } else if (isDocumentReferenceNode(root)) {
        docNode = root;
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
    } catch (final Exception e) {
      LOG.error(
          "Failed to parse document reference data from variable record key={}: {}",
          record.getKey(),
          e.getMessage());
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

  private boolean isDocumentReference(final String value) {
    try {
      final JsonNode root = objectMapper.readTree(value);
      if (root.isArray()) {
        return root.size() > 0 && isDocumentReferenceNode(root.get(0));
      }
      return isDocumentReferenceNode(root);
    } catch (final Exception e) {
      return false;
    }
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
