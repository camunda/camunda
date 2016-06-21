package org.camunda.tngp.log.integration;

import static org.assertj.core.api.Assertions.*;

import java.io.File;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.Logs;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DeleteOnCloseTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldNotDeleteOnCloseByDefault()
    {
        final File logFolder = tempFolder.getRoot();

        final Log log = Logs.createLog("foo", 0)
                .logRootPath(logFolder.getAbsolutePath())
                .build();

        log.start();

        // if
        log.close();

        // then
        assertThat(logFolder.listFiles().length).isGreaterThan(0);
    }

    @Test
    @Ignore
    public void shouldDeleteOnCloseIfSet()
    {
        final File logFolder = tempFolder.getRoot();

        final Log log = Logs.createLog("foo", 0)
                .logRootPath(logFolder.getAbsolutePath())
                .deleteOnClose(true)
                .build();

        log.start();

        // if
        log.close();

        // then
        assertThat(logFolder.listFiles().length).isEqualTo(0);
    }
}
