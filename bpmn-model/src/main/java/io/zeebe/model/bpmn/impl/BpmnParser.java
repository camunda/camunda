package io.zeebe.model.bpmn.impl;

import java.io.*;

import javax.xml.bind.*;

import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;

public class BpmnParser
{
    private final Unmarshaller unmarshaller;
    private final Marshaller marshaller;

    public BpmnParser()
    {
        try
        {
            JAXBContext jaxbContext = JAXBContext.newInstance(DefinitionsImpl.class);

            unmarshaller = jaxbContext.createUnmarshaller();
            marshaller = jaxbContext.createMarshaller();

            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        }
        catch (JAXBException e)
        {
            throw new RuntimeException(e);
        }
    }

    public DefinitionsImpl readFromFile(File file)
    {
        try
        {
            final DefinitionsImpl definitions = (DefinitionsImpl) unmarshaller.unmarshal(file);

            return definitions;
        }
        catch (JAXBException e)
        {
            throw new RuntimeException(e);
        }
    }

    public DefinitionsImpl readFromStream(InputStream stream)
    {
        try
        {
            final DefinitionsImpl definitions = (DefinitionsImpl) unmarshaller.unmarshal(stream);

            return definitions;
        }
        catch (JAXBException e)
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

}
