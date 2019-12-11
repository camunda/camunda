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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_METHOD;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_TRANSACTION;

import io.zeebe.model.bpmn.TransactionMethod;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.Transaction;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/** @author Thorben Lindhauer */
public class TransactionImpl extends SubProcessImpl implements Transaction {

  protected static Attribute<TransactionMethod> methodAttribute;

  public TransactionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Transaction.class, BPMN_ELEMENT_TRANSACTION)
            .namespaceUri(BPMN20_NS)
            .extendsType(SubProcess.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Transaction>() {
                  @Override
                  public Transaction newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new TransactionImpl(instanceContext);
                  }
                });

    methodAttribute =
        typeBuilder
            .namedEnumAttribute(BPMN_ATTRIBUTE_METHOD, TransactionMethod.class)
            .defaultValue(TransactionMethod.Compensate)
            .build();

    typeBuilder.build();
  }

  @Override
  public TransactionMethod getMethod() {
    return methodAttribute.getValue(this);
  }

  @Override
  public void setMethod(final TransactionMethod method) {
    methodAttribute.setValue(this, method);
  }
}
