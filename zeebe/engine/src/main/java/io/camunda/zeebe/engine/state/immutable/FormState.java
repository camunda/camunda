/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface FormState {

  /**
   * Query forms by the given form id and return the latest version of the form.
   *
   * @param formId the id of the form
   * @param tenantId the id of the tenant
   * @return the latest version of the form, or {@link Optional#empty()} if no form is deployed with
   *     the given id
   */
  Optional<PersistedForm> findLatestFormById(DirectBuffer formId, final String tenantId);

  /**
   * Query forms by the given form key and return the form.
   *
   * @param formKey the key of the form
   * @param tenantId the id of the tenant
   * @return the form, or {@link Optional#empty()} if no form is deployed with the given key
   */
  Optional<PersistedForm> findFormByKey(long formKey, final String tenantId);

  /**
   * Query forms by the given form id and deployment key and return the form.
   *
   * @param formId the id of the form
   * @param deploymentKey the key of the deployment the form was deployed with
   * @param tenantId the id of the tenant
   * @return the form, or {@link Optional#empty()} if no form with the given id was deployed with
   *     the given deployment
   */
  Optional<PersistedForm> findFormByIdAndDeploymentKey(
      DirectBuffer formId, long deploymentKey, final String tenantId);

  /**
   * Gets the next version a form of a given id will receive. This is used, for example, when a new
   * deployment is done. Using this method we decide the version the newly deployed form receives.
   *
   * @param formId the id of the form
   */
  int getNextFormVersion(String formId, String tenantId);

  void clearCache();
}
