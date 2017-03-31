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
package org.camunda.tngp.broker.workflow.graph.transformer;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class TngpExtensions
{
    public static final String TNGP_NAMESPACE = "http://camunda.org/schema/tngp/1.0";

    public static final String TASK_DEFINITION_ELEMENT = "taskDefiniton";

    public static final String TASK_TYPE_ATTRIBUTE = "type";

    public static final String TASK_RETRIES_ATTRIBUTE = "retries";


    public static TngpModelInstance wrap(BpmnModelInstance modelInstance)
    {
        // TODO #202 - replace by real TNGP model instance
        return new TngpModelInstance(modelInstance);
    }

    public static class TngpModelInstance implements BpmnModelInstance
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

        public TngpModelInstance taskDefinition(String activityId, String taskType, int retries)
        {
            final ModelElementInstance taskElement = wrappedInstance.getModelElementById(activityId);

            final ExtensionElements extensionElements = wrappedInstance.newInstance(ExtensionElements.class);
            final ModelElementInstance taskDefinition = extensionElements.addExtensionElement(TNGP_NAMESPACE, TASK_DEFINITION_ELEMENT);

            taskDefinition.setAttributeValue(TASK_TYPE_ATTRIBUTE, taskType);
            taskDefinition.setAttributeValue(TASK_RETRIES_ATTRIBUTE, String.valueOf(retries));

            taskElement.addChildElement(extensionElements);

            return this;
        }

    }

}
