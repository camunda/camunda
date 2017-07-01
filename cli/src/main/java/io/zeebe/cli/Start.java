package io.zeebe.cli;

import java.util.Properties;

import io.zeebe.cli.core.Command;
import io.zeebe.cli.core.Option;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.workflow.cmd.WorkflowInstance;

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
        final ZeebeClient zeebeClient = new ZeebeClientImpl(clientProperties);

        System.out.println(String.format("> Connecting to %s ...", ipAddr));
        zeebeClient.connect();


        System.out.println("> Connected.");
        try
        {
            final WorkflowInstance ret = zeebeClient.workflowTopic(topicName, partitionId)
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

        zeebeClient.close();
    }

}
