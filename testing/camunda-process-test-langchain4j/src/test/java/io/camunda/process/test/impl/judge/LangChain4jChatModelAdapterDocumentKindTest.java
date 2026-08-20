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
package io.camunda.process.test.impl.judge;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.judge.LangChain4jChatModelAdapter.DocumentKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LangChain4jChatModelAdapterDocumentKindTest {

  @ParameterizedTest(name = "[{index}] {0} -> {1}")
  @CsvSource(
      nullValues = "<null>",
      value = {
        // null and blank
        "<null>,            OTHER",
        "'',                OTHER",

        // images (top-level type wins, including structured-suffix images)
        "image/png,         IMAGE",
        "image/jpeg,        IMAGE",
        "image/svg+xml,     IMAGE",
        "IMAGE/PNG,         IMAGE",

        // pdf is the one named special case
        "application/pdf,   PDF",
        "APPLICATION/PDF,   PDF",

        // text/* family
        "text/plain,        TEXT",
        "text/csv,          TEXT",
        "text/markdown,     TEXT",

        // bare JSON / XML / YAML — IANA-registered without a +suffix
        "application/json,   TEXT",
        "application/xml,    TEXT",
        "application/yaml,   TEXT",
        "application/x-yaml, TEXT",

        // RFC 6839 structured-syntax suffixes — previously fell through to OTHER
        "application/ld+json,        TEXT",
        "application/vnd.api+json,   TEXT",
        "application/schema+json,    TEXT",
        "application/manifest+json,  TEXT",
        "application/atom+xml,       TEXT",
        "application/rss+xml,        TEXT",
        "application/xhtml+xml,      TEXT",
        "application/vnd.custom+yaml, TEXT",

        // case-insensitive suffix match
        "APPLICATION/LD+JSON, TEXT",

        // unambiguously not text-like — must stay OTHER
        "application/octet-stream, OTHER",
        "application/zip,          OTHER",
        "application/javascript,   OTHER",
        "application/x-sh,         OTHER",
        "audio/mpeg,               OTHER",
        "video/mp4,                OTHER",
        "not-a-mime-type,          OTHER",
      })
  void shouldClassifyContentType(final String contentType, final DocumentKind expected) {
    assertThat(DocumentKind.from(contentType)).isEqualTo(expected);
  }
}
