/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaInterfaceSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Replaces the openapi-generator "advanced" execution. Reads the multi-file OpenAPI spec under
 * {@code zeebe/gateway-protocol/src/main/proto/v2/}, merges {@code components.schemas} across all
 * YAML files, and emits one Java source per schema into the configured output directory.
 *
 * <p>Each emission is built as a Roaster {@link JavaSource} so the structure (fields, ctor,
 * accessors, builder, enums) is shaped via an AST API rather than concatenated strings; the
 * resulting source text is prepended with the project's standard license header (read from the
 * file path passed as the third CLI argument, normally wired to the
 * {@code ${license.header.file}} Maven property).
 */
public final class ModelGenerator {

  private static final String PKG = "io.camunda.gateway.protocol.model";
  private static final String NULLABLE = "org.jspecify.annotations.Nullable";
  private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
  private static final String JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator";
  private static final String JSON_VALUE = "com.fasterxml.jackson.annotation.JsonValue";
  private static final String JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";
  private static final String JSON_TYPE_INFO = "com.fasterxml.jackson.annotation.JsonTypeInfo";
  private static final String JSON_SUB_TYPES = "com.fasterxml.jackson.annotation.JsonSubTypes";
  private static final String JSON_IGNORE_PROPS =
      "com.fasterxml.jackson.annotation.JsonIgnoreProperties";
  private static final String JSON_ANY_SETTER = "com.fasterxml.jackson.annotation.JsonAnySetter";
  private static final String JSON_ANY_GETTER = "com.fasterxml.jackson.annotation.JsonAnyGetter";
  private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";
  private static final String NOT_NULL = "jakarta.validation.constraints.NotNull";
  private static final String VALID = "jakarta.validation.Valid";
  private static final String SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";
  private static final String GENERATED = "jakarta.annotation.Generated";

  /**
   * Static type-name overrides mirroring the deleted advanced execution's {@code <typeMappings>}.
   * Only entries that cannot be derived from the spec live here — currently just the
   * {@code OffsetDateTime} format mapping, which openapi-generator emits as a Java type but
   * which we want surfaced as {@code String} so downstream code can validate the format
   * manually. Filter-property aliases (e.g. {@code JobKeyFilterProperty} →
   * {@code BasicStringFilterProperty}) are detected structurally at load time, see
   * {@link #buildFilterPropertyAliases}.
   */
  private static final Map<String, String> STATIC_TYPE_OVERRIDES =
      Map.of("OffsetDateTime", "String");

  /**
   * Property-key fingerprint of {@code BasicStringFilter}: any polymorphic parent whose
   * "advanced" branch has exactly these properties is structurally a
   * {@code BasicStringFilterProperty}. The matching {@code AdvancedStringFilter} fingerprint
   * adds {@code $like}.
   */
  private static final Set<String> BASIC_STRING_FILTER_PROPS =
      Set.of("$eq", "$neq", "$exists", "$in", "$notIn");

  private static final Set<String> ADVANCED_STRING_FILTER_PROPS =
      Set.of("$eq", "$neq", "$exists", "$in", "$notIn", "$like");

  /** Filter-property schema names that must NOT be aliased back to themselves. */
  private static final Set<String> FILTER_PROPERTY_ALIAS_TARGETS =
      Set.of("BasicStringFilterProperty", "StringFilterProperty");

  private final Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();
  /** All allOf-$ref parent relationships; used for property/required inheritance lookup. */
  private final Map<String, String> parentOf = new LinkedHashMap<>();
  /** Subset of parentOf where the child has its own properties — produces a Java {@code extends}. */
  private final Map<String, String> javaParentOf = new LinkedHashMap<>();
  /** branch schema name -> ordered set of parent schema names whose oneOf list includes the branch. */
  private final Map<String, Set<String>> oneOfParentsOf = new LinkedHashMap<>();
  /**
   * Filter-property aliases discovered structurally at load time: maps a polymorphic parent like
   * {@code JobKeyFilterProperty} to its canonical equivalent {@code BasicStringFilterProperty}
   * when their "advanced" branches share the same property fingerprint.
   */
  private final Map<String, String> filterPropertyAliases = new LinkedHashMap<>();

  private final String licenseHeader;

  ModelGenerator(final String licenseHeader) {
    this.licenseHeader = licenseHeader;
  }

