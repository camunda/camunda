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

import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.util.FileUtil;
import org.agrona.BitUtil;
import org.agrona.LangUtil;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import static io.zeebe.util.StringUtil.getBytes;


public class FsSnapshotWriter implements SnapshotWriter
{
    public static final Logger LOG = io.zeebe.logstreams.impl.Loggers.LOGSTREAMS_LOGGER;
    protected final FsSnapshotStorageConfiguration config;
    protected final File dataFile;
    protected final File checksumFile;
    protected final FsReadableSnapshot lastSnapshot;

    protected DigestOutputStream dataOutputStream;
    protected BufferedOutputStream checksumOutputStream;

    public FsSnapshotWriter(FsSnapshotStorageConfiguration config, File snapshotFile, File checksumFile, FsReadableSnapshot lastSnapshot)
    {
        this.config = config;
        this.dataFile = snapshotFile;
        this.checksumFile = checksumFile;
        this.lastSnapshot = lastSnapshot;

        initOutputStreams(config, snapshotFile, checksumFile);
    }

    protected void initOutputStreams(FsSnapshotStorageConfiguration config, File snapshotFile, File checksumFile)
    {
        try
        {
            final MessageDigest messageDigest = MessageDigest.getInstance(config.getChecksumAlgorithm());

            final FileOutputStream dataFileOutputStream = new FileOutputStream(snapshotFile);
            final BufferedOutputStream bufferedDataOutputStream = new BufferedOutputStream(dataFileOutputStream);
            dataOutputStream = new DigestOutputStream(bufferedDataOutputStream, messageDigest);

            final FileOutputStream checksumFileOutputStream = new FileOutputStream(checksumFile);
            checksumOutputStream = new BufferedOutputStream(checksumFileOutputStream);

        }
        catch (Exception e)
        {
            abort();
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public OutputStream getOutputStream()
    {
        return dataOutputStream;
    }

    @Override
    public void commit() throws Exception
    {
        try
        {
            dataOutputStream.close();

            final MessageDigest digest = dataOutputStream.getMessageDigest();

            final byte[] digestBytes = digest.digest();
            final String digestString = BitUtil.toHex(digestBytes);
            final String checksumFileContents = config.checksumContent(digestString, dataFile.getName());

            checksumOutputStream.write(getBytes(checksumFileContents));
            checksumOutputStream.close();

            if (lastSnapshot != null)
            {
                LOG.info("Delete last snapshot file {}.", lastSnapshot.getDataFile());
                lastSnapshot.delete();
            }
        }
        catch (Exception e)
        {
            abort();
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public void abort()
    {
        FileUtil.closeSilently(dataOutputStream);
        FileUtil.closeSilently(checksumOutputStream);
        dataFile.delete();
        checksumFile.delete();
    }

    public File getChecksumFile()
    {
        return checksumFile;
    }

    public File getDataFile()
    {
        return dataFile;
    }
}
