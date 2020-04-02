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

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

/** Address test. */
public class AddressTest {
  @Test
  public void testIPv4Address() throws Exception {
    final Address address = Address.from("127.0.0.1:5000");
    assertEquals("127.0.0.1", address.host());
    assertEquals(5000, address.port());
    assertEquals("localhost", address.address().getHostName());
    assertEquals("127.0.0.1:5000", address.toString());
  }

  @Test
  public void testIPv6Address() throws Exception {
    final Address address = Address.from("[fe80:cd00:0000:0cde:1257:0000:211e:729c]:5000");
    assertEquals("fe80:cd00:0000:0cde:1257:0000:211e:729c", address.host());
    assertEquals(5000, address.port());
    assertEquals("fe80:cd00:0:cde:1257:0:211e:729c", address.address().getHostName());
    assertEquals("[fe80:cd00:0000:0cde:1257:0000:211e:729c]:5000", address.toString());
  }

  @Test
  @Ignore
  public void testResolveAddress() throws Exception {
    final Address address = Address.from("localhost", 5000);
    assertEquals("127.0.0.1", address.address().getHostAddress());
    assertEquals(5000, address.port());
  }
}
