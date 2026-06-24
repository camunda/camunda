/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.domain.FormDbModel.FormDbModelBuilder;
import io.camunda.db.rdbms.write.service.FormWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.nio.charset.StandardCharsets;

public class FormExportHandler implements RdbmsExportHandler<Form> {

  private final FormWriter formWriter;

  public FormExportHandler(final FormWriter formWriter) {
    this.formWriter = formWriter;
  }

  @Override
  public boolean canExport(final Record<Form> record) {
    return record.getIntent() instanceof FormIntent;
  }

  @Override
  public void export(final Record<Form> record) {
    if (record.getIntent().equals(FormIntent.CREATED)) {
      formWriter.create(map(record));
    } else if (record.getIntent().equals(FormIntent.DELETED)) {
      formWriter.update(map(record).copy(b -> ((FormDbModelBuilder) b).isDeleted(true)));
    }
  }

  private FormDbModel map(final Record<Form> record) {
    final var value = record.getValue();
    return new FormDbModelBuilder()
        .formKey(value.getFormKey())
        .formId(value.getFormId())
        .tenantId(value.getTenantId())
        .schema(new String(value.getResource(), StandardCharsets.UTF_8))
        .version((long) value.getVersion())
        .isDeleted(false)
        .build();
  }
}
