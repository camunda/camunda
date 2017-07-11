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
package io.zeebe.dispatcher.impl.allocation;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class AllocatedMappedFile extends AllocatedBuffer
{

    protected final RandomAccessFile raf;

    public AllocatedMappedFile(ByteBuffer buffer, RandomAccessFile raf)
    {
        super(buffer);
        this.raf = raf;
    }

    @Override
    public void close() throws IOException
    {
        raf.close();
    }

    public RandomAccessFile getFile()
    {
        return raf;
    }

}
