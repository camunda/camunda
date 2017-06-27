package io.zeebe.broker.workflow.graph.transformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.type.ModelElementType;

import io.zeebe.broker.workflow.graph.model.BpmnFactory;
import io.zeebe.broker.workflow.graph.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;

public class Transformers
{
    private static final Map<Class<? extends BaseElement>, BpmnElementTransformer<?, ?>> TRANSFORMERS = new HashMap<>();

    private static final Map<Class<? extends BaseElement>, List<BpmnElementTransformer<?, ?>>> TRANSFORMERS_BY_TYPE = new HashMap<>();

    static
    {
        registerTransformer(new FlowElementTransformer());
        registerTransformer(new FlowNodeTransformer());
        registerTransformer(new ProcessTransformer());
        registerTransformer(new SequenceFlowTransformer());
        registerTransformer(new ServiceTaskTransformer());

        BpmnFactory.getSupportedTypes().forEach(Transformers::initTransformers);
    }

    private static void initTransformers(Class<? extends BaseElement> instanceType)
    {
        final List<BpmnElementTransformer<?, ?>> transformers = new ArrayList<>();

        final Model bpmnModel = Bpmn.INSTANCE.getBpmnModel();

        ModelElementType modelType = bpmnModel.getType(instanceType);

        do
        {
            final BpmnElementTransformer<?, ?> transformer = TRANSFORMERS.get(modelType.getInstanceType());
            if (transformer != null)
            {
                transformers.add(0, transformer);
            }
        }
        while ((modelType = modelType.getBaseType()) != null);

        TRANSFORMERS_BY_TYPE.put(instanceType, transformers);
    }

    private static void registerTransformer(BpmnElementTransformer<?, ?> baseElementTransformer)
    {
        TRANSFORMERS.put(baseElementTransformer.getType(), baseElementTransformer);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void apply(BaseElement modelElement, ExecutableFlowElement executableElement, ExecutableScope scope)
    {
        final List<BpmnElementTransformer<?, ?>> transformers = TRANSFORMERS_BY_TYPE.get(modelElement.getElementType().getInstanceType());

        if (transformers != null)
        {
            for (BpmnElementTransformer bpmnElementTransformer : transformers)
            {
                bpmnElementTransformer.transform(modelElement, executableElement, scope);
            }
        }
    }
}
