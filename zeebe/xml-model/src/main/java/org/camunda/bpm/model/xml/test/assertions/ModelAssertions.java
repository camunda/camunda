/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.xml.test.assertions;

import org.assertj.core.api.Assertions;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * @author Sebastian Menski
 */
public class ModelAssertions extends Assertions {

  public static AttributeAssert assertThat(Attribute<?> actual) {
    return new AttributeAssert(actual);
  }

  public static ModelElementTypeAssert assertThat(ModelElementType actual) {
    return new ModelElementTypeAssert(actual);
  }

  public static ChildElementAssert assertThat(ChildElementCollection<?> actual) {
    return new ChildElementAssert(actual);
  }

  public static AttributeReferenceAssert assertThat(AttributeReference<?> actual) {
    return new AttributeReferenceAssert(actual);
  }

  public static ElementReferenceCollectionAssert assertThat(ElementReferenceCollection<?,?> actual) {
    return new ElementReferenceCollectionAssert(actual);
  }

}
