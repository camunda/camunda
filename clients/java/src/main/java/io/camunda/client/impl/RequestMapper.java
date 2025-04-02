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
package io.camunda.client.impl;

import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.GroupChangeset;
import io.camunda.client.api.command.JobChangeset;
import io.camunda.client.api.search.filter.*;
import io.camunda.client.api.search.filter.AdHocSubprocessActivityRequestFilter;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ProcessInstanceStateEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RequestMapper {

  public static io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter toProtocolObject(
      final AdHocSubprocessActivityRequestFilter object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter protocolObject =
        new io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter();
    protocolObject.setProcessDefinitionKey(object.getProcessDefinitionKey());
    protocolObject.setAdHocSubprocessId(object.getAdHocSubprocessId());
    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.BasicStringFilterProperty toProtocolObject(
      final BasicStringFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.BasicStringFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.BasicStringFilterProperty();
    protocolObject.set$Eq(object.getEq());
    protocolObject.set$Neq(object.getNeq());
    protocolObject.set$Exists(object.getExists());
    if (object.getIn() == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object.getIn().forEach(protocolObject::add$InItem);
    }

    if (object.getNotIn() == null) {
      protocolObject.set$NotIn(null);
    } else {
      protocolObject.set$NotIn(new ArrayList<>());
      object.getNotIn().forEach(protocolObject::add$NotInItem);
    }

    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.DateTimeFilterProperty toProtocolObject(
      final DateTimeFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.DateTimeFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.DateTimeFilterProperty();
    protocolObject.set$Eq(object.getEq());
    protocolObject.set$Neq(object.getNeq());
    protocolObject.set$Exists(object.getExists());
    protocolObject.set$Gt(object.getGt());
    protocolObject.set$Gte(object.getGte());
    protocolObject.set$Lt(object.getLt());
    protocolObject.set$Lte(object.getLte());
    if (object.getIn() == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object.getIn().forEach(protocolObject::add$InItem);
    }

    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.GroupChangeset toProtocolObject(
      final GroupChangeset object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.GroupChangeset protocolObject =
        new io.camunda.client.protocol.rest.GroupChangeset();
    protocolObject.setName(object.getName());
    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.IntegerFilterProperty toProtocolObject(
      final IntegerFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.IntegerFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.IntegerFilterProperty();
    protocolObject.set$Eq(object.getEq());
    protocolObject.set$Neq(object.getNeq());
    protocolObject.set$Exists(object.getExists());
    protocolObject.set$Gt(object.getGt());
    protocolObject.set$Gte(object.getGte());
    protocolObject.set$Lt(object.getLt());
    protocolObject.set$Lte(object.getLte());
    if (object.getIn() == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object.getIn().forEach(protocolObject::add$InItem);
    }

    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.JobChangeset toProtocolObject(
      final JobChangeset object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.JobChangeset protocolObject =
        new io.camunda.client.protocol.rest.JobChangeset();
    protocolObject.setRetries(object.getRetries());
    protocolObject.setTimeout(object.getTimeout());
    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.ProblemDetail toProtocolObject(
      final ProblemDetail object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.ProblemDetail protocolObject =
        new io.camunda.client.protocol.rest.ProblemDetail();
    protocolObject.setType(object.getType());
    protocolObject.setTitle(object.getTitle());
    protocolObject.setStatus(object.getStatus());
    protocolObject.setDetail(object.getDetail());
    protocolObject.setInstance(object.getInstance());
    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty toProtocolObject(
      final ProcessInstanceStateFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty();
    protocolObject.set$Eq(EnumUtil.convert(object.getEq(), ProcessInstanceStateEnum.class));
    protocolObject.set$Exists(object.getExists());
    if (object.getIn() == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object
          .getIn()
          .forEach(
              item ->
                  protocolObject.add$InItem(
                      EnumUtil.convert(item, ProcessInstanceStateEnum.class)));
    }
    protocolObject.set$Like(object.getLike());
    protocolObject.set$Neq(EnumUtil.convert(object.getNeq(), ProcessInstanceStateEnum.class));

    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.StringFilterProperty toProtocolObject(
      final StringFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.StringFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.StringFilterProperty();
    protocolObject.set$Eq(object.getEq());
    protocolObject.set$Neq(object.getNeq());
    protocolObject.set$Exists(object.getExists());
    if (object.getIn() == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object.getIn().forEach(protocolObject::add$InItem);
    }
    protocolObject.set$Like(object.getLike());

    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest
      toProtocolObject(final ProcessInstanceVariableFilterRequest object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest protocolObject =
        new io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest();
    protocolObject.setName(object.getName());
    protocolObject.setValue(RequestMapper.toProtocolObject(object.getValue()));
    return protocolObject;
  }

  public static List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest>
      toProtocolList(final List<ProcessInstanceVariableFilterRequest> list) {
    if (list == null) {
      return null;
    }

    final List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest> protocolList =
        new ArrayList<>();
    list.forEach(item -> protocolList.add(toProtocolObject(item)));
    return protocolList;
  }

  public static io.camunda.client.protocol.rest.UserTaskVariableFilterRequest toProtocolObject(
      final UserTaskVariableFilterRequest object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.UserTaskVariableFilterRequest protocolObject =
        new io.camunda.client.protocol.rest.UserTaskVariableFilterRequest();
    protocolObject.setName(object.getName());
    protocolObject.setValue(RequestMapper.toProtocolObject(object.getValue()));
    return protocolObject;
  }

  public static List<io.camunda.client.protocol.rest.UserTaskVariableFilterRequest>
      toUserTaskVariableFilterRequestList(final List<UserTaskVariableFilterRequest> list) {
    if (list == null) {
      return null;
    }

    final List<io.camunda.client.protocol.rest.UserTaskVariableFilterRequest> protocolList =
        new ArrayList<>();
    list.forEach(item -> protocolList.add(toProtocolObject(item)));
    return protocolList;
  }

  public static List<ProcessInstanceVariableFilterRequest>
      toProcessInstanceVariableFilterRequestList(final Map<String, Object> variableValueFilters) {
    return variableValueFilters.entrySet().stream()
        .map(
            entry -> {
              if (entry.getValue() == null) {
                throw new IllegalArgumentException("Variable value cannot be null");
              }
              final ProcessInstanceVariableFilterRequest request =
                  new ProcessInstanceVariableFilterRequest();
              request.setName(entry.getKey());
              final StringProperty property = new StringPropertyImpl();
              property.eq(entry.getValue().toString());
              request.setValue(property.build());
              return request;
            })
        .collect(Collectors.toList());
  }
}
