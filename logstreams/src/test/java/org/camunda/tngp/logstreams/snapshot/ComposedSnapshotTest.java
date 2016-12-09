/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class ComposedSnapshotTest
{
    private static final String DATA_PART1 = "part1";
    private static final String DATA_PART2 = "part2";

    private SerializableWrapper<String> part1;
    private SerializableWrapper<String> part2;

    private File snapshotFile;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws IOException
    {
        part1 = new SerializableWrapper<>(DATA_PART1);
        part2 = new SerializableWrapper<>(DATA_PART2);

        snapshotFile = tempFolder.newFile("snapshot");
    }

    @Test
    public void shouldRecoverParts() throws FileNotFoundException, IOException, Exception
    {
        final ComposedSnapshot composedSnapshot = new ComposedSnapshot(part1, part2);

        composedSnapshot.writeSnapshot(new FileOutputStream(snapshotFile));

        composedSnapshot.recoverFromSnapshot(new FileInputStream(snapshotFile));

        assertThat(part1.getObject()).isNotNull().isEqualTo(DATA_PART1);
        assertThat(part2.getObject()).isNotNull().isEqualTo(DATA_PART2);
    }

    @Test
    public void shouldFailIfSnapshotHaveNoParts() throws FileNotFoundException, IOException, Exception
    {
        thrown.expect(IllegalArgumentException.class);

        new ComposedSnapshot();
    }

    @Test
    public void shouldFailToRecoverIfPartsAreLessThanSnapshot() throws FileNotFoundException, IOException, Exception
    {
        new ComposedSnapshot(part1, part2).writeSnapshot(new FileOutputStream(snapshotFile));

        thrown.expect(IllegalStateException.class);

        new ComposedSnapshot(part1).recoverFromSnapshot(new FileInputStream(snapshotFile));
    }

    @Test
    public void shouldFailToRecoverIfPartsAreMoreThanSnapshot() throws FileNotFoundException, IOException, Exception
    {
        new ComposedSnapshot(part1).writeSnapshot(new FileOutputStream(snapshotFile));

        thrown.expect(IllegalStateException.class);

        new ComposedSnapshot(part1, part2).recoverFromSnapshot(new FileInputStream(snapshotFile));
    }

}
