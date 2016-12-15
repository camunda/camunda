package org.camunda.tngp.msgpack.benchmark;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class JsonGenerator
{

    protected int maxLevel;
    protected int numKeysPerLevel;

    public JsonGenerator(int maxLevel, int numKeysPerLevel)
    {
        this.maxLevel = maxLevel;
        this.numKeysPerLevel = numKeysPerLevel;
    }

    public void generate(OutputStream outStream) throws Exception
    {
        OutputStreamWriter outWriter = new OutputStreamWriter(outStream, StandardCharsets.UTF_8);

        int numLeafElements = (int) Math.pow(numKeysPerLevel, maxLevel + 1);

        int currentLevel = 0;
        outWriter.write("{");
        for (int i = 0; i < numLeafElements; i++)
        {
            while (currentLevel < maxLevel)
            {
                int offsetOnLevel = offsetOnLevel(i, currentLevel, maxLevel, numKeysPerLevel);
                if (offsetOnLevel > 0)
                {
                    outWriter.append(",");
                }

                outWriter.write("\"");
                outWriter.write((char) (offsetOnLevel + 65));
                outWriter.write("\"");
                outWriter.write(":");
                outWriter.write("{");
                currentLevel++;
            }

            int offsetOnLevel = offsetOnLevel(i, currentLevel, maxLevel, numKeysPerLevel);

            outWriter.write("\"");
            outWriter.write((char) (offsetOnLevel + 65));
            outWriter.write("\"");
            outWriter.write(":");
            outWriter.write(Integer.toString(i));

            if (offsetOnLevel < numKeysPerLevel - 1)
            {
                outWriter.write(",");
            }

            while (offsetOnLevel(i, currentLevel, maxLevel, numKeysPerLevel) == numKeysPerLevel - 1 && currentLevel > 0)
            {
                outWriter.write("}");
                currentLevel--;
            }

        }
        outWriter.write("}");
        outWriter.flush();

        outStream.flush();
    }

    public static void main(String[] args)
    {

    }

    protected static int offsetOnLevel(int index, int level, int maxLevel, int numKeysPerLevel)
    {
        int stepSize = (int) Math.pow(numKeysPerLevel, maxLevel - level);
        int parentStepSize = stepSize * numKeysPerLevel;
        return (index % parentStepSize) / stepSize;
    }

    protected static String indexToString(int index)
    {
        return new String(new byte[]{ (byte) (index + 65) }, StandardCharsets.UTF_8);
    }
}
