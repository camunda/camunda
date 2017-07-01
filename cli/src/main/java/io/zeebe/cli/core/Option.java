package io.zeebe.cli.core;

public class Option
{
    private String name;
    private String desc;
    private Boolean needValue = false;
    private Boolean required = false;
    private Boolean exist = false;
    private String longName;
    private String value;
    public static final String LONG_NAME_PREFIX = "--";
    public static final String NAME_PREFIX = "-";

    public static Boolean isOption(String s)
    {
        return s.startsWith(NAME_PREFIX);
    }


    public Option setLongName(String name)
    {
        this.longName = LONG_NAME_PREFIX + name;
        return this;
    }

    public Option setName(String name)
    {
        this.name = NAME_PREFIX + name;
        return this;
    }

    public String getName()
    {
        return this.name;
    }

    public String getLongName()
    {
        return this.longName;
    }

    public Option setDesc(String desc)
    {
        this.desc = desc;
        return this;
    }

    public String getDesc()
    {
        return this.desc;
    }

    public Option needValue(Boolean b)
    {
        this.needValue = b;
        return this;
    }

    public String getValue()
    {
        return this.value;
    }

    public void showFormattedDescription(String prefix)
    {
        System.out.println(prefix + name + ", " + longName + " : " + desc);
    }

    public Boolean exists()
    {
        return this.exist;
    }

    public Option setRequired(Boolean b)
    {
        this.required = b;
        return this;
    }

    public Boolean isRequired()
    {
        return this.required;
    }

    public String[] eatArgs(String[] args)
    {
        if (needValue)
        {
            if (args.length >= 2)
            {
                value = args[1];
                exist = true;
            }
            else
            {
                System.out.println("there should be a value for " + this.name);
                throw new RuntimeException("Option need a value");
            }
        }
        else
        {
            exist = true;
        }
        final int removeLen = needValue ? 2 : 1;
        final String[] ret = new String[args.length - removeLen];
        System.arraycopy(args, removeLen, ret, 0, ret.length);

        return ret;
    }
}
