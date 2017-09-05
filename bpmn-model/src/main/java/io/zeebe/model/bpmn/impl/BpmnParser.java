/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.io.*;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.bind.Unmarshaller.Listener;
import javax.xml.stream.*;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.instance.BaseElement;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import org.xml.sax.SAXException;

public class BpmnParser
{
    private final Unmarshaller unmarshaller;
    private final Marshaller marshaller;

    private final XMLInputFactory xmlInputFactory;
    private final BaseElementListener baseElementListener;

    public BpmnParser()
    {
        try
        {
            final JAXBContext jaxbContext = JAXBContext.newInstance(DefinitionsImpl.class);

            final SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final URL bpmnSchema = getClass().getResource("/" + BpmnConstants.BPMN_20_SCHEMA_LOCATION);
            ensureNotNull("BPMN schema", bpmnSchema);
            final Schema schema = schemaFactory.newSchema(bpmnSchema);

            baseElementListener = new BaseElementListener();

            unmarshaller = jaxbContext.createUnmarshaller();
            unmarshaller.setSchema(schema);
            unmarshaller.setListener(baseElementListener);

            marshaller = jaxbContext.createMarshaller();
            marshaller.setSchema(schema);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

            xmlInputFactory = XMLInputFactory.newFactory();
        }
        catch (JAXBException | SAXException e)
        {
            throw new RuntimeException(e);
        }
    }

    public DefinitionsImpl readFromFile(File file)
    {
        try
        {
            return readFromStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    public DefinitionsImpl readFromStream(InputStream stream)
    {
        try
        {
            final XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(stream);
            baseElementListener.wrap(xmlStreamReader);

            final DefinitionsImpl definitions = (DefinitionsImpl) unmarshaller.unmarshal(xmlStreamReader);

            return definitions;
        }
        catch (JAXBException | XMLStreamException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String convertToString(DefinitionsImpl definitionsImpl)
    {
        final StringWriter writer = new StringWriter();

        try
        {
            marshaller.marshal(definitionsImpl, writer);
        }
        catch (JAXBException e)
        {
            throw new RuntimeException(e);
        }

        return writer.toString();
    }

    /**
     * Add metadata for validation.
     */
    private class BaseElementListener extends Listener
    {
        private XMLStreamReader xsr;

        public void wrap(XMLStreamReader xsr)
        {
            this.xsr = xsr;
        }

        @Override
        public void beforeUnmarshal(Object target, Object parent)
        {
            final int lineNumber = xsr.getLocation().getLineNumber();
            final String localName = xsr.getLocalName();
            final String prefix = xsr.getPrefix();

            if (target instanceof BaseElement)
            {
                final BaseElement element = (BaseElement) target;

                element.setNamespace(prefix);
                element.setElementName(localName);
                element.setLineNumber(lineNumber);
            }
        }
    }

}
