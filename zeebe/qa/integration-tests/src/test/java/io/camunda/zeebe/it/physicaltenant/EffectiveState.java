/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.util.unit.DataSize;

/**
 * Reads the effective state of a running application: walks the object graph reachable from a set
 * of roots (for example every Spring singleton) breadth first and reports the values held in it,
 * each with the shortest field path that reaches it. Objects of the JDK are only entered through
 * their public API (collections, maps, optionals, atomics, thread pools); every other object is
 * entered field by field.
 *
 * <p>It answers two questions: where in the graph a given value is held ({@link #find}), and which
 * leaf values a sub-graph holds ({@link #leaves}).
 */
final class EffectiveState {

  private static final int MAX_PRIMITIVE_ARRAY_SCAN = 1 << 14;
  private static final int MAX_STRING_LENGTH = 200;
  private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

  private EffectiveState() {}

  /**
   * Finds every place in the graph that holds one of the given values.
   *
   * @param roots the roots of the walk, by label
   * @param tokens a token of a value (see {@link #tokensOf}) mapped to the name of what it marks
   * @param strings substrings mapped to the name of what they mark, found in any string
   * @param stop objects that are not entered
   */
  static List<Hit> find(
      final Map<String, Object> roots,
      final Map<String, String> tokens,
      final Map<String, String> strings,
      final Predicate<Object> stop) {
    final List<Hit> hits = new ArrayList<>();
    walk(
        roots,
        stop,
        (node, value) -> {
          for (final var token : tokensOf(value)) {
            final var marked = tokens.get(token);
            if (marked != null) {
              hits.add(new Hit(marked, node.path(), node.consumer(), String.valueOf(value)));
            }
          }
          if (value instanceof final String string) {
            strings.forEach(
                (substring, marked) -> {
                  if (string.contains(substring)) {
                    hits.add(new Hit(marked, node.path(), node.consumer(), abbreviate(string)));
                  }
                });
          }
        });
    return hits;
  }

  /**
   * The leaf values held for each tenant: a leaf belongs to a tenant when the path that reaches it
   * names the tenant, for example a map entry keyed by the tenant id or a partition named after it.
   *
   * @return per tenant, the values each consumer (class and field) holds, and a path to each
   */
  static Map<String, Map<String, Attributed>> attributed(
      final Map<String, Object> roots,
      final Set<String> tenants,
      final Predicate<Object> stop,
      final Predicate<String> ignoredPath) {
    final Map<String, Map<String, Attributed>> byTenant = new HashMap<>();
    tenants.forEach(tenant -> byTenant.put(tenant, new TreeMap<>()));
    walk(
        roots,
        stop,
        (node, value) -> {
          if (node.tenant() == null) {
            return;
          }
          final var path = node.path();
          if (ignoredPath.test(path)) {
            return;
          }
          final var attributed =
              byTenant
                  .get(node.tenant())
                  .computeIfAbsent(
                      node.consumer(), consumer -> new Attributed(new ArrayList<>(), path));
          attributed.values().add(abbreviate(String.valueOf(value)));
        },
        tenants);
    return byTenant;
  }

  /** The tokens a leaf value can be recognised by, in every unit a consumer may keep it in. */
  static List<String> tokensOf(final Object value) {
    return switch (value) {
      case final Duration duration -> {
        final List<String> tokens = new ArrayList<>(3);
        tokens.add("n:" + duration.toMillis());
        try {
          tokens.add("n:" + duration.toNanos());
          tokens.add("n:" + duration.toNanos() / 1000);
        } catch (final ArithmeticException ignored) {
          // too long to be a sentinel
        }
        yield tokens;
      }
      case final DataSize size -> List.of("n:" + size.toBytes());
      case final Byte b -> List.of("n:" + b);
      case final Short s -> List.of("n:" + s);
      case final Integer i -> List.of("n:" + i);
      case final Long l -> List.of("n:" + l);
      case final Float f -> List.of("d:" + f.doubleValue());
      case final Double d -> List.of("d:" + d);
      default -> List.of();
    };
  }

  private static void walk(
      final Map<String, Object> roots, final Predicate<Object> stop, final LeafVisitor visitor) {
    walk(roots, stop, visitor, Set.of());
  }

  private static void walk(
      final Map<String, Object> roots,
      final Predicate<Object> stop,
      final LeafVisitor visitor,
      final Set<String> tenants) {
    final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    final ArrayDeque<Pending> queue = new ArrayDeque<>();
    roots.forEach(
        (label, root) -> {
          if (root != null) {
            queue.add(new Pending(root, Node.root(label, tenants)));
          }
        });
    while (!queue.isEmpty()) {
      final var pending = queue.poll();
      final var object = pending.object();
      if (isLeaf(object)) {
        final var value = leafValue(object);
        if (value != null) {
          visitor.visit(pending.node(), value);
        }
        continue;
      }
      if (!visited.add(object) || stop.test(object)) {
        continue;
      }
      try {
        expand(object, pending.node(), queue, visitor);
      } catch (final RuntimeException | LinkageError ignored) {
        // a concurrently modified collection or an object that cannot be read: skip it
      }
    }
  }

