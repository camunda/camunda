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
