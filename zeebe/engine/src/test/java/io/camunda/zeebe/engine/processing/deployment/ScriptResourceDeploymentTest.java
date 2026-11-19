/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior.LinkedResourceProps;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ScriptResourceDeploymentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldReadIdAndVersionTagFromPythonHeader() {
    // given
    final var script =
        """
        # /// script
        # [tool.camunda]
        # id = "calculate-total"
        # versionTag = "1.2.0"
        # ///
        def execute(variables, context):
            return {}
        """;

    // when
    final var metadata = deployScript(script, "calculate_total.py");

    // then
    Assertions.assertThat(metadata)
        .hasResourceId("calculate-total")
        .hasVersionTag("1.2.0")
        .hasResourceName("calculate_total.py")
        .hasVersion(1);
  }

  @Test
  public void shouldReadIdAndVersionTagFromJavaScriptHeader() {
    // given
    final var script =
        """
        // /// script
        // [tool.camunda]
        // id = "calculate-total-js"
        // versionTag = "1.2.0"
        // ///
        export function execute(variables, context) { return {}; }
        """;

    // when
    final var metadata = deployScript(script, "calculate_total.js");

    // then
    Assertions.assertThat(metadata)
        .hasResourceId("calculate-total-js")
        .hasVersionTag("1.2.0")
        .hasResourceName("calculate_total.js")
        .hasVersion(1);
  }

  @Test
  public void shouldReadHeaderNextToOtherPep723Metadata() {
    // given - a real PEP 723 block also declares the Python version and dependencies
    final var script =
        """
        #!/usr/bin/env python3
        # /// script
        # requires-python = ">=3.12"
        # dependencies = [
        #   "requests<3",
        #   "rich",
        # ]
        #
        # [tool.camunda]
        # id = "with-dependencies"
        # versionTag = "2.0.0"
        # ///
        def execute(variables, context):
            return {}
        """;

    // when
    final var metadata = deployScript(script, "with_dependencies.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("with-dependencies").hasVersionTag("2.0.0");
  }

  @Test
  public void shouldReadHeaderWithWindowsLineEndings() {
    // given
    final var script =
        "# /// script\r\n# [tool.camunda]\r\n# id = \"crlf-script\"\r\n# ///\r\ndef execute(v, c): ...\r\n";

    // when
    final var metadata = deployScript(script, "crlf.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("crlf-script");
  }

  @Test
  public void shouldReadHeaderAfterByteOrderMark() {
    // given - some Windows editors save UTF-8 files with a byte order mark
    final var script = "﻿# /// script\n# [tool.camunda]\n# id = \"with-bom\"\n# ///\n";

    // when
    final var metadata = deployScript(script, "with_bom.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("with-bom");
  }

  @Test
  public void shouldReadHeaderPlacedAfterCode() {
    // given - as in PEP 723, the block can be anywhere in the file
    final var script =
        """
        import json

        # /// script
        # [tool.camunda]
        # id = "header-after-code"
        # ///
        def execute(variables, context):
            return {}
        """;

    // when
    final var metadata = deployScript(script, "header_after_code.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("header-after-code");
  }

  @Test
  public void shouldUseFileNameAsIdWithoutHeader() {
    // given
    final var python = "def execute(variables, context):\n    return {}\n";
    final var javaScript = "export function execute(variables, context) { return {}; }\n";

    // when
    final var pythonMetadata = deployScript(python, "no_header.py");
    final var javaScriptMetadata = deployScript(javaScript, "no_header.js");

    // then
    Assertions.assertThat(pythonMetadata).hasResourceId("no_header.py").hasVersionTag("");
    Assertions.assertThat(javaScriptMetadata).hasResourceId("no_header.js").hasVersionTag("");
  }

  @Test
  public void shouldUseFileNameAsIdWhenHeaderHasNoCamundaTable() {
    // given - an ordinary PEP 723 script that only declares its dependencies
    final var script =
        """
        # /// script
        # dependencies = ["requests"]
        # ///
        def execute(variables, context):
            return {}
        """;

    // when
    final var metadata = deployScript(script, "dependencies_only.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("dependencies_only.py").hasVersionTag("");
  }

  @Test
  public void shouldUseFileNameAsIdWhenHeaderHasOnlyVersionTag() {
    // given - lets an existing script gain a version tag without changing its id
    final var script =
        """
        # /// script
        # [tool.camunda]
        # versionTag = "1.0.0"
        # ///
        def execute(variables, context):
            return {}
        """;

    // when
    final var metadata = deployScript(script, "version_tag_only.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("version_tag_only.py").hasVersionTag("1.0.0");
  }

  @Test
  public void shouldNotReadJavaScriptBlockComment() {
    // given - only line comments form a header
    final var script =
        """
        /* /// script
        [tool.camunda]
        id = "block-comment"
        /// */
        export function execute(variables, context) { return {}; }
        """;

    // when
    final var metadata = deployScript(script, "block_comment.js");

    // then
    Assertions.assertThat(metadata).hasResourceId("block_comment.js");
  }

  @Test
  public void shouldCreateNewVersionForSameHeaderIdInAnotherFile() {
    // given
    final var header =
        """
        # /// script
        # [tool.camunda]
        # id = "shared-id"
        # ///
        """;
    deployScript(header + "def execute(v, c): return 1\n", "first.py");

    // when
    final var metadata = deployScript(header + "def execute(v, c): return 2\n", "second.py");

    // then - the id comes from the header, so both files are versions of one script
    Assertions.assertThat(metadata).hasResourceId("shared-id").hasVersion(2);
  }

  @Test
  public void shouldRejectBlankId() {
    // given
    final var script =
        """
        # /// script
        # [tool.camunda]
        # id = "  "
        # ///
        """;

    // when
    final var rejection = deployRejectedScript(script, "blank_id.py");

    // then
    assertThat(rejection.getRejectionReason())
        .contains("Expected 'id' in [tool.camunda] of 'blank_id.py' to be filled, but it is blank");
  }

  @Test
  public void shouldRejectNonStringId() {
    // given
    final var script =
        """
        // /// script
        // [tool.camunda]
        // id = 42
        // ///
        """;

    // when
    final var rejection = deployRejectedScript(script, "numeric_id.js");

    // then
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected 'id' in [tool.camunda] of 'numeric_id.js' to be a string, but got number"
                + " (42)");
  }

  @Test
  public void shouldRejectInvalidToml() {
    // given
    final var script =
        """
        # /// script
        # [tool.camunda]
        # id = calculate-total
        # ///
        """;

    // when
    final var rejection = deployRejectedScript(script, "invalid_toml.py");

    // then
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected the 'script' header block of 'invalid_toml.py' to be valid TOML, but got:");
  }

  @Test
  public void shouldRejectUnclosedBlock() {
    // given
    final var script =
        """
        # /// script
        # [tool.camunda]
        # id = "unclosed"
        def execute(variables, context):
            return {}
        """;

    // when
    final var rejection = deployRejectedScript(script, "unclosed.py");

    // then
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected the 'script' header block of 'unclosed.py' that starts at line 1 to end with"
                + " a '# ///' line, but line 4 ('def execute(variables, context):') does not"
                + " start with '# '. Each line of the block must be '#' or start with '# '");
  }

  @Test
  public void shouldRejectMoreThanOneBlock() {
    // given
    final var script =
        """
        # /// script
        # [tool.camunda]
        # id = "first"
        # ///

        # /// script
        # [tool.camunda]
        # id = "second"
        # ///
        """;

    // when
    final var rejection = deployRejectedScript(script, "two_blocks.py");

    // then
    assertThat(rejection.getRejectionReason())
        .contains("Expected at most one 'script' header block in 'two_blocks.py', but found 2");
  }

  @Test
  public void shouldReadHeaderFollowedByLongCommentRun() {
    // given - a long run of comment lines must not exhaust the stack while looking for the block
    final var script =
        "# /// script\n# [tool.camunda]\n# id = \"long-header\"\n# ///\n"
            + "# comment\n".repeat(50_000);

    // when
    final var metadata = deployScript(script, "long_header.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("long-header");
  }

  @Test
  public void shouldRejectUnclosedBlockFollowedByLongCommentRun() {
    // given
    final var script = "# /// script\n" + "# comment\n".repeat(50_000);

    // when
    final var rejection = deployRejectedScript(script, "long_unclosed.py");

    // then
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected the 'script' header block of 'long_unclosed.py' that starts at line 1 to end"
                + " with a '# ///' line, but reached the end of the file");
  }

  @Test
  public void shouldReadHeaderWithTrailingWhitespaceOnMarkers() {
    // given - trailing whitespace is invisible in most editors
    final var script =
        "# /// script \n# [tool.camunda]\n# id = \"trailing-space\"\n# versionTag = \"1.0.0\"\n#"
            + " ///\t\ndef execute(variables, context): ...\n";

    // when
    final var metadata = deployScript(script, "trailing_space.py");

    // then
    Assertions.assertThat(metadata).hasResourceId("trailing-space").hasVersionTag("1.0.0");
  }

  @Test
  public void shouldRejectBlockWithLineMissingSpaceAfterCommentPrefix() {
    // given
    final var script =
        """
        // /// script
        // [tool.camunda]
        //id = "missing-space"
        // ///
        """;

    // when
    final var rejection = deployRejectedScript(script, "missing_space.js");

    // then
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected the 'script' header block of 'missing_space.js' that starts at line 1 to end"
                + " with a '// ///' line, but line 3 ('//id = \"missing-space\"') does not start"
                + " with '// '. Each line of the block must be '//' or start with '// '");
  }

  @Test
  public void shouldRejectNonStringVersionTag() {
    // given
    final var script =
        """
        # /// script
        # [tool.camunda]
        # id = "numeric-version-tag"
        # versionTag = 2
        # ///
        """;

    // when
    final var rejection = deployRejectedScript(script, "numeric_version_tag.py");

    // then
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected 'versionTag' in [tool.camunda] of 'numeric_version_tag.py' to be a string,"
                + " but got number (2)");
  }

  @Test
  public void shouldLinkScriptByVersionTagFromHeader() throws Exception {
    // given
    final var scriptV1 =
        """
        # /// script
        # [tool.camunda]
        # id = "linked-script"
        # versionTag = "1.0.0"
        # ///
        def execute(variables, context): return 1
        """;
    final var scriptV2 =
        """
        # /// script
        # [tool.camunda]
        # id = "linked-script"
        # versionTag = "2.0.0"
        # ///
        def execute(variables, context): return 2
        """;
    final var v1 = deployScript(scriptV1, "linked_script.py");
    deployScript(scriptV2, "linked_script.py");

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("linked-script-process")
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeLinkedResources(
                                l ->
                                    l.resourceId("linked-script")
                                        .resourceType("script")
                                        .bindingType(ZeebeBindingType.versionTag)
                                        .versionTag("1.0.0")
                                        .linkName("script"))
                            .zeebeJobType("script-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("linked-script-process").create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final List<LinkedResourceProps> linkedResources =
        MAPPER.readValue(
            job.getValue().getCustomHeaders().get("linkedResources"), new TypeReference<>() {});
    assertThat(linkedResources)
        .singleElement()
        .extracting(LinkedResourceProps::getResourceKey)
        .isEqualTo(String.valueOf(v1.getResourceKey()));
  }

  private ResourceMetadataValue deployScript(final String script, final String fileName) {
    final var deployment =
        ENGINE.deployment().withJsonResource(script.getBytes(UTF_8), fileName).deploy();
    return deployment.getValue().getResourceMetadata().getFirst();
  }

  private Record<?> deployRejectedScript(final String script, final String fileName) {
    final var rejection =
        ENGINE
            .deployment()
            .withJsonResource(script.getBytes(UTF_8), fileName)
            .expectRejection()
            .deploy();
    Assertions.assertThat(rejection)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    return rejection;
  }
}
