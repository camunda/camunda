/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.sbe;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.xml.sax.InputSource;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.IrDecoder;
import uk.co.real_logic.sbe.json.JsonPrinter;
import uk.co.real_logic.sbe.xml.IrGenerator;
import uk.co.real_logic.sbe.xml.ParserOptions;
import uk.co.real_logic.sbe.xml.XmlSchemaParser;

public final class SbeJsonDecoder {

  private SbeJsonDecoder() {}

  public static Ir loadIr(final Path schema) throws Exception {
    final var schemaFileName = schema.getFileName().toString();
    if (schemaFileName.endsWith(".sbeir")) {
      try (final var irDecoder = new IrDecoder(ByteBuffer.wrap(Files.readAllBytes(schema)))) {
        return irDecoder.decode();
      }
    }

    final var parserOptions = ParserOptions.builder().stopOnError(true).xIncludeAware(true).build();
    try (final var inputStream = Files.newInputStream(schema)) {
      final var inputSource = new InputSource(inputStream);
      inputSource.setSystemId(schema.toUri().toString());
      final var messageSchema = XmlSchemaParser.parse(inputSource, parserOptions);
      return new IrGenerator().generate(messageSchema, schemaFileName);
    }
  }

  public static Ir loadIrFromResource(final Class<?> resourceOwner, final String resourceName)
      throws Exception {
    try (final var irFileResource =
        resourceOwner.getClassLoader().getResourceAsStream(resourceName)) {
      if (irFileResource == null) {
        throw new IllegalArgumentException("Failed to get resource " + resourceName);
      }

      try (final var irDecoder = new IrDecoder(ByteBuffer.wrap(irFileResource.readAllBytes()))) {
        return irDecoder.decode();
      }
    }
  }

  public static UnsafeBuffer readFile(final Path inputFile) throws Exception {
    final long fileSize = Files.size(inputFile);
    if (fileSize > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("File is too large to process: " + fileSize + " bytes");
    }

    return new UnsafeBuffer(Files.readAllBytes(inputFile));
  }

  public static String toJson(final Path inputFile, final Ir ir, final long offset)
      throws Exception {
    return toJson(readFile(inputFile), ir, offset);
  }

  public static String toJson(final DirectBuffer buffer, final Ir ir, final long offset) {
    final var output = new StringBuilder();
    new JsonPrinter(ir).print(output, buffer, validateOffset(offset, buffer.capacity()));
    return output.toString();
  }

  private static int validateOffset(final long offset, final int length) {
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must be greater than or equal to 0");
    }

    if (offset >= length) {
      throw new IllegalArgumentException(
          "Offset " + offset + " is outside the file size of " + length + " bytes");
    }

    return Math.toIntExact(offset);
  }
}
