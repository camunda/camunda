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
package io.camunda.client.wrappers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ProtocolEnumWrappersTest {

  @Test
  public void shouldConvertOwnerType() {

    for (final OwnerType value : OwnerType.values()) {
      final io.camunda.client.protocol.rest.OwnerTypeEnum protocolValue =
          OwnerType.toProtocolEnum(value);
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
      final OwnerType value = OwnerType.fromProtocolEnum(protocolValue);
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
          PermissionType.toProtocolEnum(value);
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
      final PermissionType value = PermissionType.fromProtocolEnum(protocolValue);
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
          ProcessInstanceState.toProtocolEnum(value);
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
      final ProcessInstanceState value = ProcessInstanceState.fromProtocolEnum(protocolValue);
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
          ResourceType.toProtocolEnum(value);
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
      final ResourceType value = ResourceType.fromProtocolEnum(protocolValue);
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

    for (final AdHocSubprocessActivityResult.Type value :
        AdHocSubprocessActivityResult.Type.values()) {
      final io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum protocolValue =
          AdHocSubprocessActivityResult.Type.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == AdHocSubprocessActivityResult.Type.UNKNOWN_ENUM_VALUE) {
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
      final AdHocSubprocessActivityResult.Type value =
          AdHocSubprocessActivityResult.Type.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.AdHocSubprocessActivityResult.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(AdHocSubprocessActivityResult.Type.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceResultType() {

    for (final FlowNodeInstanceResult.Type value : FlowNodeInstanceResult.Type.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum protocolValue =
          FlowNodeInstanceResult.Type.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceResult.Type.UNKNOWN_ENUM_VALUE) {
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
      final FlowNodeInstanceResult.Type value =
          FlowNodeInstanceResult.Type.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceResult.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceResult.Type.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceResultState() {

    for (final FlowNodeInstanceResult.State value : FlowNodeInstanceResult.State.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum protocolValue =
          FlowNodeInstanceResult.State.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceResult.State.UNKNOWN_ENUM_VALUE) {
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
      final FlowNodeInstanceResult.State value =
          FlowNodeInstanceResult.State.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceResult.StateEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceResult.State.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceFilterType() {

    for (final FlowNodeInstanceFilter.Type value : FlowNodeInstanceFilter.Type.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum protocolValue =
          FlowNodeInstanceFilter.Type.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceFilter.Type.UNKNOWN_ENUM_VALUE) {
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
      final FlowNodeInstanceFilter.Type value =
          FlowNodeInstanceFilter.Type.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceFilter.Type.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceFilterState() {

    for (final FlowNodeInstanceFilter.State value : FlowNodeInstanceFilter.State.values()) {
      final io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum protocolValue =
          FlowNodeInstanceFilter.State.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceFilter.State.UNKNOWN_ENUM_VALUE) {
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
      final FlowNodeInstanceFilter.State value =
          FlowNodeInstanceFilter.State.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceFilter.State.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentResultState() {

    for (final IncidentResult.State value : IncidentResult.State.values()) {
      final io.camunda.client.protocol.rest.IncidentResult.StateEnum protocolValue =
          IncidentResult.State.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentResult.State.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.IncidentResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.IncidentResult.StateEnum protocolValue :
        io.camunda.client.protocol.rest.IncidentResult.StateEnum.values()) {
      final IncidentResult.State value = IncidentResult.State.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentResult.State.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentResultErrorType() {

    for (final IncidentResult.ErrorType value : IncidentResult.ErrorType.values()) {
      final io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum protocolValue =
          IncidentResult.ErrorType.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentResult.ErrorType.UNKNOWN_ENUM_VALUE) {
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
      final IncidentResult.ErrorType value =
          IncidentResult.ErrorType.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentResult.ErrorTypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentResult.ErrorType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentFilterState() {

    for (final IncidentFilter.State value : IncidentFilter.State.values()) {
      final io.camunda.client.protocol.rest.IncidentFilter.StateEnum protocolValue =
          IncidentFilter.State.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentFilter.State.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.IncidentFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.IncidentFilter.StateEnum protocolValue :
        io.camunda.client.protocol.rest.IncidentFilter.StateEnum.values()) {
      final IncidentFilter.State value = IncidentFilter.State.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentFilter.State.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertIncidentFilterErrorType() {

    for (final IncidentFilter.ErrorType value : IncidentFilter.ErrorType.values()) {
      final io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum protocolValue =
          IncidentFilter.ErrorType.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == IncidentFilter.ErrorType.UNKNOWN_ENUM_VALUE) {
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
      final IncidentFilter.ErrorType value =
          IncidentFilter.ErrorType.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.IncidentFilter.ErrorTypeEnum
              .UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(IncidentFilter.ErrorType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertUserTaskResultState() {

    for (final UserTaskResult.State value : UserTaskResult.State.values()) {
      final io.camunda.client.protocol.rest.UserTaskResult.StateEnum protocolValue =
          UserTaskResult.State.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskResult.State.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.UserTaskResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.UserTaskResult.StateEnum protocolValue :
        io.camunda.client.protocol.rest.UserTaskResult.StateEnum.values()) {
      final UserTaskResult.State value = UserTaskResult.State.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.UserTaskResult.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskResult.State.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertUserTaskFilterState() {

    for (final UserTaskFilter.State value : UserTaskFilter.State.values()) {
      final io.camunda.client.protocol.rest.UserTaskFilter.StateEnum protocolValue =
          UserTaskFilter.State.toProtocolEnum(value);
      assertThat(protocolValue).isNotNull();
      if (value == UserTaskFilter.State.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(
                io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final io.camunda.client.protocol.rest.UserTaskFilter.StateEnum protocolValue :
        io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.values()) {
      final UserTaskFilter.State value = UserTaskFilter.State.fromProtocolEnum(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue
          == io.camunda.client.protocol.rest.UserTaskFilter.StateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(UserTaskFilter.State.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }
}
