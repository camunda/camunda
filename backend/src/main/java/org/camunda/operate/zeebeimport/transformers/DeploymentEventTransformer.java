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
package org.camunda.operate.zeebeimport.transformers;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.operate.entities.ActivityEntity;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.zeebeimport.record.value.DeploymentRecordValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import io.zeebe.exporter.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.record.value.deployment.DeploymentResource;
import io.zeebe.exporter.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;

@Component
public class DeploymentEventTransformer implements AbstractRecordTransformer {

  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final Logger logger = LoggerFactory.getLogger(DeploymentEventTransformer.class);

  private final static Set<String> STATES = new HashSet<>();

  static {
    STATES.add(DeploymentIntent.CREATED.name());
  }

  @Override
  public List<OperateZeebeEntity> convert(io.zeebe.exporter.record.Record record) {

    List<OperateZeebeEntity> result = new ArrayList<>();

//    ZeebeUtil.ALL_EVENTS_LOGGER.debug(event.toJson());
    final String intentStr = record.getMetadata().getIntent().name();

    if (STATES.contains(intentStr)) {

//      logger.debug(event.toJson());

      DeploymentRecordValueImpl recordValue = (DeploymentRecordValueImpl)record.getValue();

      Map<String, DeploymentResource> resources = resourceToMap(recordValue.getResources());

      for (DeployedWorkflow workflow : recordValue.getDeployedWorkflows()) {
        String resourceName = workflow.getResourceName();
        DeploymentResource resource = resources.get(resourceName);

        final WorkflowEntity workflowEntity = createEntity(workflow, resource);

        workflowEntity.setKey(record.getKey());

        result.add(workflowEntity);
      }

    }
    return result;

  }

  public WorkflowEntity createEntity(DeployedWorkflow workflow, DeploymentResource resource) {
    WorkflowEntity workflowEntity = new WorkflowEntity();

    workflowEntity.setId(String.valueOf(workflow.getWorkflowKey()));
    workflowEntity.setBpmnProcessId(workflow.getBpmnProcessId());
    workflowEntity.setVersion(workflow.getVersion());

    ResourceType resourceType = resource.getResourceType();
    if (resourceType != null && resourceType.equals(ResourceType.BPMN_XML)) {
      byte[] byteArray = resource.getResource();

      String bpmn = new String(byteArray, CHARSET);
      workflowEntity.setBpmnXml(bpmn);

      String resourceName = resource.getResourceName();
      workflowEntity.setResourceName(resourceName);

      InputStream is = new ByteArrayInputStream(byteArray);
      final WorkflowEntity diagramData = extractDiagramData(is);
      workflowEntity.setName(diagramData.getName());
    }

    return workflowEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream().collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }

  public WorkflowEntity extractDiagramData(InputStream xmlInputStream) {
    SAXParser saxParser = getSAXParser();
    BpmnXmlParserHandler handler = new BpmnXmlParserHandler();

    try {
      saxParser.parse(xmlInputStream, handler);
      return handler.getWorkflowEntity();
    } catch (SAXException | IOException e) {
      // just return null
    }

    return null;
  }

  @Bean
  public SAXParser getSAXParser() {
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      return saxParserFactory.newSAXParser();
    } catch (ParserConfigurationException | SAXException e) {
      logger.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    WorkflowEntity workflowEntity = new WorkflowEntity();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (localName.equalsIgnoreCase("process")) {
        if (attributes.getValue("name") != null) {
          workflowEntity.setName(attributes.getValue("name"));
        }
      }
    }

    public WorkflowEntity getWorkflowEntity() {
      return workflowEntity;
    }
  }

}
