package io.zeebe.model.bpmn.impl.instance;

public class BaseElement
{
    private String elementName;
    private String namespace;
    private Integer lineNumber;

    public Integer getLineNumber()
    {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber)
    {
        this.lineNumber = lineNumber;
    }

    public String getElementName()
    {
        return elementName;
    }

    public void setElementName(String elementName)
    {
        this.elementName = elementName;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public void setNamespace(String namespace)
    {
        this.namespace = namespace;
    }
}
