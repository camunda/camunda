/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * Scans a SECRET_REFERENCE cluster variable's msgpack value for {@code camunda.secrets.<name>}
 * occurrences, walking every string leaf of the value (the root scalar, object field values, and
 * array elements) and recording each distinct reference name together with the RFC 6901 JSON
 * pointer of the leaf it was found in.
 *
 * <p>This only detects references; resolving them into activated job variables is a separate, later
 * concern.
 */
public final class ClusterVariableSecretReferenceScanner {

  /** Reads the msgpack encoding of a cluster variable value as a Jackson tree. */
  private final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

  /**
   * Scans the msgpack value buffer, returning every secret reference with its leaf's JSON pointer.
   * The value is valid msgpack by construction (it comes from a record already round-tripped
   * through the protocol), so the parse failure this guards against is very likely unreachable —
   * but a processor must never throw, so it is reported as a rejection rather than propagated.
   */
  public Either<Rejection, List<DetectedReference>> scan(final DirectBuffer valueBuffer) {
    final JsonNode root;
    try {
      root = mapper.readTree(new DirectBufferInputStream(valueBuffer));
    } catch (final IOException e) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to detect secret references in the cluster variable value, but the value "
                  + "could not be parsed: "
                  + e.getMessage()));
    }
    final List<DetectedReference> references = new ArrayList<>();
    collect(root, new StringBuilder(), references);
    return Either.right(references);
  }

  private void collect(
      final JsonNode node, final StringBuilder pointer, final List<DetectedReference> out) {
    if (node.isTextual()) {
      collectFromLeaf(node.textValue(), pointer.toString(), out);
    } else if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              field -> {
                final int length = pointer.length();
                pointer.append('/').append(escape(field.getKey()));
                collect(field.getValue(), pointer, out);
                pointer.setLength(length);
              });
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        final int length = pointer.length();
        pointer.append('/').append(i);
        collect(node.get(i), pointer, out);
        pointer.setLength(length);
      }
    }
    // number/bool/null/missing leaves cannot carry a reference
  }

  /** Dedupes repeated occurrences of the same reference within a single leaf. */
  private static void collectFromLeaf(
      final String text, final String pointer, final List<DetectedReference> out) {
    final Matcher matcher = SecretReference.REFERENCE_PATTERN.matcher(text);
    final Set<String> namesInLeaf = new LinkedHashSet<>();
    while (matcher.find()) {
      namesInLeaf.add(matcher.group().substring(SecretReference.PREFIX.length()));
    }
    namesInLeaf.forEach(name -> out.add(new DetectedReference(name, pointer)));
  }

  /**
   * Escapes a JSON pointer segment per RFC 6901: {@code ~} is replaced first (to {@code ~0}), then
   * {@code /} (to {@code ~1}) — the order matters, otherwise a segment containing {@code /} would
   * produce a pointer that does not round-trip through {@code JsonPointer.compile}.
   */
  private static String escape(final String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }

  /** A detected reference: the secret name and the RFC 6901 pointer of the leaf it was found in. */
  public record DetectedReference(String name, String pointer) {}
}
