package org.camunda.tngp.example.msgpack.impl.newidea;

import org.camunda.tngp.example.msgpack.impl.ImmutableIntList;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

public class MsgPackTokenVisitor
{

    protected MsgPackFilter[] filters;
    protected ImmutableIntList matchingPositions = new ImmutableIntList(100);
    protected int matchingContainer = -1;
    protected int matchingContainerStartPosition;

    protected MsgPackTraversalContext context2 = new MsgPackTraversalContext(30);

    public MsgPackTokenVisitor(MsgPackFilter[] filters)
    {
        this.filters = filters;
    }

    public void visitElement(int position, MsgPackToken currentValue)
    {
        // count current element
        int currentFilter = 0;

        if (context2.hasElements())
        {
            context2.moveToLastElement();
            context2.currentElement(context2.currentElement() + 1);
            currentFilter = context2.applyingFilter();
        }

        // evaluate filter
        boolean filterMatch = false;
        if (currentFilter >= 0)
        {
            MsgPackFilter filter = filters[currentFilter];
            filterMatch = filter.matches(context2, currentValue);
        }

        // build new context
        if (MsgPackType.ARRAY == currentValue.getType() || MsgPackType.MAP == currentValue.getType())
        {
            context2.appendElement();
            context2.currentElement(-1);
            context2.numElements(currentValue.getSize());
            context2.applyingFilter(-1);
            context2.setIsMap(MsgPackType.MAP == currentValue.getType());
        }

        // post-process filter match
        if (filterMatch)
        {
            if (isLastFilter(currentFilter))
            {
                if (currentValue.getType().isScalar())
                {
                    matchingPositions.add(position);
                    matchingPositions.add(position + currentValue.getTotalLength());
                }
                else
                {
                    matchingContainer = this.context2.size() - 1;
                    matchingContainerStartPosition = position;
                }
            }
            else
            {
                context2.applyingFilter(currentFilter + 1);
            }
        }

        // destroy context
        while (context2.hasElements() && context2.currentElement() + 1 >= context2.numElements())
        {

            if (matchingContainer == context2.size() - 1)
            {
                matchingPositions.add(matchingContainerStartPosition);
                matchingPositions.add(position + currentValue.getTotalLength());
                matchingContainer = -1;
            }

            context2.removeLastElement();
        }
    }

    protected boolean isLastFilter(int filterIndex)
    {
        return filterIndex + 1 == filters.length;
    }

    public ImmutableIntList getMatchingPositions()
    {
        return matchingPositions;
    }

}
