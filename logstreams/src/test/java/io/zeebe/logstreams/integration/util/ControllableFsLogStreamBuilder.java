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
package io.zeebe.logstreams.integration.util;

import org.agrona.DirectBuffer;
import io.zeebe.logstreams.fs.FsLogStreamBuilder;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;

import java.io.File;

public class ControllableFsLogStreamBuilder extends FsLogStreamBuilder
{

    public ControllableFsLogStreamBuilder(final DirectBuffer topicName, final int partitionId)
    {
        super(topicName, partitionId);
    }

    @Override
    public void initLogStorage()
    {
        if (logDirectory == null)
        {
            logDirectory = logRootPath + File.separatorChar + logName + File.separatorChar;
        }

        final File file = new File(logDirectory);
        file.mkdirs();

        final FsLogStorageConfiguration storageConfig = new FsLogStorageConfiguration(logSegmentSize,
            logDirectory,
            initialLogSegmentId,
            deleteOnClose);

        final FsLogStorage storage = new ControllableFsLogStorage(storageConfig);

        storage.open();
        logStorage = storage;
    }

}
