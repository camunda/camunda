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

import io.camunda.client.api.search.enums.*;
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
  public void shouldConvertAdHocSubprocessActivityResultType() {

    for (final AdHocSubprocessActivityResultType value :
        AdHocSubprocessActivityResultType.values()) {
      final io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == AdHocSubprocessActivityResultType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum
        protocolValue :
            io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum.values()) {
      final AdHocSubprocessActivityResultType value =
          EnumUtil.convert(protocolValue, AdHocSubprocessActivityResultType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(AdHocSubprocessActivityResultType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceResultType() {

    for (final FlowNodeInstanceType value : FlowNodeInstanceType.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum protocolValue :
        io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum.values()) {
      final FlowNodeInstanceType value =
          EnumUtil.convert(protocolValue, FlowNodeInstanceType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceResultState() {

    for (final FlowNodeInstanceState value : FlowNodeInstanceState.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum protocolValue :
        io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum.values()) {
      final FlowNodeInstanceState value =
          EnumUtil.convert(protocolValue, FlowNodeInstanceState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceFilterType() {

    for (final FlowNodeInstanceType value : FlowNodeInstanceType.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum protocolValue :
        io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum.values()) {
      final FlowNodeInstanceType value =
          EnumUtil.convert(protocolValue, FlowNodeInstanceType.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceFilterState() {

    for (final FlowNodeInstanceState value : FlowNodeInstanceState.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum protocolValue =
          EnumUtil.convert(
              value, io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum
                    .UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum protocolValue :
        io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum.values()) {
      final FlowNodeInstanceState value =
          EnumUtil.convert(protocolValue, FlowNodeInstanceState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceState.UNKNOWN_ENUM_VALUE);
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
      final io.camunda.client.protocol.rest.UserTaskResult.StateEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.UserTaskResult.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.UserTaskResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.UserTaskResult.StateEnum protocolValue :
        io.camunda.client.protocol.rest.UserTaskResult.StateEnum.values()) {
      final UserTaskState value = EnumUtil.convert(protocolValue, UserTaskState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.UserTaskResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertUserTaskFilterState() {

    for (final UserTaskState value : UserTaskState.values()) {
      final io.camunda.client.protocol.rest.UserTaskFilter.StateEnum protocolValue =
          EnumUtil.convert(value, io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.class);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.UserTaskFilter.StateEnum protocolValue :
        io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.values()) {
      final UserTaskState value = EnumUtil.convert(protocolValue, UserTaskState.class);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }
}
