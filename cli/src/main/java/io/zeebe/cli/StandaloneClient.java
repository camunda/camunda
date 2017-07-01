package io.zeebe.cli;

import io.zeebe.cli.core.Command;

public class StandaloneClient
{

    public static void main(String[] args)
    {
        try
        {
            processor(args);
        }
        catch (Exception e)
        {
            //do not show any exception to users.
//            e.printStackTrace();
        }
    }

    public static void processor(String[] args)
    {
        final Command main = new Command();
        main.setName("zeebe");
        main.setDesc("Camunda Zeebe Client command client tools");

        final Command workflow = new Command();
        final Deploy testDeploy = new Deploy();
        final Start testStart = new Start();
        workflow.setName("workflow");
        workflow.setDesc("Command related to workflow operation.");
        main.addCommand(workflow);
        workflow.addCommand(testDeploy);
        workflow.addCommand(testStart);
        main.setOptions(args);
        main.start();
    }

}


