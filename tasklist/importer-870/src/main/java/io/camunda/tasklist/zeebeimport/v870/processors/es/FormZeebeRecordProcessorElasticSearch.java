/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.v870.record.value.deployment.FormRecordImpl;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class FormZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FormZeebeRecordProcessorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private FormIndex formIndex;

  public void processFormRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {

    final FormRecordImpl recordValue = (FormRecordImpl) record.getValue();

    if (record.getIntent().name().equals(FormIntent.CREATED.name())) {
      persistForm(
          recordValue.getFormKey(),
          bytesToXml(recordValue.getResource()),
          (long) recordValue.getVersion(),
          recordValue.getTenantId(),
          recordValue.getFormId(),
          false,
          bulkRequest);
    } else if (record.getIntent().name().equals(FormIntent.DELETED.name())) {
      persistForm(
          recordValue.getFormKey(),
          bytesToXml(recordValue.getResource()),
          (long) recordValue.getVersion(),
          recordValue.getTenantId(),
          recordValue.getFormId(),
          true,
          bulkRequest);
    } else {
      LOGGER.info("Form intent {} not supported", record.getIntent().name());
    }
  }

  private void persistForm(
      final Long formKey,
      final String schema,
      final Long version,
      final String tenantId,
      final String formId,
      final boolean isDelete,
      final BulkRequest bulkRequest)
      throws PersistenceException {
    final FormEntity formEntity =
        new FormEntity()
            .setId(String.valueOf(formKey))
            .setKey(formKey)
            .setFormId(formId)
            .setSchema(schema)
            .setVersion(version)
            .setTenantId(tenantId)
            .setEmbedded(false)
            .setIsDeleted(isDelete);
    try {
      if (isDelete) {
        // Delete operation
        bulkRequest.add(
            new UpdateRequest()
                .index(formIndex.getFullQualifiedName())
                .id(formEntity.getId())
                .upsert(objectMapper.writeValueAsString(formEntity), XContentType.JSON)
                .doc(Map.of(FormIndex.IS_DELETED, true))
                .retryOnConflict(UPDATE_RETRY_COUNT));
      } else {
        // Create operation
        bulkRequest.add(
            new IndexRequest()
                .index(formIndex.getFullQualifiedName())
                .id(ConversionUtils.toStringOrNull(formEntity.getId()))
                .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));
      }
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the form query for the formId: [%s]", formEntity.getId()),
          e);
    }
  }

  public static String bytesToXml(final byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
