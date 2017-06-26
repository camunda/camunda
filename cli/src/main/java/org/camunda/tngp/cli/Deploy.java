package org.camunda.tngp.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;
import java.util.stream.Collectors;

import org.camunda.tngp.cli.core.Command;
import org.camunda.tngp.cli.core.Option;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.workflow.cmd.DeploymentResult;

public class Deploy extends Command
{

    final Option ipArg = new Option();
    final Option fileArg = new Option();
    final Option testSwitch = new Option();

    String bpmnfile = null;
    String ipAddr = null;


    public Deploy()
    {
        this.setName("deploy");
        this.setDesc("deploy a bpmn workflow file (*.bpmn)");
        initArgs();
    }



    void initArgs()
    {
        ipArg.setName("ip").setLongName("ipaddr").setDesc("set ip address").needValue(true).setRequired(true);
        fileArg.setName("f").setLongName("filename").setDesc("set bpmnfile").needValue(true).setRequired(true);
        addOption(ipArg);
        addOption(fileArg);
    }
    void handleArgs()
    {
        ipAddr = ipArg.getValue();
        bpmnfile = fileArg.getValue();

    }

    @Override
    protected void run()
    {
        handleArgs();
        deployMain();
    }
    void deployMain()
    {
        final String topicName = "default-topic";
        InputStream bpmnStream = null;
        try
        {
            bpmnStream = new FileInputStream(new File(bpmnfile));
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        final int partitionId = 0;
        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, ipAddr);
        final TngpClient tngpClient = new TngpClientImpl(clientProperties);

        System.out.println(String.format("> Connecting to %s ...", ipAddr));
        tngpClient.connect();


        System.out.println("> Connected.");


        System.out.println(String.format("> Deploying workflow to topic '%s' and partition '%d'", topicName, partitionId));

        final DeploymentResult deploymentResult = tngpClient.workflowTopic(topicName, partitionId)
            .deploy()
            .resourceStream(bpmnStream)
            .execute();
        if (deploymentResult.isDeployed())
        {
            final String deployedWorkflows = deploymentResult.getDeployedWorkflows().stream()
                    .map(wf -> String.format("<%s>", wf.getBpmnProcessId()))
                    .collect(Collectors.joining(","));

            System.out.println(String.format("\n> Created Process ID (--pid,-p) for workflow: %s\n", deployedWorkflows));

        }
        else
        {
            System.out.println(String.format("> Fail to deploy: %s", deploymentResult.getErrorMessage()));
        }

        tngpClient.close();


    }

}
