package org.camunda.tngp.test.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;

public class TestFileUtil
{

    public static InputStream readAsTextFileAndReplace(InputStream inputStream, Charset charset, Map<String, String> replacements)
    {
        final String fileContent;
        try
        (
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        )
        {
            final StringBuilder sb = new StringBuilder();

            reader.lines().forEach((line) ->
            {
                String replacingLine = line;

                for (Map.Entry<String, String> replacement : replacements.entrySet())
                {
                    replacingLine = replacingLine.replaceAll(replacement.getKey(),
                            Matcher.quoteReplacement(replacement.getValue()));
                }

                sb.append(replacingLine);
                sb.append("\n");
            });

            fileContent = sb.toString();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }


        return new ByteArrayInputStream(fileContent.getBytes(charset));
    }
}
