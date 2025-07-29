/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.BatchOperationErrorType;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.protocol.rest.BatchOperationError;
import io.camunda.client.protocol.rest.BatchOperationItemResponse;
import io.camunda.client.protocol.rest.BatchOperationResponse;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import org.junit.jupiter.api.Test;

public class EnumUtilTest {

  @Test
  public void shouldConvertOwnerType() {

    for (final OwnerType value : OwnerType.values()) {
      final io.camunda.client.protocol.rest.OwnerTypeEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.OwnerTypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == OwnerType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(io.camunda.client.protocol.rest.OwnerTypeEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.OwnerTypeEnum protocolValue :
        io.camunda.client.protocol.rest.OwnerTypeEnum.values()) {
      final OwnerType value = EnumUtil.convert(protocolValue, OwnerType.class);
      assertThat(value).isNotNull();
      if (protocolValue == io.camunda.client.protocol.rest.OwnerTypeEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(OwnerType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertPermissionType() {

    for (final PermissionType value : PermissionType.values()) {
      final io.camunda.client.protocol.rest.PermissionTypeEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.PermissionTypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == PermissionType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(io.camunda.client.protocol.rest.PermissionTypeEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.PermissionTypeEnum protocolValue :
        io.camunda.client.protocol.rest.PermissionTypeEnum.values()) {
      final PermissionType value = EnumUtil.convert(protocolValue, PermissionType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.PermissionTypeEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(PermissionType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertProcessInstanceState() {

    for (final ProcessInstanceState value : ProcessInstanceState.values()) {
      final io.camunda.client.protocol.rest.ProcessInstanceStateEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.ProcessInstanceStateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == ProcessInstanceState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.ProcessInstanceStateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.ProcessInstanceStateEnum protocolValue :
        io.camunda.client.protocol.rest.ProcessInstanceStateEnum.values()) {
      final ProcessInstanceState value =
          EnumUtil.convert(protocolValue, ProcessInstanceState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.ProcessInstanceStateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(ProcessInstanceState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertResourceType() {

    for (final ResourceType value : ResourceType.values()) {
      final io.camunda.client.protocol.rest.ResourceTypeEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.ResourceTypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == ResourceType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(io.camunda.client.protocol.rest.ResourceTypeEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.ResourceTypeEnum protocolValue :
        io.camunda.client.protocol.rest.ResourceTypeEnum.values()) {
      final ResourceType value = EnumUtil.convert(protocolValue, ResourceType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.ResourceTypeEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(ResourceType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertElementInstanceResultType() {

    for (final ElementInstanceType value : ElementInstanceType.values()) {
      final io.camunda.client.protocol.rest.ElementInstanceResult.TypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.ElementInstanceResult.TypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == ElementInstanceType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.ElementInstanceResult.TypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.ElementInstanceResult.TypeEnum protocolValue :
        io.camunda.client.protocol.rest.ElementInstanceResult.TypeEnum.values()) {
      final ElementInstanceType value = EnumUtil.convert(protocolValue, ElementInstanceType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.ElementInstanceResult.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(ElementInstanceType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertElementInstanceFilterType() {

    for (final ElementInstanceType value : ElementInstanceType.values()) {
      final io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == ElementInstanceType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum protocolValue :
        io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum.values()) {
      final ElementInstanceType value = EnumUtil.convert(protocolValue, ElementInstanceType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(ElementInstanceType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertElementInstanceFilterState() {

    for (final ElementInstanceState value : ElementInstanceState.values()) {
      final io.camunda.client.protocol.rest.ElementInstanceStateEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.ElementInstanceStateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == ElementInstanceState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.ElementInstanceStateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.ElementInstanceStateEnum protocolValue :
        io.camunda.client.protocol.rest.ElementInstanceStateEnum.values()) {
      final ElementInstanceState value =
          EnumUtil.convert(protocolValue, ElementInstanceState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.ElementInstanceStateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(ElementInstanceState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentResultState() {

    for (final IncidentState value : IncidentState.values()) {
      final io.camunda.client.protocol.rest.IncidentResult.StateEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.IncidentResult.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.IncidentResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.IncidentResult.StateEnum protocolValue :
        io.camunda.client.protocol.rest.IncidentResult.StateEnum.values()) {
      final IncidentState value = EnumUtil.convert(protocolValue, IncidentState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentResultErrorType() {

    for (final IncidentErrorType value : IncidentErrorType.values()) {
      final io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentErrorType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum protocolValue :
        io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum.values()) {
      final IncidentErrorType value = EnumUtil.convert(protocolValue, IncidentErrorType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentErrorType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentFilterState() {

    for (final IncidentState value : IncidentState.values()) {
      final io.camunda.client.protocol.rest.IncidentFilter.StateEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.IncidentFilter.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.IncidentFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.IncidentFilter.StateEnum protocolValue :
        io.camunda.client.protocol.rest.IncidentFilter.StateEnum.values()) {
      final IncidentState value = EnumUtil.convert(protocolValue, IncidentState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentFilterErrorType() {

    for (final IncidentErrorType value : IncidentErrorType.values()) {
      final io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentErrorType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum protocolValue :
        io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum.values()) {
      final IncidentErrorType value = EnumUtil.convert(protocolValue, IncidentErrorType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentErrorType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertUserTaskResultState() {

    for (final UserTaskState value : UserTaskState.values()) {
      final UserTaskStateEnum protocolValue = EnumUtil.convert(value, UserTaskStateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue).isEqualTo(UserTaskStateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final UserTaskStateEnum protocolValue : UserTaskStateEnum.values()) {
      final UserTaskState value = EnumUtil.convert(protocolValue, UserTaskState.class);
      assertThat(value).isNotNull();
      if (protocolValue == UserTaskStateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertUserTaskFilterState() {

    for (final UserTaskState value : UserTaskState.values()) {
      final UserTaskStateEnum protocolValue = EnumUtil.convert(value, UserTaskStateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue).isEqualTo(UserTaskStateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final UserTaskStateEnum protocolValue : UserTaskStateEnum.values()) {
      final UserTaskState value = EnumUtil.convert(protocolValue, UserTaskState.class);
      assertThat(value).isNotNull();
      if (protocolValue == UserTaskStateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertBatchOperationState() {

    for (final BatchOperationState value : BatchOperationState.values()) {
      final BatchOperationResponse.StateEnum protocolValue =
          EnumUtil.convert(value, BatchOperationResponse.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == BatchOperationState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(BatchOperationResponse.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final BatchOperationResponse.StateEnum protocolValue :
        BatchOperationResponse.StateEnum.values()) {
      final BatchOperationState value = EnumUtil.convert(protocolValue, BatchOperationState.class);
      assertThat(value).isNotNull();
      if (protocolValue == BatchOperationResponse.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(BatchOperationState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertBatchOperationItemState() {

    for (final BatchOperationItemState value : BatchOperationItemState.values()) {
      final BatchOperationItemResponse.StateEnum protocolValue =
          EnumUtil.convert(value, BatchOperationItemResponse.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == BatchOperationItemState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(BatchOperationItemResponse.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final BatchOperationItemResponse.StateEnum protocolValue :
        BatchOperationItemResponse.StateEnum.values()) {
      final BatchOperationItemState value =
          EnumUtil.convert(protocolValue, BatchOperationItemState.class);
      assertThat(value).isNotNull();
      if (protocolValue == BatchOperationItemResponse.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(BatchOperationItemState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertBatchOperationType() {

    for (final BatchOperationType value : BatchOperationType.values()) {
      final BatchOperationTypeEnum protocolValue =
          EnumUtil.convert(value, BatchOperationTypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == BatchOperationType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue).isEqualTo(BatchOperationTypeEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final BatchOperationTypeEnum protocolValue : BatchOperationTypeEnum.values()) {
      final BatchOperationType value = EnumUtil.convert(protocolValue, BatchOperationType.class);
      assertThat(value).isNotNull();
      if (protocolValue == BatchOperationTypeEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(BatchOperationType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertBatchOperationErrorType() {

    for (final BatchOperationErrorType value : BatchOperationErrorType.values()) {
      final BatchOperationError.TypeEnum protocolValue =
          EnumUtil.convert(value, BatchOperationError.TypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == BatchOperationErrorType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue).isEqualTo(BatchOperationError.TypeEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final BatchOperationError.TypeEnum protocolValue : BatchOperationError.TypeEnum.values()) {
      final BatchOperationErrorType value =
          EnumUtil.convert(protocolValue, BatchOperationErrorType.class);
      assertThat(value).isNotNull();
      if (protocolValue == BatchOperationError.TypeEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(BatchOperationErrorType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }
}