  private static void expand(
      final Object object,
      final Node node,
      final ArrayDeque<Pending> queue,
      final LeafVisitor visitor) {
    final var type = object.getClass();
    switch (object) {
      case final Map<?, ?> map -> {
        for (final var entry : new ArrayList<>(map.entrySet())) {
          final var key = entry.getKey();
          if (key == null || isLeaf(key)) {
            enqueue(
                queue, entry.getValue(), node.child("[" + abbreviate(String.valueOf(key)) + "]"));
          } else {
            enqueue(queue, key, node.child("{key}"));
            enqueue(queue, entry.getValue(), node.child("{value}"));
          }
        }
        return;
      }
      case final Collection<?> collection -> {
        for (final var element : new ArrayList<>(collection)) {
          enqueue(queue, element, node.child("[*]"));
        }
        return;
      }
      case final Optional<?> optional -> {
        optional.ifPresent(value -> enqueue(queue, value, node.child(".get()")));
        return;
      }
      case final AtomicReference<?> reference -> {
        enqueue(queue, reference.get(), node.child(".get()"));
        return;
      }
      case final ThreadPoolExecutor executor -> {
        visitor.visit(node.child(".corePoolSize"), executor.getCorePoolSize());
        visitor.visit(node.child(".maximumPoolSize"), executor.getMaximumPoolSize());
        return;
      }
      default -> {}
    }
    if (type.isArray()) {
      expandArray(object, node, queue, visitor);
      return;
    }
    for (final var field : fieldsOf(type)) {
      final Object value;
      try {
        value = field.get(object);
      } catch (final IllegalAccessException | RuntimeException e) {
        continue;
      }
      if (value != null) {
        enqueue(queue, value, node.child("." + field.getName(), type));
      }
    }
  }

  private static void expandArray(
      final Object array,
      final Node node,
      final ArrayDeque<Pending> queue,
      final LeafVisitor visitor) {
    final var componentType = array.getClass().getComponentType();
    if (componentType == byte.class
        || componentType == char.class
        || componentType == boolean.class) {
      return;
    }
    final int length = Math.min(Array.getLength(array), MAX_PRIMITIVE_ARRAY_SCAN);
    final var element = node.child("[*]");
    if (componentType.isPrimitive()) {
      for (int i = 0; i < length; i++) {
        visitor.visit(element, Array.get(array, i));
      }
    } else {
      for (int i = 0; i < length; i++) {
        enqueue(queue, Array.get(array, i), element);
      }
    }
  }

  private static void enqueue(
      final ArrayDeque<Pending> queue, final Object value, final Node node) {
    if (value != null) {
      queue.add(new Pending(value, node));
    }
  }

  private static boolean isLeaf(final Object object) {
    return object instanceof Number
        || object instanceof CharSequence
        || object instanceof Boolean
        || object instanceof Character
        || object instanceof Enum<?>
        || object instanceof Duration
        || object instanceof DataSize
        || object instanceof Path
        || object instanceof File
        || object instanceof URI
        || object instanceof URL
        || object instanceof Pattern
        || object instanceof AtomicBoolean
        || object instanceof Class<?>;
  }

  private static Object leafValue(final Object object) {
    return switch (object) {
      case final AtomicInteger atomic -> atomic.get();
      case final AtomicLong atomic -> atomic.get();
      case final AtomicBoolean atomic -> atomic.get();
      case final CharSequence chars -> chars.toString();
      case final Enum<?> constant -> constant.getDeclaringClass().getSimpleName() + "." + constant;
      case final Pattern pattern -> pattern.pattern();
      case final Class<?> type -> "class " + type.getName();
      case final Path path -> path.toString();
      case final File file -> file.toString();
      default -> object;
    };
  }

  private static List<Field> fieldsOf(final Class<?> type) {
    return FIELDS.computeIfAbsent(type, EffectiveState::readableFields);
  }

  private static List<Field> readableFields(final Class<?> type) {
    final List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      if (isJdk(current)) {
        break;
      }
      for (final var field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if (trySetAccessible(field)) {
          fields.add(field);
        }
      }
    }
    return List.copyOf(fields);
  }

  private static boolean trySetAccessible(final AccessibleObject member) {
    try {
      return member.trySetAccessible();
    } catch (final RuntimeException e) {
      return false;
    }
  }

  private static boolean isJdk(final Class<?> type) {
    final var name = type.getName();
    return name.startsWith("java.")
        || name.startsWith("javax.")
        || name.startsWith("jdk.")
        || name.startsWith("sun.")
        || name.startsWith("com.sun.");
  }

  static String abbreviate(final String value) {
    return value.length() <= MAX_STRING_LENGTH
        ? value
        : value.substring(0, MAX_STRING_LENGTH) + "...(" + value.length() + ")";
  }

  @FunctionalInterface
  private interface LeafVisitor {
    void visit(Node node, Object value);
  }

  /** A value found in the graph: what it marks, where, and in which class's field. */
  record Hit(String marked, String path, String consumer, String value) {}

  record Attributed(List<String> values, String examplePath) {}

  private record Pending(Object object, Node node) {}

  private record Node(
      Node parent,
      String segment,
      Class<?> owner,
      String field,
      String tenant,
      Set<String> tenants) {

    static Node root(final String label, final Set<String> tenants) {
      return new Node(null, label, null, "", tenantOf(label, null, tenants), tenants);
    }

    Node child(final String segment) {
      return new Node(this, segment, owner, field, tenantOf(segment, tenant, tenants), tenants);
    }

    Node child(final String segment, final Class<?> owner) {
      return new Node(this, segment, owner, segment, tenantOf(segment, tenant, tenants), tenants);
    }

    private static String tenantOf(
        final String segment, final String inherited, final Set<String> tenants) {
      if (inherited != null) {
        return inherited;
      }
      for (final var tenant : tenants) {
        if (segment.contains(tenant)) {
          return tenant;
        }
      }
      return null;
    }

    String path() {
      final var segments = new ArrayDeque<String>();
      for (Node node = this; node != null; node = node.parent) {
        segments.push(node.segment);
      }
      return String.join("", segments);
    }

    /** The class and field that hold the value, the same for every instance of the consumer. */
    String consumer() {
      final var name =
          owner == null ? "" : owner.getName().replaceAll("\\$\\$Lambda.*", "\\$\\$Lambda");
      return name + field;
    }
  }
}
