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
package io.zeebe.logstreams.impl.snapshot.fs;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.zeebe.logstreams.impl.Loggers;
import org.agrona.LangUtil;
import io.zeebe.logstreams.spi.SnapshotStorage;
import org.slf4j.Logger;

public class FsSnapshotStorage implements SnapshotStorage
{
    public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

    protected final FsSnapshotStorageConfiguration cfg;

    public FsSnapshotStorage(FsSnapshotStorageConfiguration cfg)
    {
        this.cfg = cfg;
    }

    @Override
    public FsReadableSnapshot getLastSnapshot(String name) throws Exception
    {
        final File rootFile = new File(cfg.getRootPath());
        final List<File> snapshotFiles = Arrays.asList(rootFile.listFiles(file -> cfg.matchesSnapshotFileNamePattern(file, name)));

        FsReadableSnapshot snapshot = null;

        if (snapshotFiles.size() > 0)
        {
            snapshotFiles.sort((f1, f2) -> Long.compare(position(f1, name), position(f2, name)));

            final File snapshotFile = snapshotFiles.get(0);
            final long logPosition = position(snapshotFile, name);

            final String checksumFileName = cfg.checksumFileName(name, logPosition);
            final File checksumFile = new File(checksumFileName);

            if (checksumFile.exists())
            {
                snapshot = new FsReadableSnapshot(cfg, snapshotFile, checksumFile, logPosition);
            }
            else
            {
                // delete snapshot file since checksum does not exist anymore
                LOG.error("Delete snapshot {}, no checksum file exists.", snapshotFile.getAbsolutePath());

                snapshotFile.delete();
            }
        }

        return snapshot;
    }

    public boolean purgeSnapshot(String name)
    {
        final File rootFile = new File(cfg.getRootPath());
        final List<File> snapshotFiles = Arrays.asList(rootFile.listFiles(file -> cfg.matchesSnapshotFileNamePattern(file, name)));

        boolean deletionSuccessful = false;
        if (snapshotFiles.size() > 0)
        {
            for (File snapshotFile : snapshotFiles)
            {
                final long logPosition = position(snapshotFile, name);
                final String checksumFileName = cfg.checksumFileName(name, logPosition);
                final File checksumFile = new File(checksumFileName);

                checksumFile.delete();
                snapshotFile.delete();
            }
            deletionSuccessful = true;
        }
        return deletionSuccessful;
    }

    protected long position(File file, String snapshotName)
    {
        return cfg.getPositionOfSnapshotFile(file, snapshotName);
    }

    @Override
    public FsSnapshotWriter createSnapshot(String name, long logPosition) throws Exception
    {
        final FsReadableSnapshot lastSnapshot = getLastSnapshot(name);

        final String snapshotFileName = cfg.snapshotFileName(name, logPosition);
        final String checksumFileName = cfg.checksumFileName(name, logPosition);

        final File snapshotFile = new File(snapshotFileName);
        final File checksumFile = new File(checksumFileName);

        if (snapshotFile.exists())
        {
            throw new RuntimeException(String.format("Cannot write snapshot %s, file already exists.", snapshotFile.getAbsolutePath()));
        }

        if (checksumFile.exists())
        {
            throw new RuntimeException(String.format("Cannot write snapshot checksum %s, file already exists.", checksumFile.getAbsolutePath()));
        }

        try
        {
            snapshotFile.createNewFile();
            checksumFile.createNewFile();
        }
        catch (IOException e)
        {
            snapshotFile.delete();
            checksumFile.delete();
            LangUtil.rethrowUnchecked(e);
        }

        return new FsSnapshotWriter(cfg, snapshotFile, checksumFile, lastSnapshot);
    }
}
