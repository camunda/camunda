/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.tasklist.zeebeimport.util;

import io.camunda.tasklist.entities.ProcessFlowNodeEntity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtil.class);

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
    } catch (ParserConfigurationException | SAXException e) {
      throw new RuntimeException("Error creating SAXParser: " + e.getMessage(), e);
    }
  }

  public void extractDiagramData(
      byte[] byteArray,
      Predicate<String> processIdPredicate,
      Consumer<String> nameConsumer,
      Consumer<ProcessFlowNodeEntity> flowNodeConsumer,
      BiConsumer<String, String> userTaskFormConsumer,
      Consumer<String> formKeyConsumer,
      Consumer<String> formIdConsumer,
      Consumer<Boolean> startedByFormConsumer) {
    final SAXParserFactory saxParserFactory = getSAXParserFactory();
    final InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler =
        new BpmnXmlParserHandler(
            processIdPredicate,
            nameConsumer,
            flowNodeConsumer,
            userTaskFormConsumer,
            formKeyConsumer,
            formIdConsumer,
            startedByFormConsumer);
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final Predicate<String> processIdPredicate;
    private final Consumer<String> nameConsumer;
    private final Consumer<ProcessFlowNodeEntity> flowNodeConsumer;
    private final BiConsumer<String, String> userTaskFormConsumer;
    private final Consumer<String> formKeyConsumer;
    private final Consumer<String> formIdConsumer;
    private final Consumer<Boolean> startedByFormConsumer;
    private boolean isUserTaskForm = false;

    private boolean isStartEvent = false;
    private boolean isCurrentProcess = false;

    private String userTaskFormId;
    private StringBuilder userTaskFormJson = new StringBuilder();

    public BpmnXmlParserHandler(
        final Predicate<String> processIdPredicate,
        final Consumer<String> nameConsumer,
        final Consumer<ProcessFlowNodeEntity> flowNodeConsumer,
        final BiConsumer<String, String> userTaskFormConsumer,
        final Consumer<String> formKeyConsumer,
        final Consumer<String> formIdConsumer,
        final Consumer<Boolean> startedByFormConsumer) {
      this.processIdPredicate = processIdPredicate;
      this.nameConsumer = nameConsumer;
      this.flowNodeConsumer = flowNodeConsumer;
      this.userTaskFormConsumer = userTaskFormConsumer;
      this.formKeyConsumer = formKeyConsumer;
      this.formIdConsumer = formIdConsumer;
      this.startedByFormConsumer = startedByFormConsumer;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      if ("process".equalsIgnoreCase(localName)) {
        if (processIdPredicate.test(attributes.getValue("id"))) {
          isCurrentProcess = true;
          if (attributes.getValue("name") != null) {
            nameConsumer.accept(attributes.getValue("name"));
          }
        }
      } else if (!isCurrentProcess) {
        // element is not part of the current imported process, skip it
      } else if ("userTask".equalsIgnoreCase(localName)) {
        if (attributes.getValue("name") != null) {
          final ProcessFlowNodeEntity flowNodeEntity =
              new ProcessFlowNodeEntity(attributes.getValue("id"), attributes.getValue("name"));
          flowNodeConsumer.accept(flowNodeEntity);
        }
      } else if ("userTaskForm".equalsIgnoreCase(localName)) {
        isUserTaskForm = true;
        if (attributes.getValue("id") != null) {
          userTaskFormId = attributes.getValue("id");
        }
      } else if ("startEvent".equalsIgnoreCase(localName)) {
        isStartEvent = true;
      } else if (isStartEvent) {
        if ("formDefinition".equalsIgnoreCase(localName)) {
          if (attributes.getValue("formKey") != null) {
            formKeyConsumer.accept(attributes.getValue("formKey"));
          }
          if (attributes.getValue("formId") != null) {
            formIdConsumer.accept(attributes.getValue("formId"));
          }
        } else if ("property".equalsIgnoreCase(localName)) {
          final String name = attributes.getValue("name");
          final String value = attributes.getValue("value");
          if ("publicAccess".equalsIgnoreCase(name) && "true".equalsIgnoreCase(value)) {
            startedByFormConsumer.accept(true);
          }
        }
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
      if (!isCurrentProcess) {
        // element is not part of the current imported process, skip it
      } else if ("process".equalsIgnoreCase(localName)) {
        isCurrentProcess = false;
      } else if ("userTaskForm".equalsIgnoreCase(localName)) {
        userTaskFormConsumer.accept(userTaskFormId, userTaskFormJson.toString());
        isUserTaskForm = false;
        userTaskFormJson = new StringBuilder();
      } else if ("startEvent".equalsIgnoreCase(localName)) {
        isStartEvent = false;
      }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
      if (isUserTaskForm) {
        userTaskFormJson.append(new String(ch, start, length));
      }
    }
  }
}
