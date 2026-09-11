/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.xml.sax.InputSource;
import uk.co.real_logic.sbe.SbeTool;
import uk.co.real_logic.sbe.generation.CodeGenerator;
import uk.co.real_logic.sbe.generation.java.JavaGenerator;
import uk.co.real_logic.sbe.generation.java.JavaOutputManager;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.IrEncoder;
import uk.co.real_logic.sbe.xml.IrGenerator;
import uk.co.real_logic.sbe.xml.MessageSchema;
import uk.co.real_logic.sbe.xml.ParserOptions;
import uk.co.real_logic.sbe.xml.XmlSchemaParser;

public final class SbeGenerator {

  public static void main(final String[] args) throws Exception {
    if (args.length < 1) {
      printUsage();
      System.exit(1);
    }

    final List<String> filenames = new ArrayList<>();
    final Map<String, String> cliOptions = new HashMap<>();

    for (final String arg : args) {
      if (arg.startsWith("-D")) {
        final int eqPos = arg.indexOf('=');
        if (eqPos > 2) {
          final String key = arg.substring(2, eqPos);
          final String value = arg.substring(eqPos + 1);
          cliOptions.put(key, value);
        }
      } else {
        filenames.add(arg);
      }
    }

    if (filenames.isEmpty()) {
      printUsage();
      System.exit(1);
    }

    final String outputDir = cliOptions.getOrDefault(SbeTool.OUTPUT_DIR, ".");
    final String targetLanguage = cliOptions.getOrDefault(SbeTool.TARGET_LANGUAGE, "Java");
    final String targetNamespace = cliOptions.get(SbeTool.TARGET_NAMESPACE);
    final boolean xIncludeAware =
        Boolean.parseBoolean(cliOptions.getOrDefault(SbeTool.XINCLUDE_AWARE, "false"));
    final boolean stopOnError =
        Boolean.parseBoolean(cliOptions.getOrDefault(SbeTool.VALIDATION_STOP_ON_ERROR, "false"));
    final boolean warningsFatal =
        Boolean.parseBoolean(cliOptions.getOrDefault(SbeTool.VALIDATION_WARNINGS_FATAL, "false"));
    final boolean suppressOutput =
        Boolean.parseBoolean(cliOptions.getOrDefault(SbeTool.VALIDATION_SUPPRESS_OUTPUT, "false"));
    final boolean generateInterfaces =
        Boolean.parseBoolean(cliOptions.getOrDefault(SbeTool.JAVA_GENERATE_INTERFACES, "false"));
    final boolean decodeUnknownEnumValues =
        Boolean.parseBoolean(cliOptions.getOrDefault(SbeTool.DECODE_UNKNOWN_ENUM_VALUES, "false"));

    if (!"Java".equalsIgnoreCase(targetLanguage)) {
      throw new IllegalArgumentException("Only Java target language is supported by this tool.");
    }

    final ParserOptions options =
        ParserOptions.builder()
            .stopOnError(stopOnError)
            .warningsFatal(warningsFatal)
            .suppressOutput(suppressOutput)
            .xIncludeAware(xIncludeAware)
            .build();

    for (final String filename : filenames) {
      final Path filePath = Paths.get(filename);
      try (final InputStream in = Files.newInputStream(filePath)) {
        final InputSource inputSource = new InputSource(in);
        inputSource.setSystemId(filename);
        final MessageSchema schema = XmlSchemaParser.parse(inputSource, options);
        final Ir ir = new IrGenerator().generate(schema, targetNamespace);

        final CodeGenerator codeGenerator =
            new JavaGenerator(
                ir,
                SbeTool.JAVA_DEFAULT_ENCODING_BUFFER_TYPE,
                SbeTool.JAVA_DEFAULT_DECODING_BUFFER_TYPE,
                false,
                generateInterfaces,
                decodeUnknownEnumValues,
                new JavaOutputManager(outputDir, ir.applicableNamespace()));

        codeGenerator.generate();

        final String schemaFileName = filePath.getFileName().toString();
        final String irFileName =
            schemaFileName.substring(0, schemaFileName.lastIndexOf('.')) + ".sbeir";
        final String irFilePath = filePath.resolveSibling(irFileName).toAbsolutePath().toString();
        int attempts = 3;
        while (attempts-- > 0) {
          try (final IrEncoder irEncoder = new IrEncoder(irFilePath, ir)) {
            irEncoder.encode();
            break;
          } catch (final Exception e) {
            if (Thread.interrupted()) {
              // Under IntelliJ, we get interrupted prematurely (unclear why). Swallow the interrupt
              // and try again.
              continue;
            } else {
              throw e;
            }
          }
        }
      }
    }
  }

  private static void printUsage() {
    System.err.println("Usage: SbeGenerator <filenames>... [-Doption=value]...");
    System.err.println("Options:");
    System.err.println("  -Dsbe.output.dir=<dir>                Output directory");
    System.err.println(
        "  -Dsbe.target.language=Java            Target language (only Java supported)");
    System.err.println("  -Dsbe.java.generate.interfaces=true|false");
    System.err.println("  -Dsbe.decode.unknown.enum.values=true|false");
    System.err.println("  -Dsbe.xinclude.aware=true|false");
  }
}
