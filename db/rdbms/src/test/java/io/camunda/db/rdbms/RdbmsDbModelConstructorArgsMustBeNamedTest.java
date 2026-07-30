/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Enforces invariants on every MyBatis {@code <constructor>} block whose enclosing {@code resultMap
 * type=} is a {@code io.camunda.db.rdbms.write.domain.*DbModel}:
 *
 * <ul>
 *   <li>every {@code <arg>}/{@code <idArg>} carries a non-blank {@code name=} attribute, so
 *       MyBatis's constructor-arg binding is order-independent instead of relying on column
 *       declaration order matching Java parameter order
 *   <li>for record targets, that {@code name=} must match an actual record component -- a typo
 *       would otherwise only surface as a {@code BuilderException} at Spring-context-startup
 *   <li>for record targets, the {@code javaType} alias resolves (via MyBatis's own {@link
 *       Configuration#getTypeAliasRegistry()}) to exactly the declared type of the matching record
 *       component -- catching a primitive/wrapper alias mismatch (e.g. {@code javaType="long"},
 *       which resolves to {@code Long.class}, used for a primitive {@code long} component) at unit
 *       test time instead of only at Spring-context-startup in a full integration test
 * </ul>
 *
 * Read-side {@code *Entity} resultMaps are intentionally out of scope -- only {@code write.domain}
 * DbModels are covered by this series' invariant enforcement.
 */
class RdbmsDbModelConstructorArgsMustBeNamedTest {

  private static final String MAPPER_DIR = "src/main/resources/mapper";
  private static final String DB_MODEL_PACKAGE_PREFIX = "io.camunda.db.rdbms.write.domain.";

  // Not yet fully constructor-based -- covered by the still-open PR #58969, which rewrites these
  // resultMaps to constructor-based mapping with name= on every arg. Remove once that PR merges.
  private static final Set<String> PENDING_PR_58969 =
      Set.of("JobMapper.xml", "MessageSubscriptionMapper.xml", "UserTaskMapper.xml");

  @Test
  void everyDbModelConstructorArgMustBeNamedAndCorrectlyTyped() throws Exception {
    final var mapperDir = new File(MAPPER_DIR);
    assertThat(mapperDir).as("mapper resources directory").isDirectory();
    final var mapperFiles = mapperDir.listFiles((dir, name) -> name.endsWith(".xml"));
    assertThat(mapperFiles).as("mapper XML files").isNotEmpty();

    final var typeAliasRegistry = new Configuration().getTypeAliasRegistry();
    final var softly = new SoftAssertions();
    final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    // Mapper XMLs declare a DOCTYPE pointing at mybatis.org's DTD; without this, every parse
    // fetches it over the network, making the test dependent on external connectivity.
    documentBuilderFactory.setFeature(
        "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

    for (final File mapperFile : mapperFiles) {
      if (PENDING_PR_58969.contains(mapperFile.getName())) {
        continue;
      }
      checkMapperFile(mapperFile, documentBuilderFactory, typeAliasRegistry, softly);
    }

    softly.assertAll();
  }

  private void checkMapperFile(
      final File mapperFile,
      final DocumentBuilderFactory documentBuilderFactory,
      final TypeAliasRegistry typeAliasRegistry,
      final SoftAssertions softly)
      throws Exception {
    final var document = documentBuilderFactory.newDocumentBuilder().parse(mapperFile);

    final var resultMaps = document.getElementsByTagName("resultMap");
    for (int i = 0; i < resultMaps.getLength(); i++) {
      final var resultMap = (Element) resultMaps.item(i);
      checkResultMapOwnConstructor(mapperFile, resultMap, typeAliasRegistry, softly);
      checkNestedCollectionConstructors(mapperFile, resultMap, typeAliasRegistry, softly);
    }
  }

  private void checkResultMapOwnConstructor(
      final File mapperFile,
      final Element resultMap,
      final TypeAliasRegistry typeAliasRegistry,
      final SoftAssertions softly) {
    final var type = resultMap.getAttribute("type");
    if (!type.startsWith(DB_MODEL_PACKAGE_PREFIX)) {
      return;
    }
    final var constructor = directChild(resultMap, "constructor");
    if (constructor == null) {
      return;
    }
    checkConstructorArgs(mapperFile, type, constructor, typeAliasRegistry, softly);
  }

  private void checkNestedCollectionConstructors(
      final File mapperFile,
      final Element resultMap,
      final TypeAliasRegistry typeAliasRegistry,
      final SoftAssertions softly) {
    final var collections = resultMap.getElementsByTagName("collection");
    for (int i = 0; i < collections.getLength(); i++) {
      final var collection = (Element) collections.item(i);
      final var ofType = collection.getAttribute("ofType");
      if (!ofType.startsWith(DB_MODEL_PACKAGE_PREFIX)) {
        continue;
      }
      final var constructor = directChild(collection, "constructor");
      if (constructor == null) {
        continue;
      }
      checkConstructorArgs(mapperFile, ofType, constructor, typeAliasRegistry, softly);
    }
  }

  private void checkConstructorArgs(
      final File mapperFile,
      final String targetTypeName,
      final Element constructor,
      final TypeAliasRegistry typeAliasRegistry,
      final SoftAssertions softly) {
    final Class<?> targetType = classForName(targetTypeName);
    final var recordComponentsByName = recordComponentsByName(targetType);

    final var args = new ArrayList<Element>();
    collectDirectChildren(constructor, "arg", args);
    collectDirectChildren(constructor, "idArg", args);

    for (final var arg : args) {
      final var column = arg.getAttribute("column");
      final var name = arg.getAttribute("name");
      final var location =
          mapperFile.getName() + " <" + arg.getTagName() + " column=\"" + column + "\">";

      softly.assertThat(name).as("%s must have a non-blank name=", location).isNotBlank();

      if (name.isBlank() || recordComponentsByName == null) {
        continue;
      }

      final var component = recordComponentsByName.get(name);
      softly
          .assertThat(component)
          .as(
              "%s name=\"%s\" must match a component of record %s",
              location, name, targetType.getSimpleName())
          .isNotNull();

      if (component == null) {
        continue;
      }

      final var javaType = arg.getAttribute("javaType");
      if (javaType.isBlank()) {
        continue;
      }

      final Class<?> resolvedJavaType = typeAliasRegistry.resolveAlias(javaType);
      softly
          .assertThat(resolvedJavaType)
          .as(
              "%s javaType=\"%s\" resolves to %s, but record component \"%s\" is declared as %s",
              location, javaType, resolvedJavaType, name, component.getType())
          .isEqualTo(component.getType());
    }
  }

  private Element directChild(final Element parent, final String tagName) {
    final NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node child = children.item(i);
      if (child instanceof Element element && tagName.equals(element.getTagName())) {
        return element;
      }
    }
    return null;
  }

  private void collectDirectChildren(
      final Element parent, final String tagName, final List<Element> out) {
    final NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node child = children.item(i);
      if (child instanceof Element element && tagName.equals(element.getTagName())) {
        out.add(element);
      }
    }
  }

  private Map<String, RecordComponent> recordComponentsByName(final Class<?> type) {
    if (!type.isRecord()) {
      return null;
    }
    final var map = new HashMap<String, RecordComponent>();
    for (final var component : type.getRecordComponents()) {
      map.put(component.getName(), component);
    }
    return map;
  }

  private Class<?> classForName(final String name) {
    try {
      return Class.forName(name);
    } catch (final ClassNotFoundException e) {
      throw new IllegalStateException("Could not load class " + name, e);
    }
  }
}
