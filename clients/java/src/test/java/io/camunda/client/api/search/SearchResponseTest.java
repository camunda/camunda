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
package io.camunda.client.api.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class SearchResponseTest {
  @Test
  public void shouldReturnSingleItem() {
    // given
    final String item = "item";
    final SearchResponse<String> resp =
        new SearchResponseTestImpl<>(Collections.singletonList(item));

    // when
    assertThat(resp.singleItem()).isEqualTo(item);
  }

  @Test
  public void shouldReturnNullOnEmpty() {
    // given
    final SearchResponse<String> resp = new SearchResponseTestImpl<>(Collections.emptyList());

    // when
    assertThat(resp.singleItem()).isNull();
  }

  @Test
  public void shouldThrowExceptionOnMultipleItems() {
    // given
    final List<String> items = new ArrayList<>();
    items.add("item1");
    items.add("item2");
    final SearchResponse<String> resp = new SearchResponseTestImpl<>(items);

    // when
    Assertions.assertThatThrownBy(() -> resp.singleItem())
        .isInstanceOf(ClientException.class)
        .hasMessage("Expecting only one item but got 2");
  }

  static class SearchResponseTestImpl<T> implements SearchResponse<T> {
    private final List<T> items;

    SearchResponseTestImpl(final List<T> items) {
      this.items = items;
    }

    @Override
    public List<T> items() {
      return items;
    }

    @Override
    public SearchResponsePage page() {
      throw new UnsupportedOperationException();
    }
  }
}
