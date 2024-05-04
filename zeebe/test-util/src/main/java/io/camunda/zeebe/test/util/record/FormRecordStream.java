/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.util.stream.Stream;

public class FormRecordStream extends ExporterRecordStream<Form, FormRecordStream> {

  public FormRecordStream(final Stream<Record<Form>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected FormRecordStream supply(final Stream<Record<Form>> wrappedStream) {
    return new FormRecordStream(wrappedStream);
  }

  public FormRecordStream withFormId(final String formId) {
    return valueFilter(v -> v.getFormId().equals(formId));
  }

  public FormRecordStream withFormKey(final long formKey) {
    return valueFilter(v -> v.getFormKey() == formKey);
  }

  public FormRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }
}
