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

import io.camunda.client.api.search.GroupChangeset;
import io.camunda.client.api.search.JobChangeset;
import io.camunda.client.api.search.ProblemDetail;
import io.camunda.client.api.search.filter.*;
import io.camunda.client.api.search.filter.AdHocSubprocessActivityRequestFilter;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ProcessInstanceStateEnum;
import java.util.ArrayList;
import java.util.List;

public class RequestMapper {

  public static io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter toProtocolObject(
      AdHocSubprocessActivityRequestFilter object) {
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
      BasicStringFilterProperty object) {
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

    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.DateTimeFilterProperty toProtocolObject(
      DateTimeFilterProperty object) {
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
      GroupChangeset object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.GroupChangeset protocolObject =
        new io.camunda.client.protocol.rest.GroupChangeset();
    protocolObject.setName(object.getName());
    return protocolObject;
  }

  public static io.camunda.client.protocol.rest.IntegerFilterProperty toProtocolObject(
      IntegerFilterProperty object) {
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

  public static io.camunda.client.protocol.rest.JobChangeset toProtocolObject(JobChangeset object) {
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
      ProblemDetail object) {
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
      ProcessInstanceStateFilterProperty object) {
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
      StringFilterProperty object) {
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
      toProtocolObject(ProcessInstanceVariableFilterRequest object) {
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
      toProtocolList(List<ProcessInstanceVariableFilterRequest> list) {
    if (list == null) {
      return null;
    }

    final List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest> protocolList =
        new ArrayList<>();
    list.forEach(item -> protocolList.add(toProtocolObject(item)));
    return protocolList;
  }

  public static io.camunda.client.protocol.rest.UserTaskVariableFilterRequest toProtocolObject(
      UserTaskVariableFilterRequest object) {
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
      toUserTaskVariableFilterRequestList(List<UserTaskVariableFilterRequest> list) {
    if (list == null) {
      return null;
    }

    final List<io.camunda.client.protocol.rest.UserTaskVariableFilterRequest> protocolList =
        new ArrayList<>();
    list.forEach(item -> protocolList.add(toProtocolObject(item)));
    return protocolList;
  }
}
