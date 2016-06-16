package org.camunda.tngp.broker;

import java.util.Scanner;
import static java.lang.Runtime.*;

public class StandaloneBroker
{

    public static void main(String[] args)
    {
        String configFile = null;
        if(args.length == 1)
        {
            configFile = args[0];
        }

        final Broker taskBroker = new Broker(configFile);

        getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                taskBroker.close();
            }
        });

        try (Scanner scanner = new Scanner(System.in))
        {
            String nextLine = scanner.nextLine();
            if(nextLine.contains("exit")
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
