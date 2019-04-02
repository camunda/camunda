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
package io.zeebe.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class JsonPathTokenizerTest {

  @Test
  public void testTokenization() {
    // given
    final List<Token> tokens = new ArrayList<>();
    final JsonPathTokenizer tokenizer = new JsonPathTokenizer();

    final String jsonPath = "key1.key2.key3";
    final UnsafeBuffer jsonPathBuffer = new UnsafeBuffer(jsonPath.getBytes(StandardCharsets.UTF_8));

    // when
    tokenizer.tokenize(
        jsonPathBuffer,
        0,
        jsonPathBuffer.capacity(),
        (type, buf, offset, length) -> tokens.add(new Token(type, offset, length)));

    // then
    assertThat(tokens).hasSize(7);
    assertThat(tokens.get(0))
        .isEqualTo(new Token(JsonPathToken.START_INPUT, 0, jsonPathBuffer.capacity()));
    assertThat(tokens.get(1)).isEqualTo(new Token(JsonPathToken.LITERAL, 0, 4));
    assertThat(tokens.get(2)).isEqualTo(new Token(JsonPathToken.CHILD_OPERATOR, 4, 1));
    assertThat(tokens.get(3)).isEqualTo(new Token(JsonPathToken.LITERAL, 5, 4));
    assertThat(tokens.get(4)).isEqualTo(new Token(JsonPathToken.CHILD_OPERATOR, 9, 1));
    assertThat(tokens.get(5)).isEqualTo(new Token(JsonPathToken.LITERAL, 10, 4));
    assertThat(tokens.get(6))
        .isEqualTo(new Token(JsonPathToken.END_INPUT, 0, jsonPathBuffer.capacity()));
  }

  protected static class Token {

    JsonPathToken type;
    int position;
    int length;

    public Token(JsonPathToken type, int position, int length) {
      this.type = type;
      this.position = position;
      this.length = length;
    }

    @Override
    public boolean equals(Object obj) {
      final Token otherToken = (Token) obj;
      return type == otherToken.type
          && position == otherToken.position
          && length == otherToken.length;
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }

    @Override
    public String toString() {
      return "Type: " + type + ", Position: " + position + ", Length: " + length;
    }
  }
}
