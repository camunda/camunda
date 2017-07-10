/**
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

import java.io.File;

public class MappedFileAllocationDescriptor extends AllocationDescriptor
{

    protected File file;

    protected long startPosition;

    public MappedFileAllocationDescriptor(int capacity, File file)
    {
        super(capacity);
        this.file = file;
    }

    public File getFile()
    {
        return file;
    }

    public void setFile(File file)
    {
        this.file = file;
    }

    public long getStartPosition()
    {
        return startPosition;
    }

    public void setStartPosition(long startProsition)
    {
        this.startPosition = startProsition;
    }
}
