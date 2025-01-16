/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ModifyProcessInstanceRequestValidatorTest {
  private ModifyProcessInstanceRequestValidator underTest;

  @Mock private ProcessInstanceReader processInstanceReader;

  @BeforeEach
  public void setup() {
    underTest = new ModifyProcessInstanceRequestValidator(processInstanceReader);
  }

  @Test
  public void testValidateWithMissingProcessInstance() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto().setProcessInstanceKey("123");
    when(processInstanceReader.getProcessInstanceByKey(
            Long.valueOf(request.getProcessInstanceKey())))
        .thenReturn(null);

    final InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> underTest.validate(request));

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "Process instance with key %s does not exist", request.getProcessInstanceKey()));
  }

  @Test
  public void testValidateWithMissingModifications() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto().setProcessInstanceKey("123").setModifications(null);
    when(processInstanceReader.getProcessInstanceByKey(
            Long.valueOf(request.getProcessInstanceKey())))
        .thenReturn(new ProcessInstanceForListViewEntity());

    final InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> underTest.validate(request));

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "No modifications given for process instance with key %s",
                request.getProcessInstanceKey()));
  }

  @Test
  public void testValidateWithNullModificationType() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(List.of(new Modification().setModification(null)));
    when(processInstanceReader.getProcessInstanceByKey(
            Long.valueOf(request.getProcessInstanceKey())))
        .thenReturn(new ProcessInstanceForListViewEntity());

    final InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> underTest.validate(request));

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "Unknown Modification.Type given for process instance with key %s.",
                request.getProcessInstanceKey()));
  }
}
