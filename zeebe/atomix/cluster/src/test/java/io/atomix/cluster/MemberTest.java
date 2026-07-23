/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.utils.net.Address;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class MemberTest {

  private static final Address ADDRESS = Address.from("localhost", 26502);

  @Test
  void shouldPropagateZoneFromMemberIdWhenBuildingZonedMemberViaFactory() {
    // given
    final var memberId = MemberId.from("us-east", 0);

    // when
    final var member = Member.member(memberId, ADDRESS);

    // then
    assertThat(member.zone()).isEqualTo("us-east");
  }

  @Test
  void shouldBuildBareMemberViaFactoryWhenMemberIdHasNoZone() {
    // given
    final var memberId = MemberId.from(0);

    // when / then
    assertThatNoException().isThrownBy(() -> Member.member(memberId, ADDRESS));
    assertThat(Member.member(memberId, ADDRESS).zone()).isNull();
  }

  @Test
  void shouldBuildViaConfigConstructorWhenZoneMatchesMemberId() {
    // given
    final var memberId = MemberId.from("us-east", 0);
    final var config = new MemberConfig().setId(memberId).setZoneId("us-east").setAddress(ADDRESS);

    // when
    final var member = new Member(config);

    // then
    assertThat(member.id()).isEqualTo(memberId);
    assertThat(member.zone()).isEqualTo("us-east");
  }

  @Test
  void shouldThrowViaConfigConstructorWhenZoneDoesNotMatchMemberId() {
    // given
    final var memberId = MemberId.from("us-east", 0);
    final var config = new MemberConfig().setId(memberId).setZoneId("eu-west");

    // when / then
    assertThatThrownBy(() -> new Member(config)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldDeriveZoneFromMemberIdViaTwoArgConstructor() {
    // given
    final var memberId = MemberId.from("us-east", 0);

    // when
    final var member = new Member(memberId, ADDRESS);

    // then
    assertThat(member.id()).isEqualTo(memberId);
    assertThat(member.zone()).isEqualTo("us-east");
  }

  @Test
  void shouldBuildBareMemberViaTwoArgConstructorWhenMemberIdHasNoZone() {
    // given
    final var memberId = MemberId.from(0);

    // when
    final var member = new Member(memberId, ADDRESS);

    // then
    assertThat(member.zone()).isNull();
  }

  @Test
  void shouldSetAllFieldsViaFullConstructorWhenZoneMatchesMemberId() {
    // given
    final var memberId = MemberId.from("us-east", 0);
    final var properties = new Properties();
    properties.setProperty("k", "v");

    // when
    final var member = new Member(memberId, 3L, ADDRESS, "us-east", "rack-1", "host-1", properties);

    // then
    assertThat(member.id()).isEqualTo(memberId);
    assertThat(member.nodeVersion()).isEqualTo(3L);
    assertThat(member.zone()).isEqualTo("us-east");
    assertThat(member.rack()).isEqualTo("rack-1");
    assertThat(member.host()).isEqualTo("host-1");
    assertThat(member.properties()).isEqualTo(properties);
  }

  @Test
  void shouldThrowViaFullConstructorWhenZoneDoesNotMatchMemberId() {
    // given
    final var memberId = MemberId.from("us-east", 0);

    // when / then
    assertThatThrownBy(
            () -> new Member(memberId, 0L, ADDRESS, "eu-west", null, null, new Properties()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
