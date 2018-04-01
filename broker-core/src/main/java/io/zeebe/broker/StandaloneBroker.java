/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker;

import static java.lang.Runtime.getRuntime;

import java.util.Scanner;

public class StandaloneBroker
{
    public static void main(String[] args)
    {
        final Broker broker = new Broker(args);

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
