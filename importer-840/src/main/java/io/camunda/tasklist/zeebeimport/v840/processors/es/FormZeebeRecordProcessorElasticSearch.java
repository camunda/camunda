/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v840.processors.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.v840.record.value.deployment.FormRecordImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.nio.charset.StandardCharsets;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FormZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FormZeebeRecordProcessorElasticSearch.class);

  @Autowired private ObjectMapper objectMapper;

  @Autowired private FormIndex formIndex;

  public void processFormRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {

    final FormRecordImpl recordValue = (FormRecordImpl) record.getValue();

    persistForm(
        recordValue.getFormKey(),
        bytesToXml(recordValue.getResource()),
        (long) recordValue.getVersion(),
        recordValue.getTenantId(),
        false,
        recordValue.getFormId(),
        false,
        bulkRequest);
  }

  private void persistForm(
      Long formKey,
      String schema,
      Long version,
      String tenantId,
      boolean embeeded,
      String formId,
      boolean isDeleted,
      BulkRequest bulkRequest)
      throws PersistenceException {
    final FormEntity formEntity =
        new FormEntity(
            null, formId, schema, version, tenantId, formKey.toString(), embeeded, isDeleted);
    LOGGER.debug("Form: key {}", formKey);
    try {
      bulkRequest.add(
          new IndexRequest()
              .index(formIndex.getFullQualifiedName())
              .id(ConversionUtils.toStringOrNull(formEntity.getId()))
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));

    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert task form [%s]", formEntity.getId()),
          e);
    }
  }

  public static String bytesToXml(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
