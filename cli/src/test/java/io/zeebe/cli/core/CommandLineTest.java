package io.zeebe.cli.core;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CommandLineTest
{



    Command main;
    Command test;

    @Rule
    public ExpectedException expection = ExpectedException.none();

    @Before
    public void before()
    {
        main = new Command();
        test = new Command();
        test.setName("test").setDesc("testing command");
        test.addOption(new Option().setName("ip").setLongName("ipaddress").setRequired(false).needValue(true));
        main.addCommand(test);
    }

    @Test
    public void shouldThrowInvaildOptionException()
    {
        //then
        expection.expect(RuntimeException.class);
        expection.expectMessage("Invalid option!");

        //when
        final String[] testArgs = {"test", "-f"};
        main.setOptions(testArgs);
        main.start();
    }

    @Test
    public void shouldThrowInvaildCommandException()
    {
        //then
        expection.expect(RuntimeException.class);
        expection.expectMessage("Invalid command!");

        //when
        final String[] testArgs = {"test", "deploy"};
        main.setOptions(testArgs);
        main.start();
    }

    @Test
    public void shouldThrowNeedRequiredOptionException()
    {
        //then
        expection.expect(RuntimeException.class);
        expection.expectMessage("cannot find required arguments");

        //when
        final String[] testArgs = {"test", "-ip", "127.0.0.1:5000"};
        test.addOption(new Option().setName("f").setLongName("file").setRequired(true).needValue(true));
        main.setOptions(testArgs);
        main.start();
    }

    @Test
    public void shouldThrowNeedValueException()
    {
        //then
        expection.expect(RuntimeException.class);
        expection.expectMessage("Option need a value");

        //when
        final String[] testArgs = {"test", "-ip"};
        main.setOptions(testArgs);
        main.start();
    }

    @Test
    public void shouldExecuteCommand()
    {


        //given
        final String[] testArgs = {"exec", "-s"};
        final Command execCommand = new Command()
        {
            @Override
            protected void run()
            {
                throw new RuntimeException("Command executed");
            }
        };
        execCommand.setName("exec").setDesc("execute command");
        execCommand.addOption(new Option().setName("s").setLongName("start").setRequired(true).needValue(false));
        main.addCommand(execCommand);


        //then
        expection.expect(RuntimeException.class);
        expection.expectMessage("Command executed");


        //when
        main.setOptions(testArgs);
        main.start();
    }
}
