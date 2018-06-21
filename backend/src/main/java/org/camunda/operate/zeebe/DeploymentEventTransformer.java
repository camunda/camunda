package org.camunda.operate.zeebe;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import io.zeebe.client.api.commands.DeploymentResource;
import io.zeebe.client.api.commands.ResourceType;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.DeploymentState;
import io.zeebe.client.api.subscription.DeploymentEventHandler;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("zeebe")
public class DeploymentEventTransformer extends AbstractEventTransformer implements DeploymentEventHandler {

  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceEventTransformer.class);

  private final static Set<DeploymentState> STATES = new HashSet<>();

  @Autowired
  private EntityStorage entityStorage;

  @Autowired
  private OperateProperties operateProperties;

  static {
    STATES.add(DeploymentState.CREATED);
  }

  @Override
  public void onDeploymentEvent(DeploymentEvent event) throws Exception {
    if (STATES.contains(event.getState())) {

      logger.debug(event.toJson());

      //check that deployment is from one of the configured topics
      final List<String> topics = operateProperties.getZeebe().getTopics();
      if (!topics.contains(event.getDeploymentTopic())) {
        logger.debug("Deployment event won't be processed, as we're not listening for the topic [{}]", event.getDeploymentTopic());
        return;
      }
      Map<String, DeploymentResource> resourcesMap = resourceToMap(event.getResources());
      for (Workflow workflow: event.getDeployedWorkflows()) {
        final WorkflowEntity workflowEntity = createEntity(workflow, resourcesMap.get(workflow.getResourceName()));
        updateMetdataFields(workflowEntity, event);
        entityStorage.getOperateEntititesQueue(event.getDeploymentTopic()).put(workflowEntity);
      }
    }

  }

  public WorkflowEntity createEntity(Workflow workflow, DeploymentResource resource) throws InterruptedException {
    WorkflowEntity workflowEntity = new WorkflowEntity();
    workflowEntity.setId(String.valueOf(workflow.getWorkflowKey()));
    workflowEntity.setBpmnProcessId(workflow.getBpmnProcessId());
    workflowEntity.setVersion(workflow.getVersion());
    if (resource.getResourceType() != null && resource.getResourceType().equals(ResourceType.BPMN_XML)) {
      workflowEntity.setBpmnXml(new String(resource.getResource(), CHARSET));
      workflowEntity.setResourceName(resource.getResourceName());
      workflowEntity.setName(extractWorkflowName(new ByteArrayInputStream(resource.getResource())));
    }
    return workflowEntity;
  }

  private Map<String,DeploymentResource> resourceToMap(List<DeploymentResource> resources) {

    Map<String, DeploymentResource> map = new HashMap<>();
    for (DeploymentResource deploymentResource: resources) {
      if (!map.containsKey(deploymentResource.getResourceName()) ) {    //we ignore the 2nd, 3rd etc resource of the same name, as this is probably the behaviour of Zeebe broker
        map.put(deploymentResource.getResourceName(), deploymentResource);
      }
    }
    return map;
  }

  private String extractWorkflowName(InputStream xmlInputStream) {
    SAXParser saxParser = getSAXParser();
    DeploymentEventTransformer.ExtractNameSaxHandler handler = new DeploymentEventTransformer.ExtractNameSaxHandler();
    try {
      saxParser.parse(xmlInputStream, handler);
      return handler.getWorkflowName();
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

  public static class ExtractNameSaxHandler extends DefaultHandler {

    private String workflowName;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (localName.equalsIgnoreCase("process")) {
        if (attributes.getValue("name") != null) {
          this.workflowName = attributes.getValue("name");
        }
      }
    }

    public String getWorkflowName() {
      return workflowName;
    }
  }

}
