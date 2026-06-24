/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.Test;

/** Address test. */
public class AddressTest {
  @Test
  public void testIPv4Address() throws Exception {
    final Address address = Address.from("127.0.0.1:5000");
    assertThat(address.host()).isEqualTo("127.0.0.1");
    assertThat(address.port()).isEqualTo(5000);
    assertThat(address.tryResolveAddress().getHostName()).isEqualTo("localhost");
    assertThat(address.toString()).isEqualTo("127.0.0.1:5000");
  }

  @Test
  public void testIPv6Address() throws Exception {
    final Address address = Address.from("[fe80:cd00:0000:0cde:1257:0000:211e:729c]:5000");
    assertThat(address.host()).isEqualTo("fe80:cd00:0000:0cde:1257:0000:211e:729c");
    assertThat(address.port()).isEqualTo(5000);
    assertThat(address.tryResolveAddress().getHostName())
        .isEqualTo("fe80:cd00:0:cde:1257:0:211e:729c");
    assertThat(address.toString()).isEqualTo("[fe80:cd00:0000:0cde:1257:0000:211e:729c]:5000");
  }

  @Test
  public void testResolveAddress() throws Exception {
    final Address address = Address.from("localhost", 5000);
    assertThat(address.tryResolveAddress().getHostAddress())
        .isEqualTo(InetAddress.getLoopbackAddress().getHostAddress());
    assertThat(address.port()).isEqualTo(5000);
  }
}
