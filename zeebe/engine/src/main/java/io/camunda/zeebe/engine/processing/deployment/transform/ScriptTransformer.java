/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Transformer for managed scripts: {@code .py} and {@code .js} resources run by the Connectors
 * Runtime.
 *
 * <p>A script can carry an optional header that sets its resource ID and version tag. The header is
 * a <a href="https://peps.python.org/pep-0723/">PEP 723</a> {@code script} block with a {@code
 * [tool.camunda]} table. Python uses {@code #} line comments; JavaScript mirrors the same block
 * with {@code //} line comments:
 *
 * <pre>
 * # /// script
 * # [tool.camunda]
 * # id = "calculate-total"
 * # versionTag = "1.2.0"
 * # ///
 * </pre>
 *
 * <p>Without a {@code script} block, or without a {@code [tool.camunda]} table in it, the script
 * stays a generic resource whose ID is its file name. The header is only read, the code is never
 * run.
 */
@NullMarked
public final class ScriptTransformer extends DefaultResourceTransformer {

  private static final String BLOCK_TYPE = "script";
  private static final String BYTE_ORDER_MARK = "\uFEFF";
  private static final TomlMapper TOML_MAPPER = new TomlMapper();
  private static final ScriptLanguage PYTHON = new ScriptLanguage("#");
  private static final ScriptLanguage JAVASCRIPT = new ScriptLanguage("//");

  public ScriptTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final ResourceState resourceState) {
    super(keyGenerator, stateWriter, checksumGenerator, resourceState);
  }

  @Override
  public boolean canTransform(final DeploymentResource resource) {
    return languageOf(resource.getResourceName()) != null;
  }

  @Override
  protected Either<Failure, ResourceInfo> parseResourceInfo(final DeploymentResource resource) {
    final var resourceName = resource.getResourceName();
    final var language = Objects.requireNonNull(languageOf(resourceName));
    final var text = new String(resource.getResource(), StandardCharsets.UTF_8);
    final var lines =
        (text.startsWith(BYTE_ORDER_MARK) ? text.substring(1) : text).lines().toList();

    final var scan = language.scanScriptBlocks(lines);
    if (scan.unclosed()) {
      return failure(resourceName, "the 'script' header block is not closed");
    }
    if (scan.blocks().isEmpty()) {
      return Either.right(ResourceInfo.of(resourceName));
    }
    if (scan.blocks().size() > 1) {
      return failure(resourceName, "expected at most one 'script' header block, but found many");
    }

    final JsonNode header;
    try {
      header = TOML_MAPPER.readTree(scan.blocks().getFirst());
    } catch (final JsonProcessingException e) {
      return failure(resourceName, "the header is not valid TOML: " + e.getOriginalMessage());
    }

    final var camunda = header.path("tool").path("camunda");
    if (!camunda.isObject()) {
      return Either.right(ResourceInfo.of(resourceName));
    }

    final var id = camunda.get("id");
    final var versionTag = camunda.get("versionTag");
    if (id != null && !id.isTextual() || versionTag != null && !versionTag.isTextual()) {
      return failure(resourceName, "expected 'id' and 'versionTag' to be strings");
    }
    if (id != null && id.asText().isBlank()) {
      return failure(resourceName, "expected the resource id to be filled, but it is blank");
    }

    return Either.right(
        ResourceInfo.of(
            id != null ? id.asText() : resourceName,
            versionTag != null ? versionTag.asText() : null));
  }

  private static @Nullable ScriptLanguage languageOf(final String resourceName) {
    if (resourceName.endsWith(".py")) {
      return PYTHON;
    }
    if (resourceName.endsWith(".js")) {
      return JAVASCRIPT;
    }
    return null;
  }

  private static Either<Failure, ResourceInfo> failure(
      final String resourceName, final String reason) {
    return Either.left(
        new Failure(
            String.format("Failed to read the header of script '%s': %s", resourceName, reason)));
  }

  /**
   * The comment syntax of a script language, and the PEP 723 block rules applied to it: a block
   * opens with {@code <prefix> /// script}, holds only lines that are {@code <prefix>} or start
   * with {@code <prefix> }, and ends at the last {@code <prefix> ///} line among them. This is the
   * reference regex of PEP 723, scanned line by line because a regex with a repeated group
   * overflows the stack on long comment runs.
   */
  private static final class ScriptLanguage {

    private final String prefix;
    private final String commentedPrefix;
    private final String openingLine;
    private final String closingLine;

    private ScriptLanguage(final String prefix) {
      this.prefix = prefix;
      commentedPrefix = prefix + " ";
      openingLine = prefix + " /// " + BLOCK_TYPE;
      closingLine = prefix + " ///";
    }

    /** Returns the TOML content of each {@code script} block, with the comment prefixes removed. */
    private ScanResult scanScriptBlocks(final List<String> lines) {
      final List<String> blocks = new ArrayList<>();
      int index = 0;
      while (index < lines.size()) {
        if (!lines.get(index).equals(openingLine)) {
          index++;
          continue;
        }
        int closingIndex = -1;
        int next = index + 1;
        while (next < lines.size() && isCommentLine(lines.get(next))) {
          if (lines.get(next).equals(closingLine)) {
            closingIndex = next;
          }
          next++;
        }
        if (closingIndex < 0) {
          return new ScanResult(blocks, true);
        }
        blocks.add(uncomment(lines.subList(index + 1, closingIndex)));
        index = closingIndex + 1;
      }
      return new ScanResult(blocks, false);
    }

    private boolean isCommentLine(final String line) {
      return line.equals(prefix) || line.startsWith(commentedPrefix);
    }

    private String uncomment(final List<String> blockLines) {
      return blockLines.stream()
          .map(
              line ->
                  line.startsWith(commentedPrefix) ? line.substring(commentedPrefix.length()) : "")
          .collect(Collectors.joining("\n"));
    }
  }

  private record ScanResult(List<String> blocks, boolean unclosed) {}
}
