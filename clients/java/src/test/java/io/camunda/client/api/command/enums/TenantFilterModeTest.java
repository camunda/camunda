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
package io.camunda.client.api.command.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TenantFilterModeTest {

  @Test
  public void shouldConvertUppercaseAssigned() {
    // when
    final TenantFilterMode result = TenantFilterMode.from("ASSIGNED");

    // then
    assertThat(result).isEqualTo(TenantFilterMode.ASSIGNED);
  }

  @Test
  public void shouldConvertLowercaseAssigned() {
    // when
    final TenantFilterMode result = TenantFilterMode.from("assigned");

    // then
    assertThat(result).isEqualTo(TenantFilterMode.ASSIGNED);
  }

  @Test
  public void shouldConvertMixedCaseAssigned() {
    // when
    final TenantFilterMode result = TenantFilterMode.from("AsSiGnEd");

    // then
    assertThat(result).isEqualTo(TenantFilterMode.ASSIGNED);
  }

  @Test
  public void shouldConvertUppercaseProvided() {
    // when
    final TenantFilterMode result = TenantFilterMode.from("PROVIDED");

    // then
    assertThat(result).isEqualTo(TenantFilterMode.PROVIDED);
  }

  @Test
  public void shouldConvertLowercaseProvided() {
    // when
    final TenantFilterMode result = TenantFilterMode.from("provided");

    // then
    assertThat(result).isEqualTo(TenantFilterMode.PROVIDED);
  }

  @Test
  public void shouldConvertMixedCaseProvided() {
    // when
    final TenantFilterMode result = TenantFilterMode.from("PrOvIdEd");

    // then
    assertThat(result).isEqualTo(TenantFilterMode.PROVIDED);
  }

  @ParameterizedTest
  @ValueSource(strings = {" ASSIGNED", "ASSIGNED ", " ASSIGNED ", "  ASSIGNED  "})
  public void shouldTrimWhitespaceForAssigned(final String value) {
    // when
    final TenantFilterMode result = TenantFilterMode.from(value);

    // then
    assertThat(result).isEqualTo(TenantFilterMode.ASSIGNED);
  }

  @ParameterizedTest
  @ValueSource(strings = {" PROVIDED", "PROVIDED ", " PROVIDED ", "  PROVIDED  "})
  public void shouldTrimWhitespaceForProvided(final String value) {
    // when
    final TenantFilterMode result = TenantFilterMode.from(value);

    // then
    assertThat(result).isEqualTo(TenantFilterMode.PROVIDED);
  }

  @ParameterizedTest
  @ValueSource(strings = {" assigned ", " provided ", "  ASSIGNED  ", "  PROVIDED  "})
  public void shouldHandleBothCaseAndWhitespace(final String value) {
    // when
    final TenantFilterMode result = TenantFilterMode.from(value);

    // then
    assertThat(result).isIn(TenantFilterMode.ASSIGNED, TenantFilterMode.PROVIDED);
  }

  @Test
  public void shouldThrowExceptionForNullValue() {
    // when/then
    assertThatThrownBy(() -> TenantFilterMode.from(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tenant filter value cannot be null")
        .hasMessageContaining("Expected 'ASSIGNED' or 'PROVIDED'");
  }

  @Test
  public void shouldThrowExceptionForInvalidValue() {
    // when/then
    assertThatThrownBy(() -> TenantFilterMode.from("INVALID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid tenant filter value: 'INVALID'")
        .hasMessageContaining("Expected 'ASSIGNED' or 'PROVIDED' (case-insensitive)");
  }

  @Test
  public void shouldThrowExceptionForEmptyString() {
    // when/then
    assertThatThrownBy(() -> TenantFilterMode.from(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid tenant filter value");
  }

  @Test
  public void shouldThrowExceptionForWhitespaceOnlyString() {
    // when/then
    assertThatThrownBy(() -> TenantFilterMode.from("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid tenant filter value");
  }
}
