package org.camunda.operate.zeebe;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ZeebeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import io.zeebe.client.api.commands.DeploymentResource;
import io.zeebe.client.api.commands.ResourceType;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.DeploymentState;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.subscription.DeploymentEventHandler;


@Component
public class DeploymentEventTransformer extends AbstractEventTransformer implements DeploymentEventHandler {

  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final Logger logger = LoggerFactory.getLogger(DeploymentEventTransformer.class);

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

    ZeebeUtil.ALL_EVENTS_LOGGER.debug(event.toJson());

    if (STATES.contains(event.getState())) {

      logger.debug(event.toJson());

      //check that deployment is from one of the configured topics
      final List<String> topics = operateProperties.getZeebe().getTopics();

      String deploymentTopic = event.getDeploymentTopic();

      if (topics.contains(deploymentTopic)) {
        Map<String, DeploymentResource> resources = resourceToMap(event.getResources());

        for (Workflow workflow : event.getDeployedWorkflows()) {
          String resourceName = workflow.getResourceName();
          DeploymentResource resource = resources.get(resourceName);

          final WorkflowEntity workflowEntity = createEntity(workflow, resource);
          updateMetadataFields(workflowEntity, event);

          entityStorage.getOperateEntitiesQueue(deploymentTopic).put(workflowEntity);
        }

      } else {
        logger.debug("Deployment event won't be processed, as we're not listening for the topic [{}]", deploymentTopic);
      }

    }

  }

  private void updateMetadataFields(WorkflowEntity operateEntity, Record zeebeRecord) {
    RecordMetadata metadata = zeebeRecord.getMetadata();

    operateEntity.setPartitionId(metadata.getPartitionId());
    operateEntity.setPosition(metadata.getPosition());
    operateEntity.setTopicName(metadata.getTopicName());
  }

  public WorkflowEntity createEntity(Workflow workflow, DeploymentResource resource) throws InterruptedException {
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
      String workflowName = extractWorkflowName(is);
      workflowEntity.setName(workflowName);
    }

    return workflowEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream().collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }

  private String extractWorkflowName(InputStream xmlInputStream) {
    SAXParser saxParser = getSAXParser();
    ExtractNameSaxHandler handler = new ExtractNameSaxHandler();

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
