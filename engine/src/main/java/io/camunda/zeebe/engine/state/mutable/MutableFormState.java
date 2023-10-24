/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;

public interface MutableFormState extends FormState {

  /**
   * Put the given form in FORMS column
   *
   * @param record the record of the form
   */
  void storeFormInFormColumn(FormRecord record);

  /**
   * Put the given form in FORM_BY_ID_AND_VERSION column
   *
   * @param record the record of the form
   */
  void storeFormInFormByIdAndVersionColumn(FormRecord record);

  /**
   * Update the latest version of the form if it is newer.
   *
   * @param record the record of the form
   */
  void updateLatestVersion(FormRecord record);

  /**
   * Put the given form into the cache
   *
   * @param record the record of the form
   */
  void updateLatestFormCache(FormRecord record);

  /**
   * Deletes a form from FORMS column family
   *
   * @param record the record of the form that is deleted
   */
  void deleteFormInFormsColumn(FormRecord record);

  /**
   * Deletes a form from FORM_BY_ID_AND_VERSION column family
   *
   * @param record the record of the form that is deleted
   */
  void deleteFormInFormByIdAndVersionColumn(FormRecord record);

  /**
   * Deletes a form from the cache
   *
   * @param record the record of the form that is deleted
   */
  void deleteFormFromCache(FormRecord record);

  /**
   * Deletes a form from FORM_VERSION column family
   *
   * @param record the record of the form that is deleted
   */
  void deleteFormInFormVersionColumn(FormRecord record);
}
