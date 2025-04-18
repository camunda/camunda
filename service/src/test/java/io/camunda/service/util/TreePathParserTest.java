package io.camunda.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TreePathParserTest {

  @Test
  void shouldExtractProcessInstanceKeysFromValidTreePath() {
    // given
    final String treePath = "PI_1/FN_1/FNI_1/PI_2/FN_3/FNI_3";

    // when
    final List<String> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).containsExactly("1", "2"); // Extracts all process instance keys in order
  }

  @Test
  void shouldReturnEmptyListForTreePathWithoutProcessInstances() {
    // given
    final String treePath = "FN_1/FNI_1/FN_3/FNI_3";

    // when
    final List<String> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).isEmpty(); // No PI_x patterns present
  }

  @Test
  void shouldHandleTreePathWithSingleProcessInstance() {
    // given
    final String treePath = "PI_123";

    // when
    final List<String> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).containsExactly("123"); // Only one process instance key
  }

  @Test
  void shouldReturnEmptyListForEmptyTreePath() {
    // given
    final String treePath = "";

    // when
    final List<String> result = TreePathParser.extractProcessInstanceKeys(treePath);

    // then
    assertThat(result).isEmpty(); // No valid content in a tree path
  }
}
