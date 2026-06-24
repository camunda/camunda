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

package io.camunda.zeebe.model.bpmn.instance.dc;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.NamedBpmnElement;

/**
 * The DC font element
 *
 * @author Sebastian Menski
 */
public interface Font extends BpmnModelElementInstance, NamedBpmnElement {

  Double getSize();

  void setSize(Double size);

  Boolean isBold();

  void setBold(boolean isBold);

  Boolean isItalic();

  void setItalic(boolean isItalic);

  Boolean isUnderline();

  void setUnderline(boolean isUnderline);

  Boolean isStrikeThrough();

  void setStrikeTrough(boolean isStrikeTrough);
}
