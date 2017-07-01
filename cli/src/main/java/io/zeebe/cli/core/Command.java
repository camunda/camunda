package io.zeebe.cli.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Command
{

    public static final String INDENT = "      ";
    private HashMap<String, Command> commandMap = new HashMap<String, Command>();
    private HashMap<String, Option> optionMap = new HashMap<String, Option>();
    protected String name;
    protected String desc;
    private final Option helpOption = new Option();
    private String[] args;


    public Command()
    {
        helpOption.setName("h").setLongName("help").setDesc("show help").needValue(false);
        this.addOption(helpOption);
    }


    public String getName()
    {
        return name;
    }

    public Command setName(String name)
    {
        this.name = name;
        return this;
    }

    public String getDesc()
    {
        return desc;
    }

    public Command setDesc(String desc)
    {
        this.desc = desc;
        return this;
    }

    public Command addCommand(Command c)
    {
        commandMap.put(c.name, c);
        return c;
    }

    public void delCommand(String s)
    {
        commandMap.remove(s);
    }

    public void addOption(Option arg)
    {
        this.optionMap.put(arg.getName(), arg);
        this.optionMap.put(arg.getLongName(), arg);
    }


    private void parseArguments(String[] args) throws RuntimeException
    {
        final String arg;
        if (args.length >= 1)
        {
            arg = args[0];
            if (Option.isOption(arg))
            {
                feedArgs(args);
                if (helpOption.exists())
                {
                    showHelp();
                }
                verifyArgs();
            }
            else if (commandMap.containsKey(arg))
            {
                final String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                commandMap.get(arg).setOptions(subArgs).start();
            }
            else
            {
                System.out.println("Invalid command: " + arg);
                throw new RuntimeException("Invalid command!");
            }
        }
        else
        {
            showHelp();
        }

    }
    private void showHelp() throws RuntimeException
    {
        //banner
        System.out.println("\nCamunda Zeebe Client command line interface, welcome!");
        System.out.println("Usage: ./zeebe [command [command...]] [option [option...]]");
        System.out.println("");
        System.out.println("");
        System.out.println(this.name + ": " + this.desc);
        showFormattedDescription("");
        throw new RuntimeException("time to show help!");
    }

    private void showFormattedDescription(String prefix)
    {
        final Set<Option> argsSet = new HashSet<Option>(optionMap.values());
        if (!argsSet.isEmpty())
        {
            System.out.println("");
            System.out.println(prefix + " Arguments of " + this.name + ":");
        }
        for (Option arg : argsSet)
        {
            System.out.println(prefix + INDENT + arg.getName() + "," + arg.getLongName() + ":  \t" + arg.getDesc());
        }
        if (!commandMap.isEmpty())
        {
            System.out.println("");
            System.out.println(prefix + " Subcommand of " + this.name + ":");
        }
        for (Command cmd : commandMap.values())
        {
            System.out.println("");
            System.out.println(prefix + INDENT + cmd.name + ": \t" + cmd.getDesc());
            cmd.showFormattedDescription(prefix + INDENT);
        }

    }
    private void feedArgs(String[] args)
    {
        String[] ret = new String[args.length];
        System.arraycopy(args, 0, ret, 0, ret.length);
        while (ret.length != 0)
        {
            if (optionMap.containsKey(ret[0]))
            {
                ret = optionMap.get(ret[0]).eatArgs(ret);
            }
            else
            {
                System.out.println("Invalid option: " + ret[0]);
                throw new RuntimeException("Invalid option!");
            }
        }
    }

    private void verifyArgs()
    {
        final Set<Option> argsSet = new HashSet<Option>(optionMap.values());
        for (Option arg : argsSet)
        {
            if (arg.isRequired() && !arg.exists())
            {
                System.out.println("require " + arg.getName() + " but cannot find");
                throw new RuntimeException("cannot find required arguments");
            }
        }
    }

    public Command setOptions(String[] args)
    {
        this.args = args;
        return this;
    }

    public void start() throws RuntimeException
    {
        parseArguments(this.args);
        run();
    }

    //Interface for customized code;
    protected void run()
    {
    }

}
