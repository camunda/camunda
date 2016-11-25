package org.camunda.tngp.broker.test.util.bpmn;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.Association;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class TngpModelInstance implements BpmnModelInstance
{
    protected BpmnModelInstance wrappedInstance;

    public TngpModelInstance(BpmnModelInstance wrappedInstance)
    {
        this.wrappedInstance = wrappedInstance;
    }

    @Override
    public DomDocument getDocument()
    {
        return wrappedInstance.getDocument();
    }

    @Override
    public ModelElementInstance getDocumentElement()
    {
        return wrappedInstance.getDocumentElement();
    }

    @Override
    public void setDocumentElement(ModelElementInstance documentElement)
    {
        wrappedInstance.setDocumentElement(documentElement);
    }

    @Override
    public <T extends ModelElementInstance> T newInstance(Class<T> type)
    {
        return wrappedInstance.newInstance(type);
    }

    @Override
    public <T extends ModelElementInstance> T newInstance(ModelElementType type)
    {
        return wrappedInstance.newInstance(type);
    }

    @Override
    public Model getModel()
    {
        return wrappedInstance.getModel();
    }

    @Override
    public <T extends ModelElementInstance> T getModelElementById(String id)
    {
        return wrappedInstance.getModelElementById(id);
    }

    @Override
    public Collection<ModelElementInstance> getModelElementsByType(ModelElementType referencingType)
    {
        return wrappedInstance.getModelElementsByType(referencingType);
    }

    @Override
    public <T extends ModelElementInstance> Collection<T> getModelElementsByType(Class<T> referencingClass)
    {
        return wrappedInstance.getModelElementsByType(referencingClass);
    }

    @Override
    public ValidationResults validate(Collection<ModelElementValidator<?>> validators)
    {
        return wrappedInstance.validate(validators);
    }

    @Override
    public BpmnModelInstance clone()
    {
        return wrappedInstance.clone();
    }

    @Override
    public Definitions getDefinitions()
    {
        return wrappedInstance.getDefinitions();
    }

    @Override
    public void setDefinitions(Definitions arg0)
    {
        wrappedInstance.setDefinitions(arg0);
    }

    public TngpModelInstance taskAttributes(String taskId, String taskType, int taskQueueId)
    {
        final ModelElementInstance task = wrappedInstance.getModelElementById(taskId);

        task.setAttributeValueNs(BpmnModelConstants.CAMUNDA_NS, "taskType", taskType);
        task.setAttributeValueNs(BpmnModelConstants.CAMUNDA_NS, "taskQueueId", String.valueOf(taskQueueId));

        return this;
    }

    public TngpModelInstance conditionExpression(String sequenceFlowId, String arg1, String operator, String arg2)
    {
        final SequenceFlow sequenceFlow = wrappedInstance.getModelElementById(sequenceFlowId);
        final ConditionExpression conditionExpression = sequenceFlow.getModelInstance().newInstance(ConditionExpression.class);
        conditionExpression.setAttributeValueNs(BpmnModelConstants.CAMUNDA_NS, "arg1", arg1);
        conditionExpression.setAttributeValueNs(BpmnModelConstants.CAMUNDA_NS, "arg2", arg2);
        conditionExpression.setAttributeValueNs(BpmnModelConstants.CAMUNDA_NS, "operator", operator);
        sequenceFlow.setConditionExpression(conditionExpression);

        return this;
    }

    public TngpModelInstance removeFlowNode(String flowNodeId)
    {
        final FlowNode flowNode = getModelElementById(flowNodeId);
        final ModelElementInstance scope = flowNode.getParentElement();

        for (SequenceFlow outgoingFlow : flowNode.getOutgoing())
        {
            scope.removeChildElement(outgoingFlow);
        }
        for (SequenceFlow incomingFlow : flowNode.getIncoming())
        {
            scope.removeChildElement(incomingFlow);
        }
        final Collection<Association> associations = scope.getChildElementsByType(Association.class);
        for (Association association : associations)
        {
            if (flowNode.equals(association.getSource()) || flowNode.equals(association.getTarget()))
            {
                scope.removeChildElement(association);
            }
        }
        scope.removeChildElement(flowNode);

        return this;
    }

    public static TngpModelInstance wrap(BpmnModelInstance modelInstance)
    {
        return new TngpModelInstance(modelInstance);
    }

    public static TngpModelInstance wrapCopy(BpmnModelInstance modelInstance)
    {
        return new TngpModelInstance(modelInstance.clone());
    }
}
