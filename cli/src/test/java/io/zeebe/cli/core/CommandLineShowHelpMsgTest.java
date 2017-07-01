package io.zeebe.cli.core;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CommandLineShowHelpMsgTest
{

    @Parameters
    public static Iterable<Object[]> data()
    {
        return  Arrays.asList(new Object[][] {
            {"-h"},
            {"--help"},
            {"test -h"},
            {"test --help"},
            {"test -ip 127.0.0.1:8000 -h"},
            {"test -ip 127.0.0.1:8000 --help"}

        });
    }

    @Parameter
    public String args;


    @Rule
    public ExpectedException expection = ExpectedException.none();


    @Test
    public void shouldShowHelpMsg()
    {
        final String[] argsArray = args.split(" ");
        //then
        expection.expect(RuntimeException.class);
        expection.expectMessage("time to show help!");

        //when
        final Command main = new Command();
        final Command test = new Command();
        test.setName("test").setDesc("testing command");
        test.addOption(new Option().setName("ip").setLongName("ipaddress").setRequired(false).needValue(true));
        main.addCommand(test);
        main.setOptions(argsArray);
        main.start();


    }

}
