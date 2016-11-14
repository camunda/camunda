package org.camunda.tngp.broker.log;

import java.util.HashMap;
import java.util.Map;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.taskqueue.CreateTaskInstanceRequestReader;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.WorkflowInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnBranchEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestDecoder;
import org.camunda.tngp.protocol.log.BpmnActivityEventDecoder;
import org.camunda.tngp.protocol.log.BpmnBranchEventDecoder;
import org.camunda.tngp.protocol.log.BpmnFlowElementEventDecoder;
import org.camunda.tngp.protocol.log.BpmnProcessEventDecoder;
import org.camunda.tngp.protocol.log.CreateTaskRequestDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestDecoder;
import org.camunda.tngp.protocol.log.WfDefinitionDecoder;
import org.camunda.tngp.protocol.log.WfDefinitionRequestDecoder;
import org.camunda.tngp.protocol.log.WorkflowInstanceRequestDecoder;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.camunda.tngp.protocol.wf.WfDefinitionReader;
import org.camunda.tngp.protocol.wf.WfDefinitionRequestReader;
import org.camunda.tngp.util.buffer.BufferReader;

/**
 * An instance of {@link Templates} is stateful and not thread-safe
 */
public class Templates
{

    // TODO: this could even become based on arrays, given that we know the minimal template id and
    // the template ids are ascending (which we could enforce)
    protected static final Int2ObjectHashMap<Template<?>> TEMPLATES = new Int2ObjectHashMap<>();

    // wf runtime
    public static final Template<BpmnActivityEventReader> ACTIVITY_EVENT =
            newTemplate(BpmnActivityEventDecoder.TEMPLATE_ID, BpmnActivityEventReader.class);
    public static final Template<BpmnProcessEventReader> PROCESS_EVENT =
            newTemplate(BpmnProcessEventDecoder.TEMPLATE_ID, BpmnProcessEventReader.class);
    public static final Template<BpmnFlowElementEventReader> FLOW_ELEMENT_EVENT =
            newTemplate(BpmnFlowElementEventDecoder.TEMPLATE_ID, BpmnFlowElementEventReader.class);
    public static final Template<BpmnBranchEventReader> BPMN_BRANCH_EVENT =
            newTemplate(BpmnBranchEventDecoder.TEMPLATE_ID, BpmnBranchEventReader.class);

    public static final Template<WorkflowInstanceRequestReader> WF_INSTANCE_REQUEST =
            newTemplate(WorkflowInstanceRequestDecoder.TEMPLATE_ID, WorkflowInstanceRequestReader.class);
    public static final Template<ActivityInstanceRequestReader> ACTIVITY_INSTANCE_REQUEST =
            newTemplate(ActivityInstanceRequestDecoder.TEMPLATE_ID, ActivityInstanceRequestReader.class);

    public static final Template<WfDefinitionReader> WF_DEFINITION =
            newTemplate(WfDefinitionDecoder.TEMPLATE_ID, WfDefinitionReader.class);

    public static final Template<WfDefinitionRequestReader> WF_DEFINITION_REQUEST =
            newTemplate(WfDefinitionRequestDecoder.TEMPLATE_ID, WfDefinitionRequestReader.class);

    // task
    public static final Template<TaskInstanceReader> TASK_INSTANCE =
            newTemplate(TaskInstanceDecoder.TEMPLATE_ID, TaskInstanceReader.class);
    public static final Template<TaskInstanceRequestReader> TASK_INSTANCE_REQUEST =
            newTemplate(TaskInstanceRequestDecoder.TEMPLATE_ID, TaskInstanceRequestReader.class);
    public static final Template<CreateTaskInstanceRequestReader> CREATE_TASK_REQUEST =
            newTemplate(CreateTaskRequestDecoder.TEMPLATE_ID, CreateTaskInstanceRequestReader.class);


    protected static <T extends BufferReader> Template<T> newTemplate(int templateId, Class<T> readerClass)
    {
        if (TEMPLATES.containsKey(templateId))
        {
            throw new RuntimeException("Cannot register two templates with same id");
        }

        final Template<T> template = new Template<>(templateId, readerClass);
        TEMPLATES.put(templateId, template);
        return template;
    }


    protected Map<Template<?>, BufferReader> entryReaders = new HashMap<>();

    public Templates(Template<?>... templates)
    {
        for (Template<?> template : templates)
        {
            this.entryReaders.put(template, template.newReader());
        }
    }

    public static Templates wfRuntimeLogTemplates()
    {
        return new Templates(
                ACTIVITY_EVENT,
                PROCESS_EVENT,
                FLOW_ELEMENT_EVENT,
                WF_INSTANCE_REQUEST,
                ACTIVITY_INSTANCE_REQUEST,
                WF_DEFINITION,
                WF_DEFINITION_REQUEST,
                BPMN_BRANCH_EVENT);
    }

    public static Templates taskQueueLogTemplates()
    {
        return new Templates(
                TASK_INSTANCE,
                TASK_INSTANCE_REQUEST,
                CREATE_TASK_REQUEST);
    }

    @SuppressWarnings("unchecked")
    public <S extends BufferReader> S getReader(Template<S> template)
    {
        // TODO: do something if template not contained
        return (S) entryReaders.get(template);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BufferReader> Template<T> getTemplate(int id)
    {
        // TODO: throw exception if template does not exist
        return (Template<T>) TEMPLATES.get(id);
    }
}
