package org.camunda.tngp.example.msgpack.impl.newidea;

public class ContainerContext
{

    protected int numElements;
    protected int currentElement;
    protected int applyingFilter;
    protected boolean isMap; // else: array

    public ContainerContext(int numElements, int currentElement, int applyingFilter, boolean isMap)
    {
        this.numElements = numElements;
        this.currentElement = currentElement;
        this.applyingFilter = applyingFilter;
        this.isMap = isMap;
    }

    public int getNumElements()
    {
        return numElements;
    }
    public int getCurrentElement()
    {
        return currentElement;
    }
    public int getApplyingFilter()
    {
        return applyingFilter;
    }
    /**
     * @return else array
     */
    public boolean isMap()
    {
        return isMap;
    }
}
