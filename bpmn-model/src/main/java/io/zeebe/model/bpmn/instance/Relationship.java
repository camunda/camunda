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

package io.zeebe.model.bpmn.instance;

import io.zeebe.model.bpmn.RelationshipDirection;
import io.zeebe.model.bpmn.impl.instance.Source;
import io.zeebe.model.bpmn.impl.instance.Target;
import java.util.Collection;

/**
 * The BPMN relationship element
 *
 * @author Sebastian Menski
 */
public interface Relationship extends BaseElement {

  String getType();

  void setType(String type);

  RelationshipDirection getDirection();

  void setDirection(RelationshipDirection direction);

  Collection<Source> getSources();

  Collection<Target> getTargets();
}
