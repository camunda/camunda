package io.zeebe.broker;

import static java.lang.Runtime.getRuntime;

import java.util.Scanner;

public class StandaloneBroker
{

    public static void main(String[] args)
    {
        String configFile = null;
        if (args.length == 1)
        {
            configFile = args[0];
        }

        final Broker broker = new Broker(configFile);

        getRuntime().addShutdownHook(new Thread("Broker close Thread")
        {
            @Override
            public void run()
            {
                broker.close();
            }
        });

        try (Scanner scanner = new Scanner(System.in))
        {
            while (scanner.hasNextLine())
            {
                final String nextLine = scanner.nextLine();
                if (nextLine.contains("exit")
                    || nextLine.contains("close")
                    || nextLine.contains("quit")
                    || nextLine.contains("halt")
                    || nextLine.contains("shutdown")
                    || nextLine.contains("stop"))
                {
                    System.exit(0);
                }
            }
        }

    }

}
