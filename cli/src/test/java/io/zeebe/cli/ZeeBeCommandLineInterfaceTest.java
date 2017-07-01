package io.zeebe.cli;

import java.util.Arrays;

import org.junit.Test;


public class ZeeBeCommandLineInterfaceTest
{

    //not sure if there is any other solution
    EmbeddedBrokerRule embeddedBroker;
    static final String BPMN_FILE = ZeeBeCommandLineInterfaceTest.class.getClassLoader().getResource("demoProcess.bpmn").getPath().toString();

    public static Iterable<Object[]> data()
    {
        return  Arrays.asList(new Object[][] {
            {"workflow deploy -ip 127.0.0.1:51015 -f " + BPMN_FILE},
            {"workflow start -ip 127.0.0.1:51015 -pid demoProcess -p {}"},
        });
    }


    @Test
    public void shouldNotMeetException()
    {
        //given
        embeddedBroker = new EmbeddedBrokerRule();

        final String[] deployCommand = {"workflow", "deploy", "-ip", "127.0.0.1:51015", "-f", BPMN_FILE};
        final String[] startCommand = {"workflow", "start", "-ip", "127.0.0.1:51015", "-pid", "demoProcess", "-p", "{}"};

        //then no exception

        //when
        embeddedBroker.startBroker();
        StandaloneClient.processor(deployCommand);
        StandaloneClient.processor(startCommand);


        //clean
        embeddedBroker.stopBroker();

    }

}
