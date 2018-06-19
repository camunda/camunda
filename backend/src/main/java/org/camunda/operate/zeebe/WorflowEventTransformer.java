package org.camunda.operate.zeebe;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.writer.EntityStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.WorkflowResource;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Configuration
@Profile("zeebe")
public class WorflowEventTransformer {

  @Autowired
  private EntityStorage entityStorage;

  public void onWorkflowEvent(String topicName, Workflow workflow, WorkflowResource resource) throws InterruptedException {
    WorkflowEntity workflowEntity = new WorkflowEntity();
    workflowEntity.setId(String.valueOf(workflow.getWorkflowKey()));
    workflowEntity.setBpmnProcessId(workflow.getBpmnProcessId());
    workflowEntity.setVersion(workflow.getVersion());
    workflowEntity.setBpmnXml(resource.getBpmnXml());
    workflowEntity.setName(extractWorkflowName(resource.getBpmnXmlAsStream()));
    entityStorage.getOperateEntititesQueue(topicName).put(workflowEntity);
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
      //TODO
      return null;
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
