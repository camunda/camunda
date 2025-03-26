/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.os;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v870.record.value.deployment.FormRecordImpl;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FormZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FormZeebeRecordProcessorOpenSearch.class);

  @Autowired private FormIndex formIndex;

  public void processFormRecord(final Record record, final List<BulkOperation> operations)
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
          operations);
    } else if (record.getIntent().name().equals(FormIntent.DELETED.name())) {
      persistForm(
          recordValue.getFormKey(),
          bytesToXml(recordValue.getResource()),
          (long) recordValue.getVersion(),
          recordValue.getTenantId(),
          recordValue.getFormId(),
          true,
          operations);
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
      final List<BulkOperation> operations)
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
        final BulkOperation bulkOperation =
            new BulkOperation.Builder()
                .update(
                    up ->
                        up.index(formIndex.getFullQualifiedName())
                            .id(formEntity.getId())
                            .document(CommonUtils.getJsonObjectFromEntity(formEntity))
                            .docAsUpsert(true)
                            .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
                .build();

        operations.add(bulkOperation);
      } else {
        // Create operation
        operations.add(
            new BulkOperation.Builder()
                .index(
                    IndexOperation.of(
                        io ->
                            io.index(formIndex.getFullQualifiedName())
                                .id(ConversionUtils.toStringOrNull(formEntity.getId()))
                                .document(CommonUtils.getJsonObjectFromEntity(formEntity))))
                .build());
      }
    } catch (final Exception e) {
      throw new PersistenceException(
          String.format("Error preparing the form query for the formId: [%s]", formEntity.getId()),
          e);
    }
  }

  public static String bytesToXml(final byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
