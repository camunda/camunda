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
package io.zeebe.perftest.reporter;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class FileReportWriter implements RateReportFn
{
    protected final List<long[]> values = new ArrayList<>();

    @Override
    public void reportRate(long timestamp, long intervalValue)
    {
        values.add(new long[]{ timestamp, intervalValue });
    }

    public void writeToFile(String filename)
    {
        final File file = new File(filename);

        if (file.exists())
        {
            file.delete();
        }

        try
        {
            file.createNewFile();
            final PrintWriter printWriter = new PrintWriter(file);

            printWriter.println("# Nanoseconds since Start, Number of requests sent");

            for (long[] fields : values)
            {
                for (int i = 0; i < fields.length; i++)
                {
                    printWriter.print(fields[i]);

                    if (i < fields.length - 1)
                    {
                        printWriter.print("\t");
                    }
                    else
                    {
                        printWriter.print("\n");
                    }
                }
            }

            printWriter.flush();
            printWriter.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

    }
}
