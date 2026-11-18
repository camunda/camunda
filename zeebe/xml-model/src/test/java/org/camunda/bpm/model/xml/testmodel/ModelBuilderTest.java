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
package org.camunda.bpm.model.xml.testmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.MODEL_NAME;

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelElementInstanceImpl;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;
import org.junit.jupiter.api.Test;

/**
 * @author Daniel Meyer
 */
public class ModelBuilderTest {

  private static final String NAMESPACE = "urn:model-builder-test";

  @Test
  public void shouldSetModelName() {
    final Model model = ModelBuilder.createInstance(MODEL_NAME).build();

    assertThat(model.getModelName()).isEqualTo(MODEL_NAME);
  }

  @Test
  public void shouldNotRegisterReferencesAgainWhenBuildingTheModelAgain() {
    // given
    final ModelBuilder modelBuilder = ModelBuilder.createInstance(MODEL_NAME);
    defineTargetType(modelBuilder);
    final AttributeReference<Target> reference = defineTypeReferencingTarget(modelBuilder);
    final Model model = modelBuilder.build();

    // when
    modelBuilder.defineGenericType("unknown", NAMESPACE);
    modelBuilder.build();

    // then
    final Attribute<?> idAttribute = model.getType(Target.class).getAttribute("id");
    assertThat(idAttribute.getIncomingReferences()).containsExactly(reference);
  }

  @Test
  public void shouldBuildTypesDefinedAfterTheModelWasBuilt() {
    // given
    final ModelBuilder modelBuilder = ModelBuilder.createInstance(MODEL_NAME);
    defineTargetType(modelBuilder);
    final Model model = modelBuilder.build();

    // when
    final AttributeReference<Target> reference = defineTypeReferencingTarget(modelBuilder);
    modelBuilder.build();

    // then
    final ModelElementType targetType = model.getType(Target.class);
    final ModelElementType sourceType = model.getType(Source.class);
    assertThat(sourceType.getBaseType()).isEqualTo(targetType);
    assertThat(targetType.getExtendingTypes()).containsExactly(sourceType);
    assertThat(targetType.getAttribute("id").getIncomingReferences()).containsExactly(reference);
  }

  private static void defineTargetType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Target.class, "target")
            .namespaceUri(NAMESPACE)
            .instanceProvider(Target::new);
    typeBuilder.stringAttribute("id").idAttribute().build();
    typeBuilder.build();
  }

  private static AttributeReference<Target> defineTypeReferencingTarget(
      final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Source.class, "source")
            .namespaceUri(NAMESPACE)
            .extendsType(Target.class)
            .instanceProvider(Source::new);
    final AttributeReference<Target> reference =
        typeBuilder.stringAttribute("targetRef").idAttributeReference(Target.class).build();
    typeBuilder.build();
    return reference;
  }

  private static class Target extends ModelElementInstanceImpl {
    Target(final ModelTypeInstanceContext instanceContext) {
      super(instanceContext);
    }
  }

  private static final class Source extends Target {
    Source(final ModelTypeInstanceContext instanceContext) {
      super(instanceContext);
    }
  }
}