  public static void main(final String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Usage: ModelGenerator <specDir> <outDir> <licenseHeaderFile>");
    }
    final String header = renderHeader(Files.readString(Path.of(args[2])));
    final var gen = new ModelGenerator(header);
    gen.loadSpec(Path.of(args[0]));
    gen.buildRelations();
    gen.buildFilterPropertyAliases();
    gen.emitAll(Path.of(args[1]));
  }

  /** Wraps the raw header text in a Java block comment, matching the Spotless license format. */
  private static String renderHeader(final String raw) {
    final String trimmed = raw.endsWith("\n") ? raw.substring(0, raw.length() - 1) : raw;
    final StringBuilder sb = new StringBuilder("/*\n");
    for (final String line : trimmed.split("\n", -1)) {
      sb.append(" * ").append(line).append('\n');
    }
    sb.append(" */\n");
    return sb.toString();
  }

  // ============================================================
  // Spec loading
  // ============================================================

  @SuppressWarnings("unchecked")
  void loadSpec(final Path specDir) throws IOException {
    final var yaml = new Yaml();
    try (final Stream<Path> stream = Files.list(specDir)) {
      stream
          .filter(p -> p.toString().endsWith(".yaml"))
          .sorted()
          .forEach(
              p -> {
                try {
                  final Map<String, Object> doc = yaml.load(Files.readString(p));
                  if (doc == null) {
                    return;
                  }
                  final var components =
                      (Map<String, Object>) doc.getOrDefault("components", Map.of());
                  final var fileSchemas =
                      (Map<String, Object>) components.getOrDefault("schemas", Map.of());
                  fileSchemas.forEach(
                      (name, schema) -> {
                        if (schema instanceof Map) {
                          schemas.put(name, (Map<String, Object>) schema);
                        }
                      });
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  @SuppressWarnings("unchecked")
  void buildRelations() {
    schemas.forEach(
        (name, schema) -> {
          if (schema.get("allOf") instanceof List<?> list) {
            list.stream()
                .filter(Map.class::isInstance)
                .map(o -> (Map<String, Object>) o)
                .filter(m -> m.containsKey("$ref"))
                .findFirst()
                .ifPresent(m -> parentOf.put(name, refName((String) m.get("$ref"))));
          }
        });
    // Java extends mirrors parentOf 1:1: openapi-generator with REF_AS_PARENT_IN_ALLOF=true
    // emits Java inheritance for every allOf-$ref relationship, regardless of whether the child
    // declares its own properties. Children with no own props delegate fully to super(...).
    javaParentOf.putAll(parentOf);
    schemas.forEach(
        (parentName, schema) -> {
          // Only schemas with oneOf AND no scalar type emit as Java polymorphic interfaces; skip
          // primitive-with-oneOf schemas (e.g. ScopeKey: type=string, oneOf=[LongKey]).
          if (!isPolymorphicParent(schema)) {
            return;
          }
          if (schema.get("oneOf") instanceof List<?> list) {
            for (final var entry : list) {
              if (entry instanceof Map<?, ?> m && m.containsKey("$ref")) {
                final String branch = refName((String) m.get("$ref"));
                oneOfParentsOf
                    .computeIfAbsent(branch, k -> new LinkedHashSet<>())
                    .add(parentName);
              }
            }
          }
        });
  }

  /**
   * Detect polymorphic filter-property parents that are structurally equivalent to
   * {@code BasicStringFilterProperty} or {@code StringFilterProperty} and register them as
   * aliases. This replaces an otherwise-static list of ~18 explicit type mappings; if the spec
   * grows another {@code <Foo>KeyFilterProperty} with the same shape, no code change is needed.
   *
   * <p>Detection rule: the schema is a polymorphic parent with a single $ref oneOf branch (the
   * "advanced" filter class), and that class's own property keys equal exactly the canonical
   * {@code $eq/$neq/$exists/$in/$notIn} fingerprint (or that fingerprint plus {@code $like}).
   * The exact-match string branch is ignored — every filter-property schema carries one of those
   * and it does not affect the Java type the consumer sees.
   */
  @SuppressWarnings("unchecked")
  void buildFilterPropertyAliases() {
    schemas.forEach(
        (name, schema) -> {
          if (FILTER_PROPERTY_ALIAS_TARGETS.contains(name)) {
            return;
          }
          if (!isPolymorphicParent(schema)) {
            return;
          }
          if (!(schema.get("oneOf") instanceof List<?> branches)) {
            return;
          }
          for (final var entry : branches) {
            if (entry instanceof Map<?, ?> m && m.containsKey("$ref")) {
              final var advanced = schemas.get(refName((String) m.get("$ref")));
              if (advanced == null) {
                continue;
              }
              final Set<String> keys = ownPropertyKeys(advanced);
              // The property fingerprint matches BasicStringFilter on enum filters too
              // (IncidentErrorTypeFilterProperty etc.) — narrow further by requiring the
              // advanced branch's $eq to resolve to String. Enum-typed filters resolve to the
              // enum class instead and should keep their own interface type.
              if (!filterValueIsString(advanced)) {
                continue;
              }
              if (BASIC_STRING_FILTER_PROPS.equals(keys)) {
                filterPropertyAliases.put(name, "BasicStringFilterProperty");
                return;
              }
              if (ADVANCED_STRING_FILTER_PROPS.equals(keys)) {
                filterPropertyAliases.put(name, "StringFilterProperty");
                return;
              }
            }
          }
        });
  }

  @SuppressWarnings("unchecked")
  private static Set<String> ownPropertyKeys(final Map<String, Object> schema) {
    final Set<String> out = new LinkedHashSet<>();
    if (schema.get("properties") instanceof Map<?, ?> top) {
      top.keySet().forEach(k -> out.add((String) k));
    }
    return out;
  }

  /** Whether {@code advanced.$eq} resolves to {@code String} — distinguishes string from enum filters. */
  @SuppressWarnings("unchecked")
  private boolean filterValueIsString(final Map<String, Object> advanced) {
    if (!(advanced.get("properties") instanceof Map<?, ?> props)) {
      return false;
    }
    final Object eq = props.get("$eq");
    if (!(eq instanceof Map<?, ?> eqProp)) {
      return false;
    }
    return "String".equals(javaType((Map<String, Object>) eqProp));
  }

  // ============================================================
  // Emission entry point
  // ============================================================

  void emitAll(final Path outDir) throws IOException {
    Files.createDirectories(outDir);
    int emitted = 0;
    for (final var entry : schemas.entrySet()) {
      final var name = entry.getKey();
      final var schema = entry.getValue();
      final JavaSource<?> source = build(name, schema);
      if (source == null) {
        continue;
      }
      Files.writeString(outDir.resolve(name + ".java"), licenseHeader + source);
      emitted++;
    }
    System.out.printf("[ModelGenerator] emitted %d files to %s%n", emitted, outDir);
  }

  private JavaSource<?> build(final String name, final Map<String, Object> schema) {
    if (isEnum(schema)) {
      return buildEnum(name, schema);
    }
    if (isPolymorphicParent(schema)) {
      return buildInterface(name, schema);
    }
    return buildClass(name, schema);
  }

  // ============================================================
  // Schema classification
  // ============================================================

  private boolean isEnum(final Map<String, Object> s) {
    return s.get("enum") instanceof List<?> && s.get("type") instanceof String;
  }

  private boolean isPolymorphicParent(final Map<String, Object> s) {
    if (!(s.get("oneOf") instanceof List<?>)) {
      return false;
    }
    final Object type = s.get("type");
    return type == null || "object".equals(type);
  }

  // ============================================================
  // Enum emission
  // ============================================================

  @SuppressWarnings("unchecked")
  private JavaEnumSource buildEnum(final String name, final Map<String, Object> schema) {
    final JavaEnumSource e = Roaster.create(JavaEnumSource.class).setPackage(PKG).setName(name);
    addStandardImports(e);
    final String description = stringOrNull(schema.get("description"));
    if (description != null) {
      e.getJavaDoc().setText(description);
    }
    addGeneratedAnnotation(e);
    final Set<String> parents = oneOfParentsOf.get(name);
    if (parents != null) {
      parents.forEach(e::addInterface);
    }
    final List<Object> values = (List<Object>) schema.get("enum");
    for (final var v : values) {
      e.addEnumConstant(enumConstant(String.valueOf(v)) + "(\"" + escapeJava(String.valueOf(v)) + "\")");
    }
    e.addField().setPrivate().setFinal(true).setName("value").setType(String.class);
    e.addMethod()
        .setConstructor(true)
        .setBody("this.value = value;")
        .addParameter("final String", "value");
    e.addMethod()
        .setPublic()
        .setName("getValue")
        .setReturnType(String.class)
        .setBody("return value;")
        .addAnnotation(JSON_VALUE);
    e.addMethod()
        .setPublic()
        .setName("toString")
        .setReturnType(String.class)
        .setBody("return String.valueOf(value);")
        .addAnnotation(Override.class);
    final MethodSource<?> fromValue =
        e.addMethod()
            .setPublic()
            .setStatic(true)
            .setName("fromValue")
            .setReturnType(name)
            .setBody(
                "for (final " + name + " b : " + name + ".values()) {\n"
                    + "  if (b.value.equalsIgnoreCase(value)) {\n"
                    + "    return b;\n"
                    + "  }\n"
                    + "}\n"
                    + "throw new IllegalArgumentException(\"Unexpected value '\" + value + \"'\");");
    fromValue.addParameter("final String", "value");
    fromValue.addAnnotation(JSON_CREATOR);
    return e;
  }

  // ============================================================
  // Polymorphic interface emission
  // ============================================================

  @SuppressWarnings("unchecked")
  private JavaInterfaceSource buildInterface(final String name, final Map<String, Object> schema) {
    final JavaInterfaceSource iface =
        Roaster.create(JavaInterfaceSource.class).setPackage(PKG).setName(name);
    addStandardImports(iface);
    final String description = stringOrNull(schema.get("description"));
    if (description != null) {
      iface.getJavaDoc().setText(description);
    }

    final Map<String, Object> discriminator =
        schema.get("discriminator") instanceof Map<?, ?>
            ? (Map<String, Object>) schema.get("discriminator")
            : null;
    if (discriminator != null) {
      final String prop = stringOrNull(discriminator.get("propertyName"));
      final Map<String, Object> mapping =
          discriminator.get("mapping") instanceof Map<?, ?>
              ? (Map<String, Object>) discriminator.get("mapping")
              : null;
      iface
          .addAnnotation(JSON_IGNORE_PROPS)
          .setStringValue("value", prop)
          .setLiteralValue("allowSetters", "true");
      iface
          .addAnnotation(JSON_TYPE_INFO)
          .setLiteralValue("use", "JsonTypeInfo.Id.NAME")
          .setLiteralValue("include", "JsonTypeInfo.As.PROPERTY")
          .setStringValue("property", prop)
          .setLiteralValue("visible", "true");
      if (mapping != null && !mapping.isEmpty()) {
        final StringBuilder subTypes = new StringBuilder("{");
        int i = 0;
        for (final var e : mapping.entrySet()) {
          if (i++ > 0) {
            subTypes.append(", ");
          }
          subTypes
              .append("@JsonSubTypes.Type(value = ")
              .append(refName((String) e.getValue()))
              .append(".class, name = \"")
              .append(escapeJava(e.getKey()))
              .append("\")");
        }
        subTypes.append("}");
        iface.addAnnotation(JSON_SUB_TYPES).setLiteralValue(subTypes.toString());
      }
      iface.addMethod().setPublic().setName("get" + capitalize(javaName(prop))).setReturnType(String.class).setBody(null);
    }
    addGeneratedAnnotation(iface);
    return iface;
  }

  // ============================================================
  // Class (POJO) emission
  // ============================================================

  private JavaClassSource buildClass(final String name, final Map<String, Object> schema) {
    final JavaClassSource cls =
        Roaster.create(JavaClassSource.class).setPackage(PKG).setName(name).setPublic();
    addStandardImports(cls);

    final String description = stringOrNull(schema.get("description"));
    if (description != null) {
      cls.getJavaDoc().setText(description);
    }
    cls.addAnnotation(JSON_INCLUDE).setLiteralValue("value", JSON_INCLUDE + ".Include.ALWAYS");
    cls.addAnnotation(NULL_MARKED);
    if (description != null) {
      cls.addAnnotation(SCHEMA)
          .setStringValue("name", name)
          .setStringValue("description", description);
    }
    addGeneratedAnnotation(cls);

    final String javaParent = javaParentOf.get(name);
    if (javaParent != null) {
      cls.setSuperType(javaParent);
    }
    final Set<String> ifaceNames = oneOfParentsOf.get(name);
    if (ifaceNames != null) {
      ifaceNames.forEach(cls::addInterface);
    }

    final Map<String, Map<String, Object>> ownProps = collectOwnProperties(schema);
    final List<String> required = mergedRequired(name);
    final Set<String> requiredSet = new LinkedHashSet<>(required);
    final boolean hasOwnOptional =
        ownProps.entrySet().stream().anyMatch(e -> !requiredSet.contains(e.getKey()));

    addInlineEnums(cls, ownProps);
    ownProps.forEach((p, ps) -> addField(cls, p, ps, requiredSet.contains(p)));
    addCtor(cls, name, ownProps, requiredSet, javaParent, hasOwnOptional);
    ownProps.forEach((p, ps) -> addAccessors(cls, name, p, ps, requiredSet.contains(p)));
    if (javaParent != null) {
      addInheritedFluentOverrides(cls, name, requiredSet);
    }
    if (Boolean.TRUE.equals(schema.get("additionalProperties"))) {
      addAdditionalPropertyAccessors(cls, name);
    }
    addEqualsHashCodeToString(cls, name, ownProps.keySet().stream().toList(), javaParent != null);
    cls.addNestedType(buildBuilder(name, ownProps, required, requiredSet));
    return cls;
  }

  private void addInlineEnums(
      final JavaClassSource cls, final Map<String, Map<String, Object>> ownProps) {
    for (final var e : ownProps.entrySet()) {
      final String fieldName = javaName(e.getKey());
      final Map<String, Object> ps = e.getValue();
      final Map<String, Object> enumHost = inlineEnumHost(ps);
      if (enumHost == null) {
        continue;
      }
      final JavaEnumSource inner = buildInlineEnum(capitalize(fieldName) + "Enum", enumHost);
      cls.addNestedType(inner).setPublic();
    }
  }

  @SuppressWarnings("unchecked")
  private JavaEnumSource buildInlineEnum(final String name, final Map<String, Object> schema) {
    final JavaEnumSource e = Roaster.create(JavaEnumSource.class).setName(name).setPublic();
    final String description = stringOrNull(schema.get("description"));
    if (description != null) {
      e.getJavaDoc().setText(description);
    }
    final List<Object> values = (List<Object>) schema.get("enum");
    for (final var v : values) {
      e.addEnumConstant(enumConstant(String.valueOf(v)) + "(\"" + escapeJava(String.valueOf(v)) + "\")");
    }
    e.addField().setPrivate().setFinal(true).setName("value").setType(String.class);
    e.addMethod()
        .setConstructor(true)
        .setBody("this.value = value;")
        .addParameter("final String", "value");
    e.addMethod()
        .setPublic()
        .setName("getValue")
        .setReturnType(String.class)
        .setBody("return value;")
        .addAnnotation(JSON_VALUE);
    e.addMethod()
        .setPublic()
        .setName("toString")
        .setReturnType(String.class)
        .setBody("return String.valueOf(value);")
        .addAnnotation(Override.class);
    final MethodSource<?> fromValue =
        e.addMethod()
            .setPublic()
            .setStatic(true)
            .setName("fromValue")
            .setReturnType(name)
            .setBody(
                "for (final " + name + " b : " + name + ".values()) {\n"
                    + "  if (b.value.equalsIgnoreCase(value)) {\n"
                    + "    return b;\n"
                    + "  }\n"
                    + "}\n"
                    + "throw new IllegalArgumentException(\"Unexpected value '\" + value + \"'\");");
    fromValue.addParameter("final String", "value");
    fromValue.addAnnotation(JSON_CREATOR);
    return e;
  }

  private void addField(
      final JavaClassSource cls,
      final String propName,
      final Map<String, Object> propSchema,
      final boolean required) {
    final String type = javaTypeForProperty(propSchema, javaName(propName));
    final String fieldName = javaName(propName);
    final boolean nullableFlag = isPropertyNullable(propSchema);
    final boolean isContainer = isContainerType(type);
    final String containerInit = nullableFlag ? null : containerDefault(type);
    final String specDefault = nullableFlag ? null : resolvedDefaultLiteral(propSchema, type);
    final String defaultInit = containerInit != null ? containerInit : specDefault;

    final FieldSource<JavaClassSource> field =
        cls.addField().setPrivate().setName(fieldName).setType(type);
    if (defaultInit != null) {
      field.setLiteralInitializer(defaultInit);
    }
    if (isContainer) {
      field.addAnnotation(VALID);
    }
    if ((!required || nullableFlag) && defaultInit == null) {
      field.addAnnotation(NULLABLE);
    }
  }

  private void addCtor(
      final JavaClassSource cls,
      final String name,
      final Map<String, Map<String, Object>> ownProps,
      final Set<String> requiredSet,
      final String javaParent,
      final boolean hasOwnOptional) {
    if (ownProps.isEmpty() && javaParent == null) {
      return;
    }
    final MethodSource<JavaClassSource> ctor =
        cls.addMethod().setConstructor(true).setProtected();
    ctor.getJavaDoc()
        .setText(
            "Sole construction path. Public callers go through "
                + name
                + ".Builder.create();\nJackson uses this ctor via @JsonCreator;"
                + " subclasses chain via super(...).");
    ctor.addAnnotation(JSON_CREATOR);

    final List<String> ctorArgs = allCtorArgNames(name);
    for (final String arg : ctorArgs) {
      final var prop = findProperty(name, arg);
      final String type = javaTypeForProperty(prop, javaName(arg));
      final boolean isReq = requiredSet.contains(arg);
      final boolean nullableFlag = isPropertyNullable(prop);
      final boolean hasDefault = hasDefaultInit(prop, type);
      final ParameterSource<?> p = ctor.addParameter(type, javaName(arg));
      p.addAnnotation(JSON_PROPERTY).setStringValue(arg);
      if ((!isReq || nullableFlag) && !hasDefault) {
        p.addAnnotation(NULLABLE);
      }
    }

    final StringBuilder body = new StringBuilder();
    if (javaParent != null) {
      final List<String> parentArgs = allCtorArgNames(javaParent);
      body.append("super(");
      for (int i = 0; i < parentArgs.size(); i++) {
        if (i > 0) {
          body.append(", ");
        }
        body.append(javaName(parentArgs.get(i)));
      }
      body.append(");\n");
    }
    final boolean useNullGuard = hasOwnOptional;
    for (final String arg : ownProps.keySet()) {
      final String fn = javaName(arg);
      if (useNullGuard) {
        body.append("if (").append(fn).append(" != null) this.").append(fn).append(" = ").append(fn).append(";\n");
      } else {
        body.append("this.").append(fn).append(" = ").append(fn).append(";\n");
      }
    }
    ctor.setBody(body.toString());
  }

  private void addAccessors(
      final JavaClassSource cls,
      final String enclosing,
      final String propName,
      final Map<String, Object> propSchema,
      final boolean required) {
    final String type = javaTypeForProperty(propSchema, javaName(propName));
    final String fieldName = javaName(propName);
    final String capitalized = capitalize(fieldName);
    final boolean isContainer = isContainerType(type);
    final boolean hasDefault = hasDefaultInit(propSchema, type);
    final String description = stringOrNull(propSchema.get("description"));

    if (!required) {
      // Fluent self-setter: x(value) → this
      final MethodSource<?> fluent =
          cls.addMethod()
              .setPublic()
              .setName(fieldName)
              .setReturnType(enclosing)
              .setBody("this." + fieldName + " = " + fieldName + ";\nreturn this;");
      final ParameterSource<?> fp = fluent.addParameter(type, fieldName);
      if (!hasDefault) {
        fp.addAnnotation(NULLABLE);
      }

      // addItem fluent for arrays.
      if (type.startsWith("List<") || type.startsWith("Set<")) {
        final String itemType = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'));
        final String collectionInit =
            type.startsWith("Set<") ? "new LinkedHashSet<>()" : "new ArrayList<>()";
        final MethodSource<?> add =
            cls.addMethod()
                .setPublic()
                .setName("add" + capitalized + "Item")
                .setReturnType(enclosing)
                .setBody(
                    "if (this." + fieldName + " == null) {\n"
                        + "  this." + fieldName + " = " + collectionInit + ";\n"
                        + "}\n"
                        + "this." + fieldName + ".add(" + fieldName + "Item);\n"
                        + "return this;");
        add.addParameter(itemType, fieldName + "Item");
      }
    }

    // Getter
    final MethodSource<?> getter =
        cls.addMethod()
            .setPublic()
            .setName("get" + capitalized)
            .setReturnType(type)
            .setBody("return " + fieldName + ";");
    if (description != null) {
      getter.getJavaDoc().setText(description);
    }
    if (required) {
      getter.addAnnotation(NOT_NULL);
    }
    final AnnotationSource<?> schemaAnno = getter.addAnnotation(SCHEMA).setStringValue("name", propName);
    if (description != null) {
      schemaAnno.setStringValue("description", description);
    }
    schemaAnno.setLiteralValue(
        "requiredMode",
        "Schema.RequiredMode." + (required ? "REQUIRED" : "NOT_REQUIRED"));
    getter.addAnnotation(JSON_PROPERTY).setStringValue(propName);
    if (!required && !hasDefault) {
      // NullAway treats method-level @Nullable as a nullable return; equivalent for our purposes
      // and avoids Roaster's lack of return-type annotation support.
      getter.addAnnotation(NULLABLE);
    }

    if (!required) {
      final MethodSource<?> setter =
          cls.addMethod()
              .setPublic()
              .setReturnTypeVoid()
              .setName("set" + capitalized)
              .setBody("this." + fieldName + " = " + fieldName + ";");
      setter.addAnnotation(JSON_PROPERTY).setStringValue(propName);
      final ParameterSource<?> sp = setter.addParameter(type, fieldName);
      if (!hasDefault) {
        sp.addAnnotation(NULLABLE);
      }
    }
  }

  private void addInheritedFluentOverrides(
      final JavaClassSource cls, final String name, final Set<String> requiredSet) {
    final List<String> parents = parentChain(name);
    final Set<String> seen = new LinkedHashSet<>();
    final Set<String> ownNames = collectOwnProperties(schemas.get(name)).keySet();
    for (final String pName : parents) {
      final Map<String, Map<String, Object>> pProps = collectOwnProperties(schemas.get(pName));
      for (final var entry : pProps.entrySet()) {
        final String prop = entry.getKey();
        if (requiredSet.contains(prop) || ownNames.contains(prop) || !seen.add(prop)) {
          continue;
        }
        final String type = javaTypeForProperty(entry.getValue(), javaName(prop));
        final String fn = javaName(prop);
        final String capitalized = capitalize(fn);
        final boolean hasDefault = hasDefaultInit(entry.getValue(), type);

        final MethodSource<?> fluent =
            cls.addMethod()
                .setPublic()
                .setName(fn)
                .setReturnType(name)
                .setBody("super." + fn + "(" + fn + ");\nreturn this;");
        final ParameterSource<?> p = fluent.addParameter(type, fn);
        if (!hasDefault) {
          p.addAnnotation(NULLABLE);
        }

        if (type.startsWith("List<") || type.startsWith("Set<")) {
          final String itemType = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'));
          final MethodSource<?> add =
              cls.addMethod()
                  .setPublic()
                  .setName("add" + capitalized + "Item")
                  .setReturnType(name)
                  .setBody("super.add" + capitalized + "Item(" + fn + "Item);\nreturn this;");
          add.addParameter(itemType, fn + "Item");
        }
      }
    }
  }

  private void addAdditionalPropertyAccessors(final JavaClassSource cls, final String enclosing) {
    final FieldSource<?> apField =
        cls.addField().setPrivate().setName("additionalProperties").setType("Map<String, Object>");
    apField.addAnnotation(NULLABLE);

    final MethodSource<?> setter =
        cls.addMethod()
            .setPublic()
            .setName("putAdditionalProperty")
            .setReturnType(enclosing)
            .setBody(
                "if (this.additionalProperties == null) {\n"
                    + "  this.additionalProperties = new HashMap<>();\n"
                    + "}\n"
                    + "this.additionalProperties.put(key, value);\n"
                    + "return this;");
    setter.addAnnotation(JSON_ANY_SETTER);
    setter.addParameter("final String", "key");
    setter.addParameter("final Object", "value").addAnnotation(NULLABLE);

    final MethodSource<?> getter =
        cls.addMethod()
            .setPublic()
            .setName("getAdditionalProperties")
            .setReturnType("Map<String, Object>")
            .setBody("return additionalProperties;");
    getter.addAnnotation(JSON_ANY_GETTER);
    getter.addAnnotation(NULLABLE);
  }

  private void addEqualsHashCodeToString(
      final JavaClassSource cls,
      final String name,
      final List<String> ownPropNames,
      final boolean hasParent) {
    final MethodSource<?> equals =
        cls.addMethod()
            .setPublic()
            .setName("equals")
            .setReturnType("boolean");
    equals.addAnnotation(Override.class);
    equals.addParameter("Object", "o").addAnnotation(NULLABLE);
    final StringBuilder eq = new StringBuilder();
    eq.append("if (this == o) {\n  return true;\n}\n")
        .append("if (o == null || getClass() != o.getClass()) {\n  return false;\n}\n");
    if (!ownPropNames.isEmpty()) {
      final String varName = decapitalize(name);
      eq.append(name).append(" ").append(varName).append(" = (").append(name).append(") o;\n");
      eq.append("return ");
      for (int i = 0; i < ownPropNames.size(); i++) {
        if (i > 0) {
          eq.append(" &&\n    ");
        }
        final String fn = javaName(ownPropNames.get(i));
        eq.append("Objects.equals(this.").append(fn).append(", ").append(varName).append(".").append(fn).append(")");
      }
      if (hasParent) {
        eq.append(" &&\n    super.equals(o)");
      }
      eq.append(";");
    } else {
      eq.append("return ").append(hasParent ? "super.equals(o)" : "true").append(";");
    }
    equals.setBody(eq.toString());

    final MethodSource<?> hash =
        cls.addMethod().setPublic().setName("hashCode").setReturnType("int");
    hash.addAnnotation(Override.class);
    final StringBuilder hb = new StringBuilder("return Objects.hash(");
    for (int i = 0; i < ownPropNames.size(); i++) {
      if (i > 0) {
        hb.append(", ");
      }
      hb.append(javaName(ownPropNames.get(i)));
    }
    if (hasParent) {
      if (!ownPropNames.isEmpty()) {
        hb.append(", ");
      }
      hb.append("super.hashCode()");
    }
    hb.append(");");
    hash.setBody(hb.toString());

    final MethodSource<?> toStr =
        cls.addMethod().setPublic().setName("toString").setReturnType(String.class);
    toStr.addAnnotation(Override.class);
    final StringBuilder ts = new StringBuilder();
    ts.append("StringBuilder sb = new StringBuilder();\n");
    ts.append("sb.append(\"class ").append(name).append(" {\\n\");\n");
    if (hasParent) {
      ts.append("sb.append(\"    \").append(toIndentedString(super.toString())).append(\"\\n\");\n");
    }
    for (final String prop : ownPropNames) {
      final String fn = javaName(prop);
      ts.append("sb.append(\"    ")
          .append(fn)
          .append(": \").append(toIndentedString(")
          .append(fn)
          .append(")).append(\"\\n\");\n");
    }
    ts.append("sb.append(\"}\");\nreturn sb.toString();");
    toStr.setBody(ts.toString());

    final MethodSource<?> indented =
        cls.addMethod()
            .setPrivate()
            .setName("toIndentedString")
            .setReturnType(String.class)
            .setBody("return o == null ? \"null\" : o.toString().replace(\"\\n\", \"\\n    \");");
    indented.addParameter("Object", "o").addAnnotation(NULLABLE);
  }

  // ============================================================
  // Builder emission (nested class)
  // ============================================================

  private JavaClassSource buildBuilder(
      final String name,
      final Map<String, Map<String, Object>> ownProps,
      final List<String> required,
      final Set<String> requiredSet) {
    final JavaClassSource builder =
        Roaster.create(JavaClassSource.class)
            .setName("Builder")
            .setPublic()
            .setStatic(true)
            .setFinal(true);
    builder.addMethod().setConstructor(true).setPrivate().setBody("");

    final String firstStage =
        required.isEmpty() ? "IBuild" : "I" + capitalize(javaName(required.get(0)));
    builder
        .addMethod()
        .setPublic()
        .setStatic(true)
        .setName("create")
        .setReturnType(firstStage)
        .setBody("return new Impl();");

    // Stage interfaces
    for (int i = 0; i < required.size(); i++) {
      final String prop = required.get(i);
      final var propSchema = findProperty(name, prop);
      final boolean isLast = i == required.size() - 1;
      final String next = isLast ? "IBuild" : "I" + capitalize(javaName(required.get(i + 1)));
      final JavaInterfaceSource stage =
          Roaster.create(JavaInterfaceSource.class)
              .setName("I" + capitalize(javaName(prop)))
              .setPublic();
      final String type = javaTypeForProperty(propSchema, javaName(prop));
      final boolean nullableFlag = isPropertyNullable(propSchema);
      final MethodSource<?> stageM = stage.addMethod().setName(javaName(prop)).setReturnType(next);
      final ParameterSource<?> sp = stageM.addParameter(type, javaName(prop));
      if (nullableFlag) {
        sp.addAnnotation(NULLABLE);
      }
      builder.addNestedType(stage);
    }

    // IBuild interface
    final JavaInterfaceSource iBuild =
        Roaster.create(JavaInterfaceSource.class).setName("IBuild").setPublic();
    final List<Map.Entry<String, Map<String, Object>>> parentOptional =
        javaParentOf.containsKey(name)
            ? collectParentOptionalProps(name, requiredSet)
            : List.of();
    for (final var opt : parentOptional) {
      final String type = javaTypeForProperty(opt.getValue(), javaName(opt.getKey()));
      final boolean hasDefault = hasDefaultInit(opt.getValue(), type);
      final MethodSource<?> m =
          iBuild.addMethod().setName(javaName(opt.getKey())).setReturnType("IBuild");
      final ParameterSource<?> p = m.addParameter(type, javaName(opt.getKey()));
      if (!hasDefault) {
        p.addAnnotation(NULLABLE);
      }
    }
    for (final var entry : ownProps.entrySet()) {
      if (requiredSet.contains(entry.getKey())) {
        continue;
      }
      final String type = javaTypeForProperty(entry.getValue(), javaName(entry.getKey()));
      final boolean hasDefault = hasDefaultInit(entry.getValue(), type);
      final MethodSource<?> m =
          iBuild.addMethod().setName(javaName(entry.getKey())).setReturnType("IBuild");
      final ParameterSource<?> p = m.addParameter(type, javaName(entry.getKey()));
      if (!hasDefault) {
        p.addAnnotation(NULLABLE);
      }
    }
    iBuild.addMethod().setName("build").setReturnType(name);
    builder.addNestedType(iBuild);

    // Impl class
    final JavaClassSource impl =
        Roaster.create(JavaClassSource.class)
            .setName("Impl")
            .setPackagePrivate()
            .setStatic(true)
            .setFinal(true);
    for (final String r : required) {
      impl.addInterface("I" + capitalize(javaName(r)));
    }
    impl.addInterface("IBuild");

    // Required fields (always @Nullable until set)
    for (final String r : required) {
      final var ps = findProperty(name, r);
      final String type = javaTypeForProperty(ps, javaName(r));
      impl.addField()
          .setPrivate()
          .setName(javaName(r))
          .setType(type)
          .addAnnotation(NULLABLE);
    }
    // Parent-optional fields
    for (final var opt : parentOptional) {
      final String type = javaTypeForProperty(opt.getValue(), javaName(opt.getKey()));
      final boolean hasDefault = hasDefaultInit(opt.getValue(), type);
      final FieldSource<?> f =
          impl.addField().setPrivate().setName(javaName(opt.getKey())).setType(type);
      if (!hasDefault) {
        f.addAnnotation(NULLABLE);
      }
    }
    // Own-optional fields
    for (final var entry : ownProps.entrySet()) {
      if (requiredSet.contains(entry.getKey())) {
        continue;
      }
      final String type = javaTypeForProperty(entry.getValue(), javaName(entry.getKey()));
      final boolean hasDefault = hasDefaultInit(entry.getValue(), type);
      final FieldSource<?> f =
          impl.addField().setPrivate().setName(javaName(entry.getKey())).setType(type);
      if (!hasDefault) {
        f.addAnnotation(NULLABLE);
      }
    }

    // Stage setters
    for (int i = 0; i < required.size(); i++) {
      final String r = required.get(i);
      final var ps = findProperty(name, r);
      final boolean isLast = i == required.size() - 1;
      final String next = isLast ? "IBuild" : "I" + capitalize(javaName(required.get(i + 1)));
      final String type = javaTypeForProperty(ps, javaName(r));
      final boolean nullableFlag = isPropertyNullable(ps);
      final MethodSource<?> m =
          impl.addMethod()
              .setPublic()
              .setName(javaName(r))
              .setReturnType(next)
              .setBody("this." + javaName(r) + " = " + javaName(r) + ";\nreturn this;");
      m.addAnnotation(Override.class);
      final ParameterSource<?> p = m.addParameter(type, javaName(r));
      if (nullableFlag) {
        p.addAnnotation(NULLABLE);
      }
    }
    // Parent-optional setters
    for (final var opt : parentOptional) {
      final String type = javaTypeForProperty(opt.getValue(), javaName(opt.getKey()));
      final boolean hasDefault = hasDefaultInit(opt.getValue(), type);
      final MethodSource<?> m =
          impl.addMethod()
              .setPublic()
              .setName(javaName(opt.getKey()))
              .setReturnType("IBuild")
              .setBody(
                  "this." + javaName(opt.getKey()) + " = " + javaName(opt.getKey()) + ";\nreturn this;");
      m.addAnnotation(Override.class);
      final ParameterSource<?> p = m.addParameter(type, javaName(opt.getKey()));
      if (!hasDefault) {
        p.addAnnotation(NULLABLE);
      }
    }
    // Own-optional setters
    for (final var entry : ownProps.entrySet()) {
      if (requiredSet.contains(entry.getKey())) {
        continue;
      }
      final String type = javaTypeForProperty(entry.getValue(), javaName(entry.getKey()));
      final boolean hasDefault = hasDefaultInit(entry.getValue(), type);
      final MethodSource<?> m =
          impl.addMethod()
              .setPublic()
              .setName(javaName(entry.getKey()))
              .setReturnType("IBuild")
              .setBody(
                  "this." + javaName(entry.getKey()) + " = " + javaName(entry.getKey()) + ";\nreturn this;");
      m.addAnnotation(Override.class);
      final ParameterSource<?> p = m.addParameter(type, javaName(entry.getKey()));
      if (!hasDefault) {
        p.addAnnotation(NULLABLE);
      }
    }

    // build()
    final List<String> ctorArgs = allCtorArgNames(name);
    final StringBuilder buildBody = new StringBuilder("return new ").append(name).append("(\n");
    for (int i = 0; i < ctorArgs.size(); i++) {
      buildBody.append("    ").append(javaName(ctorArgs.get(i)));
      buildBody.append(i == ctorArgs.size() - 1 ? "\n" : ",\n");
    }
    buildBody.append(");");
    final MethodSource<?> build =
        impl.addMethod()
            .setPublic()
            .setName("build")
            .setReturnType(name)
            .setBody(buildBody.toString());
    build.addAnnotation(Override.class);
    build
        .addAnnotation(SuppressWarnings.class)
        .setStringValue(
            "value", "NullAway"); // Stage interfaces guarantee all required fields were set.
    builder.addNestedType(impl);

    return builder;
  }

  // ============================================================
  // Imports + class-level annotations
  // ============================================================

  private static void addStandardImports(final JavaSource<?> source) {
    final String[] imports = {
      "java.net.URI",
      "java.util.Objects",
      "java.util.ArrayList",
      "java.util.Arrays",
      "java.util.HashMap",
      "java.util.LinkedHashSet",
      "java.util.List",
      "java.util.Map",
      "java.util.Set",
      "java.time.OffsetDateTime",
      "com.fasterxml.jackson.annotation.JsonProperty",
      "com.fasterxml.jackson.annotation.JsonCreator",
      "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
      "com.fasterxml.jackson.annotation.JsonSubTypes",
      "com.fasterxml.jackson.annotation.JsonTypeInfo",
      "com.fasterxml.jackson.annotation.JsonValue",
      "org.jspecify.annotations.Nullable",
      "jakarta.validation.Valid",
      "jakarta.validation.constraints.NotNull",
      "io.swagger.v3.oas.annotations.media.Schema",
      "jakarta.annotation.Generated",
    };
    for (final String i : imports) {
      source.addImport(i);
    }
  }

  private static void addGeneratedAnnotation(final JavaSource<?> source) {
    source
        .addAnnotation(GENERATED)
        .setStringValue("value", "io.camunda.gateway.protocol.model.tools.ModelGenerator")
        .setStringValue("comments", "Generated by ModelGenerator");
  }

  // ============================================================
  // Schema introspection helpers
  // ============================================================

  @SuppressWarnings("unchecked")
  private boolean hasOwnProperties(final Map<String, Object> schema) {
    if (schema.get("properties") instanceof Map<?, ?> p && !p.isEmpty()) {
      return true;
    }
    if (schema.get("allOf") instanceof List<?> list) {
      for (final var e : list) {
        if (e instanceof Map<?, ?> m && !m.containsKey("$ref")) {
          if (m.get("properties") instanceof Map<?, ?> p && !p.isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Own properties = top-level + inline allOf object entries (no parent-chain inlining). */
  private Map<String, Map<String, Object>> collectOwnProperties(
      final Map<String, Object> schema) {
    final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    addPropertiesFromSchema(schema, out);
    return out;
  }

  @SuppressWarnings("unchecked")
  private void addPropertiesFromSchema(
      final Map<String, Object> schema, final Map<String, Map<String, Object>> out) {
    if (schema.get("properties") instanceof Map<?, ?> top) {
      top.forEach(
          (k, v) -> {
            if (v instanceof Map<?, ?> m) {
              out.put((String) k, (Map<String, Object>) m);
            }
          });
    }
    if (schema.get("allOf") instanceof List<?> list) {
      for (final var e : list) {
        if (e instanceof Map<?, ?> m && !m.containsKey("$ref")) {
          if (m.get("properties") instanceof Map<?, ?> p) {
            p.forEach(
                (k, v) -> {
                  if (v instanceof Map<?, ?> ps) {
                    out.put((String) k, (Map<String, Object>) ps);
                  }
                });
          }
        }
      }
    }
  }

  /** Required list for a schema = parent.required (recursive) + own.required, dedup, spec order. */
  private List<String> mergedRequired(final String name) {
    final List<String> out = new ArrayList<>();
    final Set<String> seen = new LinkedHashSet<>();
    for (final String parent : parentChain(name)) {
      collectRequired(schemas.get(parent), out, seen);
    }
    collectRequired(schemas.get(name), out, seen);
    return out;
  }

  @SuppressWarnings("unchecked")
  private void collectRequired(
      final Map<String, Object> schema, final List<String> out, final Set<String> seen) {
    if (schema == null) {
      return;
    }
    if (schema.get("required") instanceof List<?> r) {
      for (final var o : r) {
        if (seen.add((String) o)) {
          out.add((String) o);
        }
      }
    }
    if (schema.get("allOf") instanceof List<?> list) {
      for (final var e : list) {
        if (e instanceof Map<?, ?> m && !m.containsKey("$ref")) {
          if (m.get("required") instanceof List<?> rr) {
            for (final var o : rr) {
              if (seen.add((String) o)) {
                out.add((String) o);
              }
            }
          }
        }
      }
    }
  }

  /** Walk the full allOf-$ref parent chain (root-most first). */
  private List<String> parentChain(final String name) {
    final List<String> chain = new ArrayList<>();
    String cur = parentOf.get(name);
    while (cur != null) {
      chain.add(0, cur);
      cur = parentOf.get(cur);
    }
    return chain;
  }

  /**
   * Order: own props (declaration order) first, then parent's allCtorArgs recursively. Mirrors
   * the deleted EnrichSpec.allCtorArgNames closure.
   */
  private List<String> allCtorArgNames(final String name) {
    final List<String> out = new ArrayList<>();
    final Set<String> seen = new LinkedHashSet<>();
    final var schema = schemas.get(name);
    if (schema != null) {
      addPropNamesFromSchema(schema, out, seen);
    }
    final String parent = parentOf.get(name);
    if (parent != null) {
      for (final String p : allCtorArgNames(parent)) {
        if (seen.add(p)) {
          out.add(p);
        }
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private void addPropNamesFromSchema(
      final Map<String, Object> schema, final List<String> out, final Set<String> seen) {
    if (schema.get("properties") instanceof Map<?, ?> top) {
      for (final var k : top.keySet()) {
        if (seen.add((String) k)) {
          out.add((String) k);
        }
      }
    }
    if (schema.get("allOf") instanceof List<?> list) {
      for (final var e : list) {
        if (e instanceof Map<?, ?> m && !m.containsKey("$ref")) {
          if (m.get("properties") instanceof Map<?, ?> p) {
            for (final var k : p.keySet()) {
              if (seen.add((String) k)) {
                out.add((String) k);
              }
            }
          }
        }
      }
    }
  }

  /** Find a property's schema fragment by name; walks parent chain to find inherited properties. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> findProperty(final String schemaName, final String propName) {
    final var s = schemas.get(schemaName);
    if (s == null) {
      throw new IllegalStateException("Unknown schema " + schemaName);
    }
    if (s.get("properties") instanceof Map<?, ?> top && top.containsKey(propName)) {
      return (Map<String, Object>) top.get(propName);
    }
    if (s.get("allOf") instanceof List<?> list) {
      for (final var e : list) {
        if (e instanceof Map<?, ?> m && !m.containsKey("$ref")) {
          if (m.get("properties") instanceof Map<?, ?> p && p.containsKey(propName)) {
            return (Map<String, Object>) p.get(propName);
          }
        }
      }
    }
    final String parent = parentOf.get(schemaName);
    if (parent != null) {
      return findProperty(parent, propName);
    }
    throw new IllegalStateException("Property " + propName + " not found on " + schemaName);
  }

  /** Parent-chain optional properties not redeclared as required by this schema. */
  private List<Map.Entry<String, Map<String, Object>>> collectParentOptionalProps(
      final String name, final Set<String> requiredSet) {
    final List<Map.Entry<String, Map<String, Object>>> out = new ArrayList<>();
    final Set<String> seen = new LinkedHashSet<>();
    for (final String pName : parentChain(name)) {
      final var ps = schemas.get(pName);
      if (ps == null) {
        continue;
      }
      final Map<String, Map<String, Object>> pProps = new LinkedHashMap<>();
      addPropertiesFromSchema(ps, pProps);
      for (final var e : pProps.entrySet()) {
        if (requiredSet.contains(e.getKey()) || !seen.add(e.getKey())) {
          continue;
        }
        out.add(e);
      }
    }
    return out;
  }

  // ============================================================
  // Type resolution
  // ============================================================

  @SuppressWarnings("unchecked")
  String javaType(final Map<String, Object> fragment) {
    if (fragment == null) {
      return "Object";
    }
    if (fragment.get("$ref") instanceof String ref) {
      return resolveRef(refName(ref));
    }
    if (fragment.get("allOf") instanceof List<?> list) {
      final var first = list.stream()
          .filter(Map.class::isInstance)
          .map(o -> (Map<String, Object>) o)
          .filter(m -> m.containsKey("$ref"))
          .findFirst();
      if (first.isPresent()) {
        return resolveRef(refName((String) first.get().get("$ref")));
      }
    }
    if (fragment.get("oneOf") instanceof List<?> list) {
      final var first = list.stream()
          .filter(Map.class::isInstance)
          .map(o -> (Map<String, Object>) o)
          .filter(m -> m.containsKey("$ref"))
          .findFirst();
      if (first.isPresent()) {
        return resolveRef(refName((String) first.get().get("$ref")));
      }
    }
    final String type = stringOrEmpty(fragment.get("type"));
    return switch (type) {
      case "string" -> {
        final Object fmt = fragment.get("format");
        if ("date-time".equals(fmt)) {
          yield STATIC_TYPE_OVERRIDES.getOrDefault("OffsetDateTime", "OffsetDateTime");
        }
        if ("uri".equals(fmt)) {
          yield "URI";
        }
        yield "String";
      }
      case "integer" -> "int64".equals(fragment.get("format")) ? "Long" : "Integer";
      case "number" -> "float".equals(fragment.get("format")) ? "Float" : "Double";
      case "boolean" -> "Boolean";
      case "array" -> {
        final String collection = Boolean.TRUE.equals(fragment.get("uniqueItems")) ? "Set" : "List";
        yield collection + "<" + javaType((Map<String, Object>) fragment.get("items")) + ">";
      }
      case "object" -> {
        if (fragment.get("additionalProperties") instanceof Map<?, ?> ap) {
          yield "Map<String, " + javaType((Map<String, Object>) ap) + ">";
        }
        if (Boolean.TRUE.equals(fragment.get("additionalProperties"))) {
          yield "Map<String, Object>";
        }
        yield "Object";
      }
      default -> "Object";
    };
  }

  @SuppressWarnings("unchecked")
  private String resolveRef(final String rn) {
    if (STATIC_TYPE_OVERRIDES.containsKey(rn)) {
      return STATIC_TYPE_OVERRIDES.get(rn);
    }
    if (filterPropertyAliases.containsKey(rn)) {
      return filterPropertyAliases.get(rn);
    }
    final var rs = schemas.get(rn);
    if (rs != null) {
      if (rs.get("enum") != null) {
        return rn;
      }
      if ("object".equals(rs.get("type"))) {
        return rn;
      }
      if (rs.get("allOf") != null && rs.get("type") == null) {
        return rn;
      }
      if (rs.get("oneOf") instanceof List<?> oneOfList && rs.get("type") == null) {
        // openapi-generator with REF_AS_PARENT_IN_ALLOF=true collapses a oneOf wrapper that has
        // exactly one $ref entry to that branch (treats it as an alias rather than a true union).
        final List<Map<String, Object>> refs =
            oneOfList.stream()
                .filter(Map.class::isInstance)
                .map(o -> (Map<String, Object>) o)
                .filter(m -> m.containsKey("$ref"))
                .toList();
        if (refs.size() == 1 && oneOfList.size() == 1) {
          return resolveRef(refName((String) refs.get(0).get("$ref")));
        }
        return rn;
      }
      final String resolved = javaType(rs);
      if (!"Object".equals(resolved)) {
        return resolved;
      }
    }
    return rn;
  }

  /** Type for a property; handles inline enum strings (uses {@code <PropName>Enum} naming). */
  @SuppressWarnings("unchecked")
  String javaTypeForProperty(final Map<String, Object> propSchema, final String javaFieldName) {
    if (propSchema == null) {
      return "Object";
    }
    if ("string".equals(propSchema.get("type")) && propSchema.get("enum") != null) {
      return capitalize(javaFieldName) + "Enum";
    }
    if ("array".equals(propSchema.get("type")) && propSchema.get("items") instanceof Map<?, ?> items) {
      final var im = (Map<String, Object>) items;
      if ("string".equals(im.get("type")) && im.get("enum") != null) {
        return "List<" + capitalize(javaFieldName) + "Enum>";
      }
      if (im.get("$ref") instanceof String ref) {
        final String rn = refName(ref);
        final var rs = schemas.get(rn);
        if (rs != null && rs.get("enum") != null) {
          return "List<" + rn + ">";
        }
      }
    }
    return javaType(propSchema);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> inlineEnumHost(final Map<String, Object> propSchema) {
    if ("string".equals(propSchema.get("type")) && propSchema.get("enum") instanceof List<?>) {
      return propSchema;
    }
    if ("array".equals(propSchema.get("type")) && propSchema.get("items") instanceof Map<?, ?> items) {
      final var im = (Map<String, Object>) items;
      if ("string".equals(im.get("type")) && im.get("enum") instanceof List<?>) {
        return im;
      }
    }
    return null;
  }

  // ============================================================
  // Defaults + nullability
  // ============================================================

  /**
   * Default initializer for a property: own {@code default:} first, then any allOf $ref's
   * {@code default:}, then any direct $ref's {@code default:}. Enums get an
   * {@code EnumName.CONSTANT} initializer; primitives get a literal.
   */
  private String resolvedDefaultLiteral(final Map<String, Object> propSchema, final String type) {
    if (propSchema == null) {
      return null;
    }
    Object def = propSchema.get("default");
    if (def == null) {
      def = defaultFromAllOfRef(propSchema);
    }
    if (def == null && propSchema.get("$ref") instanceof String ref) {
      final var rs = schemas.get(refName(ref));
      if (rs != null) {
        def = rs.get("default");
      }
    }
    if (def == null) {
      return null;
    }
    final String prim = primitiveDefaultLiteral(type, def);
    if (prim != null) {
      return prim;
    }
    if (schemas.get(type) != null && schemas.get(type).get("enum") != null) {
      return type + "." + enumConstant(def.toString());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Object defaultFromAllOfRef(final Map<String, Object> propSchema) {
    if (propSchema.get("allOf") instanceof List<?> list) {
      for (final var e : list) {
        if (e instanceof Map<?, ?> m && m.containsKey("$ref")) {
          final var rs = schemas.get(refName((String) m.get("$ref")));
          if (rs != null && rs.get("default") != null) {
            return rs.get("default");
          }
        }
      }
    }
    return null;
  }

  private static String primitiveDefaultLiteral(final String type, final Object def) {
    if (def == null) {
      return null;
    }
    return switch (type) {
      case "Boolean" -> Boolean.TRUE.equals(def) ? "true" : "false";
      case "Integer" -> def.toString();
      case "Long" -> def + "L";
      case "Float" -> def + "f";
      case "Double" -> def.toString();
      case "String" -> "\"" + escapeJava(def.toString()) + "\"";
      default -> null;
    };
  }

  private boolean hasDefaultInit(final Map<String, Object> propSchema, final String type) {
    if (propSchema == null) {
      return isContainerType(type);
    }
    if (isPropertyNullable(propSchema)) {
      return false;
    }
    if (isContainerType(type)) {
      return true;
    }
    return resolvedDefaultLiteral(propSchema, type) != null;
  }

  private static boolean isPropertyNullable(final Map<String, Object> propSchema) {
    return propSchema != null && Boolean.TRUE.equals(propSchema.get("nullable"));
  }

  private static boolean isContainerType(final String type) {
    return type.startsWith("List<") || type.startsWith("Set<") || type.startsWith("Map<");
  }

  private static String containerDefault(final String type) {
    if (type.startsWith("List<")) {
      return "new ArrayList<>()";
    }
    if (type.startsWith("Set<")) {
      return "new LinkedHashSet<>()";
    }
    if (type.startsWith("Map<")) {
      return "new HashMap<>()";
    }
    return null;
  }

  // ============================================================
  // Naming / escaping
  // ============================================================

  private static String refName(final String ref) {
    final int idx = ref.lastIndexOf('/');
    return idx >= 0 ? ref.substring(idx + 1) : ref;
  }

  private static String javaName(final String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    if (s.indexOf('.') < 0 && s.indexOf('-') < 0 && s.indexOf('_') < 0) {
      return s;
    }
    final String[] parts = s.split("[.\\-_]");
    final StringBuilder out = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      final String p = parts[i];
      if (p.isEmpty()) {
        continue;
      }
      out.append(Character.toUpperCase(p.charAt(0)));
      if (p.length() > 1) {
        out.append(p.substring(1));
      }
    }
    return out.toString();
  }

  private static String capitalize(final String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    final String n = s.startsWith("$") ? s.substring(1) : s;
    final String head = n.isEmpty() ? "" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    return s.startsWith("$") ? "$" + head : head;
  }

  private static String decapitalize(final String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * Convert an enum value to its UPPER_SNAKE_CASE Java constant name. Mirrors openapi-generator's
   * behaviour: splits camelCase boundaries with underscores, then upper-cases and replaces any
   * remaining non-alphanumeric runs with a single underscore. Examples:
   * "tenantId" -> "TENANT_ID", "processInstanceKey" -> "PROCESS_INSTANCE_KEY",
   * "BPMN_PROCESS_ID" -> "BPMN_PROCESS_ID", "ASC" -> "ASC".
   */
  private static String enumConstant(final String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (i > 0 && Character.isUpperCase(c)) {
        final char prev = s.charAt(i - 1);
        if (Character.isLowerCase(prev)
            || (i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1)))) {
          sb.append('_');
        }
      }
      sb.append(c);
    }
    return sb.toString().toUpperCase().replaceAll("[^A-Z0-9_]+", "_").replaceAll("_+", "_");
  }

  private static String stringOrNull(final Object o) {
    return o instanceof String s ? s : null;
  }

  private static String stringOrEmpty(final Object o) {
    return o instanceof String s ? s : "";
  }

  private static String escapeJava(final String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }
}
