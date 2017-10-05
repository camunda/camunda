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
package io.zeebe.logstreams.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Test;

public class UnpackedObjectSnapshotTest
{

    @Test
    public void shouldRecoverFromSnapshot() throws Exception
    {
        // given
        final FooObject foo = new FooObject();
        foo.setProp1(42);
        foo.setProp2("bar");

        final UnpackedObjectSnapshotSupport snapshotSupport = new UnpackedObjectSnapshotSupport(foo);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        snapshotSupport.writeSnapshot(outStream);

        final FooObject deserializedFoo = new FooObject();
        final UnpackedObjectSnapshotSupport deserializingSnapshotSupport = new UnpackedObjectSnapshotSupport(deserializedFoo);
        final ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());

        // when
        deserializingSnapshotSupport.recoverFromSnapshot(inStream);

        // then
        assertThat(deserializedFoo.getProp1()).isEqualTo(42);
        assertThat(deserializedFoo.getProp2()).isEqualTo("bar");
    }
}
