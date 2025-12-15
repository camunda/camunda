/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TestCheck.FORM_EXISTS_CHECK;
import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DELETED_CHECK;
import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestChecks {

  @Autowired private ProcessStore processStore;

  @Autowired private FormStore formStore;

  /** Checks whether the process of given args[0] processId (numeric string) is deployed. */
  @Bean(name = PROCESS_IS_DEPLOYED_CHECK)
  public TestCheck getProcessIsDeployedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return PROCESS_IS_DEPLOYED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String processId = (String) objects[0];
        try {
          final ProcessEntity process = processStore.getProcess(processId);
          return process != null;
        } catch (final TasklistRuntimeException ex) {
          return false;
        }
      }
    };
  }

  /** Checks whether the process of given args[0] processId (numeric string) is deleted. */
  @Bean(name = PROCESS_IS_DELETED_CHECK)
  public TestCheck getProcessIsDeletedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return PROCESS_IS_DELETED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String processId = (String) objects[0];
        try {
          processStore.getProcess(processId);
          return false;
        } catch (final NotFoundException nfe) {
          return true;
        } catch (final TasklistRuntimeException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the form of given args[0] processDefinitionId and args[1] formId exists for a
   * given args[2] versionId (Long - which may be null if we do not want a specific version).
   */
  @Bean(name = FORM_EXISTS_CHECK)
  public TestCheck getFormExistsCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return FORM_EXISTS_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(3);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        if (objects[2] != null) {
          assertThat(objects[2]).isInstanceOf(Long.class);
        }
        final String processDefinitionId = (String) objects[0];
        final String formId = (String) objects[1];
        final Long versionId = (Long) objects[2];
        try {
          return formStore.getForm(formId, processDefinitionId, versionId) != null;
        } catch (final NotFoundException ex) {
          return false;
        }
      }
    };
  }
}
