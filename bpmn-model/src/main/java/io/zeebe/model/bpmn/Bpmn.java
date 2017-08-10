package io.zeebe.model.bpmn;

import java.io.File;

import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.impl.BpmnParser;
import io.zeebe.model.bpmn.impl.BpmnTransformer;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public class Bpmn
{
    private static BpmnParser parser = new BpmnParser();
    private static BpmnTransformer transformer = new BpmnTransformer();


    public static BpmnBuilder createExecutableWorkflow(String bpmnProcessId)
    {
        return new BpmnBuilder(transformer, bpmnProcessId);
    }

    public static WorkflowDefinition readFromFile(File file)
    {
        final DefinitionsImpl definitions = parser.readFromFile(file);
        // TODO validate workflow
        final WorkflowDefinition workflowDefinition = transformer.transform(definitions);

        return workflowDefinition;
    }

    public static String convertToString(WorkflowDefinition definition)
    {
        if (definition instanceof DefinitionsImpl)
        {
            return parser.convertToString((DefinitionsImpl) definition);
        }
        else
        {
            throw new RuntimeException("not supported");
        }
    }

}
