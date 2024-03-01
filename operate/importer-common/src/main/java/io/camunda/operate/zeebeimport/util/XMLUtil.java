/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport.util;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.ProcessFlowNodeEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.camunda.bpm.model.xml.ModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
@Configuration
public class XMLUtil {

  private static final Logger logger = LoggerFactory.getLogger(XMLUtil.class);

  @Bean
  public SAXParserFactory getSAXParserFactory() {
    final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return saxParserFactory;
    } catch (final ParserConfigurationException | SAXException e) {
      logger.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public Optional<ProcessEntity> extractDiagramData(
      final byte[] byteArray, final String bpmnProcessId) {
    final SAXParserFactory saxParserFactory = getSAXParserFactory();
    InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      final ProcessEntity processEntity = handler.getProcessEntity(bpmnProcessId);
      if (processEntity == null) {
        return Optional.empty();
      }
      final Set<String> processChildrenIds = handler.getProcessChildrenIds(bpmnProcessId);
      is = new ByteArrayInputStream(byteArray);
      final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(is);
      final Collection<FlowNode> flowNodes = modelInstance.getModelElementsByType(FlowNode.class);
      flowNodes.stream()
          .filter(x -> processChildrenIds.contains(x.getId()))
          .toList()
          .forEach(
              x ->
                  processEntity
                      .getFlowNodes()
                      .add(new ProcessFlowNodeEntity(x.getId(), x.getName())));
      return Optional.of(processEntity);
    } catch (final ParserConfigurationException | SAXException | IOException | ModelException e) {
      logger.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final String processElement = "process";
    private final List<ProcessEntity> processEntities = new ArrayList<>();
    private final Map<String, Set<String>> processChildrenIds = new LinkedHashMap<>();
    private String currentProcessId = null;

    @Override
    public void startElement(
        final String uri, final String localName, final String qName, final Attributes attributes)
        throws SAXException {
      final String elementId = attributes.getValue("id");
      if (localName.equalsIgnoreCase(processElement)) {
        if (elementId == null) {
          throw new SAXException("Process has null id");
        }
        processEntities.add(
            new ProcessEntity().setBpmnProcessId(elementId).setName(attributes.getValue("name")));
        processChildrenIds.put(elementId, new LinkedHashSet<>());
        currentProcessId = elementId;
      } else if (currentProcessId != null && elementId != null) {
        processChildrenIds.get(currentProcessId).add(elementId);
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName)
        throws SAXException {
      if (localName.equalsIgnoreCase(processElement)) {
        currentProcessId = null;
      }
    }

    public ProcessEntity getProcessEntity(final String processId) {
      return processEntities.stream()
          .filter(x -> Objects.equals(x.getBpmnProcessId(), processId))
          .findFirst()
          .orElse(null);
    }

    public Set<String> getProcessChildrenIds(final String processId) {
      return processChildrenIds.containsKey(processId)
          ? processChildrenIds.get(processId)
          : new HashSet<>();
    }
  }
}
