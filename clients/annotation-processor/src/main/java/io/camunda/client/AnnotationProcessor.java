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
package io.camunda.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.Group.Property;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes({
  AnnotationProcessor.ADDITIONAL_MAP_PROPERTIES_ANNOTATION,
  AnnotationProcessor.ADDITIONAL_LIST_PROPERTIES_ANNOTATION
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class AnnotationProcessor extends AbstractProcessor {
  static final String OUTPUT_PATH = "META-INF/additional-properties.json";
  static final String ADDITIONAL_MAP_PROPERTIES_ANNOTATION =
      "io.camunda.client.spring.properties.MapProperties";
  static final String ADDITIONAL_LIST_PROPERTIES_ANNOTATION =
      "io.camunda.client.spring.properties.ListProperties";
  static final String PROPERTY_PATH = "propertyPath";
  static final String PLACEHOLDER = "placeHolder";
  private final List<Group> propertyGroups = new ArrayList<>();

  @Override
  public boolean process(
      final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      writeProperties();
      return false;
    }
    for (final TypeElement annotation : annotations) {
      final String annotationType = annotation.getQualifiedName().toString();
      final Set<? extends Element> annotatedElements =
          roundEnv.getElementsAnnotatedWith(annotation);
      for (final Element annotatedElement : annotatedElements) {
        if (annotatedElement instanceof final TypeElement typeElement) {
          final AnnotationMirror annotationMirror = getAnnotation(annotatedElement, annotationType);
          final String propertyPath =
              getAnnotationElementStringValue(annotationMirror, PROPERTY_PATH);
          final String prefix =
              switch (annotationType) {
                case AnnotationProcessor.ADDITIONAL_MAP_PROPERTIES_ANNOTATION ->
                    propertyPath
                        + ".{"
                        + getAnnotationElementStringValue(annotationMirror, PLACEHOLDER)
                        + "}";
                case AnnotationProcessor.ADDITIONAL_LIST_PROPERTIES_ANNOTATION ->
                    propertyPath + "[]";
                default ->
                    throw new IllegalArgumentException(
                        "Unknown annotation type: " + annotationType);
              };
          final String sourceType = typeElement.getQualifiedName().toString();
          if (propertyGroups.stream().noneMatch(group -> group.name().equals(propertyPath))) {
            final Group group = new Group(propertyPath, prefix, sourceType, new ArrayList<>());
            propertyGroups.add(group);
            final List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            for (final Element enclosedElement : enclosedElements) {
              if (enclosedElement instanceof final VariableElement variableElement) {
                final TypeMirror type = variableElement.asType();
                final String docComment =
                    processingEnv
                        .getElementUtils()
                        .getDocComment(variableElement)
                        .replaceAll("\\n", "");
                final String propertyName =
                    prefix
                        + "."
                        + transformPropertyName(variableElement.getSimpleName().toString());
                if (group.properties().stream().noneMatch(p -> p.name().equals(propertyName))) {
                  group
                      .properties()
                      .add(new Property(propertyName, type.toString(), docComment, sourceType));
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  private void writeProperties() {
    try (final OutputStream out = createResource().openOutputStream()) {
      final ObjectMapper mapper = new ObjectMapper();
      mapper.writerWithDefaultPrettyPrinter().writeValue(out, propertyGroups);
    } catch (final IOException e) {
      throw new RuntimeException("Error in output stream to " + OUTPUT_PATH, e);
    }
  }

  private FileObject createResource() {
    try {
      return processingEnv
          .getFiler()
          .createResource(StandardLocation.CLASS_OUTPUT, "", OUTPUT_PATH);
    } catch (final IOException e) {
      throw new RuntimeException("Error while creating " + OUTPUT_PATH, e);
    }
  }

  String getAnnotationElementStringValue(final AnnotationMirror annotation, final String name) {
    return annotation.getElementValues().entrySet().stream()
        .filter((element) -> element.getKey().getSimpleName().toString().equals(name))
        .map((element) -> asString(getAnnotationValue(element.getValue())))
        .findFirst()
        .orElse(null);
  }

  private Object getAnnotationValue(final AnnotationValue annotationValue) {
    final Object value = annotationValue.getValue();
    if (value instanceof List) {
      final List<Object> values = new ArrayList<>();
      ((List<?>) value).forEach((v) -> values.add(((AnnotationValue) v).getValue()));
      return values;
    }
    return value;
  }

  private String asString(final Object value) {
    return (value == null || value.toString().isEmpty()) ? null : (String) value;
  }

  AnnotationMirror getAnnotation(final Element element, final String type) {
    if (element != null) {
      for (final AnnotationMirror annotation : element.getAnnotationMirrors()) {
        if (type.equals(annotation.getAnnotationType().toString())) {
          return annotation;
        }
      }
    }
    return null;
  }

  String transformPropertyName(final String propertyName) {
    return Pattern.compile("[A-Z]")
        .matcher(propertyName)
        .replaceAll(
            m -> {
              final String match = m.group();
              if (match.length() == 1) {
                return "-" + match.toLowerCase();
              } else {
                return Matcher.quoteReplacement(match);
              }
            });
  }
}
