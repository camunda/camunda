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
package io.zeebe.util;

import static io.zeebe.util.StreamUtil.readLong;
import static io.zeebe.util.StreamUtil.writeLong;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 */
public class StreamUtilTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File file;

    @Before
    public void init() throws IOException
    {
        file = tempFolder.newFile();
    }

    @Test
    public void shouldReadAndWriteLong() throws Exception
    {
        for (int pow = 0; pow < 64; pow++)
        {
            // given
            final FileOutputStream fileOutputStream = new FileOutputStream(file);
            final long value = 1L << pow;

            // when
            writeLong(fileOutputStream, value);

            // then
            final FileInputStream fileInputStream = new FileInputStream(file);
            final long readValue = readLong(fileInputStream);
            assertThat(readValue).isEqualTo(value);
        }
    }
}
