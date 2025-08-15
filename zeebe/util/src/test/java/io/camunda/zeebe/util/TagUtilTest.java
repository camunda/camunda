/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TagUtilTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a", // single letter
        "A", // single uppercase letter
        "validTag", // camelCase
        "valid_tag", // with underscore
        "valid-tag", // with hyphen
        "valid:tag", // with colon
        "valid.tag", // with period
        "a1", // letter followed by number
        "A1", // uppercase letter followed by number
        "tag123", // letters followed by numbers
        "complex_tag-123:v1.0", // complex valid tag
        "myTag_v1.2-final:release", // complex with all allowed characters
        "production", // common use case
        "development", // common use case
        "test_env", // common use case
        "api-v2.1", // versioned API tag
        "user:admin", // role-based tag
        "region.us-east", // geographic tag
        "a0123456789", // digits after first letter
        "aABCDEFGHIJKLMNOPQRSTUVWXYZ", // uppercase after first letter
        "aabcdefghijklmnopqrstuvwxyz", // lowercase after first letter
        "a_", // underscore after first letter
        "a-", // hyphen after first letter
        "a:", // colon after first letter
        "a.", // period after first letter
        "env:production", // real-world: environment
        "version:1.2.3", // real-world: version
        "team:backend-api", // real-world: team
        "region:us-east-1", // real-world: region
        "cost-center:eng-123", // real-world: cost center
        "project_name", // real-world: project
        "feature-flag:enabled", // real-world: feature flag
        "service.auth.v2", // real-world: service
        "critical:high-priority", // real-world: priority
        "deployment:blue-green", // real-world: deployment strategy
      })
  void shouldAcceptValidTags(final String validTag) {
    assertThat(TagUtil.isValidTag(validTag)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "", // empty tag
        " ", // blank tag
        "   ", // multiple spaces
        "\t", // tab character
        "\n", // newline character
        "\r", // carriage return character
        "1", // single digit
        "1tag", // starts with number
        "9invalid", // starts with number
        "_tag", // starts with underscore
        "-tag", // starts with hyphen
        ":tag", // starts with colon
        ".tag", // starts with period
        "tag with space", // contains space
        "tag\ttab", // contains tab
        "tag\nnewline", // contains newline
        "tag@symbol", // contains @ symbol
        "tag#hash", // contains hash
        "tag$dollar", // contains dollar sign
        "tag%percent", // contains percent
        "tag^caret", // contains caret
        "tag&ampersand", // contains ampersand
        "tag*asterisk", // contains asterisk
        "tag+plus", // contains plus
        "tag=equals", // contains equals
        "tag[bracket", // contains bracket
        "tag]bracket", // contains bracket
        "tag{brace", // contains brace
        "tag}brace", // contains brace
        "tag|pipe", // contains pipe
        "tag\\backslash", // contains backslash
        "tag/slash", // contains forward slash
        "tag<less", // contains less than
        "tag>greater", // contains greater than
        "tag?question", // contains question mark
        "tag\"quote", // contains quote
        "tag'apostrophe", // contains apostrophe
        "tag;semicolon", // contains semicolon
        "tag,comma", // contains comma
        "café", // contains accented character
        "tağ", // contains non-ASCII character
        "🏷️tag", // starts with emoji
        "tag🏷️", // contains emoji
        "тag", // contains Cyrillic character
        "tag with multiple spaces", // contains multiple spaces
        "tag\twith\ttabs", // contains multiple tabs
        "tag\nwith\nnewlines", // contains multiple newlines
        "tag@#$%^&*()", // contains special characters
        "tag+=[]{}|\\", // contains special characters
        "tag<>?\"';,", // contains special characters
      })
  void shouldRejectInvalidTags(final String invalidTag) {
    assertThat(TagUtil.isValidTag(invalidTag)).isFalse();
  }

  @Test
  void shouldRejectNullTag() {
    assertThat(TagUtil.isValidTag(null)).isFalse();
  }

  @Test
  void shouldRejectTagExceeding100Characters() {
    final String tooLongTag = "a" + "b".repeat(100); // 101 characters
    assertThat(TagUtil.isValidTag(tooLongTag)).isFalse();
    assertThat(tooLongTag.length()).isEqualTo(101);
  }

  @Test
  void shouldAcceptTagWith100Characters() {
    final String maxLengthTag = "a" + "b".repeat(99); // exactly 100 characters
    assertThat(TagUtil.isValidTag(maxLengthTag)).isTrue();
    assertThat(maxLengthTag.length()).isEqualTo(100);
  }
}
