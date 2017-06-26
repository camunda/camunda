package org.camunda.tngp.cli;

import java.util.Properties;

import org.camunda.tngp.cli.core.Command;
import org.camunda.tngp.cli.core.Option;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;

public class Start extends Command
{

    /*
     * Deploy is a subcommand,
     * 1. no commands after subcommand
     * 2. only args come up with subcommand
     */

    final Option ipArg = new Option();
    final Option payLoadArg = new Option();
    final Option processIdArg = new Option();

    String processId = null;
    String payLoad = null;
    String ipAddr = null;


    Start()
    {
        this.setName("start");
        this.setDesc("start a topic");
        initArgs();
    }



    void initArgs()
    {
        ipArg.setName("ip").setLongName("ipaddr").setDesc("set ip address").needValue(true).setRequired(true);
        payLoadArg.setName("p").setLongName("payload").setDesc("set payload").needValue(true).setRequired(true);
        processIdArg.setName("pid").setLongName("process-id").setDesc("set process-id").needValue(true).setRequired(true);
        addOption(ipArg);
        addOption(processIdArg);
        addOption(payLoadArg);
    }
    void handleArgs()
    {
        ipAddr = ipArg.getValue();
        processId = processIdArg.getValue();
        payLoad = payLoadArg.getValue();

    }

    @Override
    protected void run()
    {
        handleArgs();
        main();
    }
    void main()
    {
        final String topicName = "default-topic";

        final int partitionId = 0;
        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, ipAddr);
        final TngpClient tngpClient = new TngpClientImpl(clientProperties);

        System.out.println(String.format("> Connecting to %s ...", ipAddr));
        tngpClient.connect();


        System.out.println("> Connected.");
        try
        {
            final WorkflowInstance ret = tngpClient.workflowTopic(topicName, partitionId)
                    .create()
                    .bpmnProcessId(processId)
                    .payload(payLoad)
                    .execute();
        }
        catch (Exception e)
        {
            System.out.println("> ERROR!!!! not Created.\n");
            e.printStackTrace();
        }
        System.out.println("> Created");

        tngpClient.close();
    }

}
