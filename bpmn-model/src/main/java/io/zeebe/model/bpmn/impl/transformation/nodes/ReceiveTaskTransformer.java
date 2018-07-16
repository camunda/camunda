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
package io.zeebe.model.bpmn.impl.transformation.nodes;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.MessageImpl;
import io.zeebe.model.bpmn.impl.instance.ReceiveTaskImpl;
import io.zeebe.model.bpmn.impl.metadata.MessageSubscriptionImpl;
import io.zeebe.model.bpmn.impl.metadata.SubscriptionImpl;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import java.util.List;
import java.util.Map;

public class ReceiveTaskTransformer {

  public void transform(
      ErrorCollector errorCollector,
      List<ReceiveTaskImpl> receiveTasks,
      Map<String, MessageImpl> messagesById) {

    for (ReceiveTaskImpl receiveTask : receiveTasks) {

      final String messageRef = receiveTask.getMessageRef();
      if (messageRef != null) {
        final MessageImpl message = messagesById.get(messageRef);
        final String messageName = message.getName();

        final JsonPathQuery query = createCorrelationKeyQuery(errorCollector, message);

        receiveTask.setMessageSubscription(new MessageSubscriptionImpl(messageName, query));
      }
    }
  }

  private JsonPathQuery createCorrelationKeyQuery(
      ErrorCollector errorCollector, final MessageImpl message) {

    final SubscriptionImpl subscription = message.getSubscription();
    final String correlationKey = subscription.getCorrelationKey();

    final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();
    final JsonPathQuery query = compiler.compile(correlationKey);

    if (!query.isValid()) {
      errorCollector.addError(
          message,
          String.format(
              "JSON path query '%s' is not valid! Reason: %s",
              bufferAsString(query.getExpression()), query.getErrorReason()));
    }

    return query;
  }
}
