package io.zeebe.cli;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;


public class ZeeBeCommandLineInterfaceTest
{

    //not sure if there is any other solution
    EmbeddedBrokerRule embeddedBroker;
    static final String BPMN_FILE = getPath("demoProcess.bpmn");

    public static Iterable<Object[]> data()
    {
        return  Arrays.asList(new Object[][] {
            {"workflow deploy -ip 127.0.0.1:51015 -f " + BPMN_FILE},
            {"workflow start -ip 127.0.0.1:51015 -pid demoProcess -p {}"},
        });
    }


    @Test
    public void shouldNotMeetException() throws UnsupportedEncodingException
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

    private static String getPath(String classPathResource)
    {
        try
        {
            final URL resource = ZeeBeCommandLineInterfaceTest.class.getClassLoader().getResource(classPathResource);
            final String path = resource.getPath().toString();
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

}
