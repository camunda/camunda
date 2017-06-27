/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.workflow.graph.transformer;

import java.util.Collection;
import java.util.Map;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class ZeebeExtensions
{
    public static final String ZEEBE_NAMESPACE = "http://camunda.org/schema/zeebe/1.0";

    public static final String IO_MAPPING_ELEMENT = "ioMapping";
    public static final String INPUT_MAPPING_ELEMENT = "input";
    public static final String OUTPUT_MAPPING_ELEMENT = "output";
    public static final String MAPPING_ATTRIBUTE_SOURCE = "source";
    public static final String MAPPING_ATTRIBUTE_TARGET = "target";

    public static final String TASK_DEFINITION_ELEMENT = "taskDefinition";
    public static final String TASK_HEADERS_ELEMENT = "taskHeaders";
    public static final String TASK_HEADER_ELEMENT = "header";

    public static final String TASK_TYPE_ATTRIBUTE = "type";
    public static final String TASK_RETRIES_ATTRIBUTE = "retries";
    public static final String TASK_HEADER_KEY_ATTRIBUTE = "key";
    public static final String TASK_HEADER_VALUE_ATTRIBUTE = "value";

    public static ZeebeModelInstance wrap(BpmnModelInstance modelInstance)
    {
        // TODO #202 - replace by real Zeebe model instance
        return new ZeebeModelInstance(modelInstance);
    }

    public static class ZeebeModelInstance implements BpmnModelInstance
    {
        protected BpmnModelInstance wrappedInstance;

        public ZeebeModelInstance(BpmnModelInstance wrappedInstance)
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

        public ZeebeModelInstance taskDefinition(String activityId, String taskType, int retries)
        {
            final ExtensionElements extensionElements = getExtensionElements(activityId);
            final ModelElementInstance taskDefinition = extensionElements.addExtensionElement(ZEEBE_NAMESPACE, TASK_DEFINITION_ELEMENT);

            taskDefinition.setAttributeValue(TASK_TYPE_ATTRIBUTE, taskType);
            taskDefinition.setAttributeValue(TASK_RETRIES_ATTRIBUTE, String.valueOf(retries));

            return this;
        }

        public ZeebeModelInstance taskHeaders(String activityId, Map<String, String> headers)
        {
            final ExtensionElements extensionElements = getExtensionElements(activityId);
            final ModelElementInstance taskHeaders = extensionElements.addExtensionElement(ZEEBE_NAMESPACE, TASK_HEADERS_ELEMENT);

            headers.forEach((k, v) ->
            {
                final DomElement taskHeader = wrappedInstance.getDocument().createElement(ZEEBE_NAMESPACE, TASK_HEADER_ELEMENT);

                taskHeader.setAttribute(TASK_HEADER_KEY_ATTRIBUTE, k);
                taskHeader.setAttribute(TASK_HEADER_VALUE_ATTRIBUTE, v);

                taskHeaders.getDomElement().appendChild(taskHeader);
            });

            return this;
        }

        public ZeebeModelInstance ioMapping(String activityId, Map<String, String> inputMappings, Map<String, String> outputMappings)
        {
            final ExtensionElements extensionElements = getExtensionElements(activityId);
            final ModelElementInstance ioMapping = extensionElements.addExtensionElement(ZEEBE_NAMESPACE, IO_MAPPING_ELEMENT);

            if (inputMappings != null)
            {
                inputMappings.forEach((k, v) -> addMappingElement(ioMapping, INPUT_MAPPING_ELEMENT, k, v));
            }
            if (outputMappings != null)
            {
                outputMappings.forEach((k, v) -> addMappingElement(ioMapping, OUTPUT_MAPPING_ELEMENT, k, v));
            }

            return this;
        }

        public IOMappingBuilder ioMapping(String activityId)
        {
            final ExtensionElements extensionElements = getExtensionElements(activityId);
            final ModelElementInstance ioMapping = extensionElements.addExtensionElement(ZEEBE_NAMESPACE, IO_MAPPING_ELEMENT);
            return new IOMappingBuilder(this, ioMapping);
        }

        private void addMappingElement(ModelElementInstance ioMapping, String mappingElement, String source, String target)
        {
            final DomElement mapping = wrappedInstance.getDocument().createElement(ZEEBE_NAMESPACE, mappingElement);
            mapping.setAttribute(MAPPING_ATTRIBUTE_SOURCE, source);
            mapping.setAttribute(MAPPING_ATTRIBUTE_TARGET, target);
            ioMapping.getDomElement().appendChild(mapping);
        }

        private ExtensionElements getExtensionElements(String activityId)
        {
            final ModelElementInstance taskElement = wrappedInstance.getModelElementById(activityId);

            ExtensionElements extensionElements = (ExtensionElements) taskElement.getUniqueChildElementByType(ExtensionElements.class);
            if (extensionElements == null)
            {
                extensionElements = wrappedInstance.newInstance(ExtensionElements.class);
                taskElement.addChildElement(extensionElements);
            }
            return extensionElements;
        }

    }

}
