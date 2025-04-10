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
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.*;
import io.camunda.client.api.search.filter.AdHocSubprocessActivityRequestFilter;
import io.camunda.client.impl.util.EnumUtil;
import java.util.ArrayList;
import java.util.List;

public class ResponseMapper {

  public static AdHocSubprocessActivityRequestFilter fromProtocolObject(
      final io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final AdHocSubprocessActivityRequestFilter object = new AdHocSubprocessActivityRequestFilter();
    object.setProcessDefinitionKey(protocolObject.getProcessDefinitionKey());
    object.setAdHocSubprocessId(protocolObject.getAdHocSubprocessId());
    return object;
  }

  public static BasicStringFilterProperty fromProtocolObject(
      final io.camunda.client.protocol.rest.BasicStringFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final BasicStringFilterProperty object = new BasicStringFilterProperty();
    object.setEq(protocolObject.get$Eq());
    object.setNeq(protocolObject.get$Neq());
    object.setExists(protocolObject.get$Exists());
    if (protocolObject.get$In() == null) {
      object.setIn(null);
    } else {
      object.setIn(new ArrayList<>());
      object.getIn().addAll(protocolObject.get$In());
    }
    if (protocolObject.get$NotIn() == null) {
      object.setNotIn(null);
    } else {
      object.setNotIn(new ArrayList<>());
      object.getNotIn().addAll(protocolObject.get$NotIn());
    }

    return object;
  }

  public static DateTimeFilterProperty fromProtocolObject(
      final io.camunda.client.protocol.rest.DateTimeFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final DateTimeFilterProperty object = new DateTimeFilterProperty();
    object.setEq(protocolObject.get$Eq());
    object.setNeq(protocolObject.get$Neq());
    object.setExists(protocolObject.get$Exists());
    object.setGt(protocolObject.get$Gt());
    object.setGte(protocolObject.get$Gte());
    object.setLt(protocolObject.get$Lt());
    object.setLte(protocolObject.get$Lte());
    if (protocolObject.get$In() == null) {
      object.setIn(null);
    } else {
      object.setIn(new ArrayList<>());
      object.getIn().addAll(protocolObject.get$In());
    }
    return object;
  }

  public static GroupChangeset fromProtocolObject(
      final io.camunda.client.protocol.rest.GroupChangeset protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final GroupChangeset object = new GroupChangeset();
    object.setName(protocolObject.getName());
    return object;
  }

  public static IntegerFilterProperty fromProtocolObject(
      final io.camunda.client.protocol.rest.IntegerFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final IntegerFilterProperty object = new IntegerFilterProperty();
    object.setEq(protocolObject.get$Eq());
    object.setNeq(protocolObject.get$Neq());
    object.setExists(protocolObject.get$Exists());
    object.setGt(protocolObject.get$Gt());
    object.setGte(protocolObject.get$Gte());
    object.setLt(protocolObject.get$Lt());
    object.setLte(protocolObject.get$Lte());
    if (protocolObject.get$In() == null) {
      object.setIn(null);
    } else {
      object.setIn(new ArrayList<>());
      object.getIn().addAll(protocolObject.get$In());
    }
    return object;
  }

  public static JobChangeset fromProtocolObject(
      final io.camunda.client.protocol.rest.JobChangeset protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final JobChangeset object = new JobChangeset();
    object.setRetries(protocolObject.getRetries());
    object.setTimeout(protocolObject.getTimeout());
    return object;
  }

  public static ProblemDetail fromProtocolObject(
      final io.camunda.client.protocol.rest.ProblemDetail protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final ProblemDetail object = new ProblemDetail();
    object.setType(protocolObject.getType());
    object.setTitle(protocolObject.getTitle());
    object.setStatus(protocolObject.getStatus());
    object.setDetail(protocolObject.getDetail());
    object.setInstance(protocolObject.getInstance());
    return object;
  }

  public static ProcessInstanceStateFilterProperty fromProtocolObject(
      final io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final ProcessInstanceStateFilterProperty object = new ProcessInstanceStateFilterProperty();
    object.setEq(EnumUtil.convert(protocolObject.get$Eq(), ProcessInstanceState.class));
    object.setExists(protocolObject.get$Exists());
    if (protocolObject.get$In() == null) {
      object.setIn(null);
    } else {
      object.setIn(new ArrayList<>());
      protocolObject
          .get$In()
          .forEach(item -> object.getIn().add(EnumUtil.convert(item, ProcessInstanceState.class)));
    }
    object.setLike(protocolObject.get$Like());
    object.setNeq(EnumUtil.convert(protocolObject.get$Neq(), ProcessInstanceState.class));

    return object;
  }

  public static StringFilterProperty fromProtocolObject(
      final io.camunda.client.protocol.rest.StringFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final StringFilterProperty object = new StringFilterProperty();
    object.setEq(protocolObject.get$Eq());
    object.setNeq(protocolObject.get$Neq());
    object.setExists(protocolObject.get$Exists());
    if (protocolObject.get$In() == null) {
      object.setIn(null);
    } else {
      object.setIn(new ArrayList<>());
      object.getIn().addAll(protocolObject.get$In());
    }
    object.setLike(protocolObject.get$Like());
    return object;
  }

  public static ProcessInstanceVariableFilterRequest fromProtocolObject(
      final io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final ProcessInstanceVariableFilterRequest object = new ProcessInstanceVariableFilterRequest();
    object.setName(protocolObject.getName());
    object.setValue(ResponseMapper.fromProtocolObject(protocolObject.getValue()));
    return object;
  }

  public static List<ProcessInstanceVariableFilterRequest> fromProtocolList(
      final List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest>
          protocolList) {
    if (protocolList == null) {
      return null;
    }

    final List<ProcessInstanceVariableFilterRequest> list = new ArrayList<>();
    protocolList.forEach(item -> list.add(fromProtocolObject(item)));
    return list;
  }

  public static UserTaskVariableFilterRequest fromProtocolObject(
      final io.camunda.client.protocol.rest.UserTaskVariableFilterRequest protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final UserTaskVariableFilterRequest object = new UserTaskVariableFilterRequest();
    object.setName(protocolObject.getName());
    object.setValue(ResponseMapper.fromProtocolObject(protocolObject.getValue()));
    return object;
  }

  public static List<UserTaskVariableFilterRequest> fromUserTaskVariableFilterRequestList(
      final List<io.camunda.client.protocol.rest.UserTaskVariableFilterRequest> protocolList) {
    if (protocolList == null) {
      return null;
    }

    final List<UserTaskVariableFilterRequest> list = new ArrayList<>();
    protocolList.forEach(item -> list.add(fromProtocolObject(item)));
    return list;
  }
}
