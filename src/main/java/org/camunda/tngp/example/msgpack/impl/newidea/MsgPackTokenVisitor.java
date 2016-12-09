package org.camunda.tngp.example.msgpack.impl.newidea;

import org.agrona.BitUtil;
import org.camunda.tngp.example.msgpack.impl.ImmutableIntList;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

public class MsgPackTokenVisitor
{


    protected MsgPackFilter[] filters;
    protected MsgPackFilterContext filterInstances;
    protected int numFilterInstances;

    protected ImmutableIntList matchingPositions = new ImmutableIntList(100);
    protected int matchingContainer = -1;
    protected int matchingContainerStartPosition;

    protected MsgPackTraversalContext context = new MsgPackTraversalContext(30, BitUtil.SIZE_OF_INT);

    public MsgPackTokenVisitor(MsgPackFilter[] filters, MsgPackFilterContext filterInstances)
    {
        this.filters = filters;
        this.filterInstances = filterInstances;
        this.numFilterInstances = filterInstances.size();
    }

    public void visitElement(int position, MsgPackToken currentValue)
    {
        // count current element
        int currentFilter = 0;

        if (context.hasElements())
        {
            context.moveToLastElement();
            context.currentElement(context.currentElement() + 1);
            currentFilter = context.applyingFilter();
        }

        // evaluate filter
        boolean filterMatch = false;
        if (currentFilter >= 0)
        {
            filterInstances.moveTo(currentFilter);
            MsgPackFilter filter = filters[filterInstances.filterId()];
            filterMatch = filter.matches(context, filterInstances.dynamicContext(), currentValue);
        }

        // build new context
        if (MsgPackType.ARRAY == currentValue.getType() || MsgPackType.MAP == currentValue.getType())
        {
            context.appendElement();
            context.currentElement(-1);
            context.numElements(currentValue.getSize());
            context.applyingFilter(-1);
            context.setIsMap(MsgPackType.MAP == currentValue.getType());
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
                    matchingContainer = this.context.size() - 1;
                    matchingContainerStartPosition = position;
                }
            }
            else
            {
                context.applyingFilter(currentFilter + 1);
            }
        }

        // destroy context
        while (context.hasElements() && context.currentElement() + 1 >= context.numElements())
        {

            if (matchingContainer == context.size() - 1)
            {
                matchingPositions.add(matchingContainerStartPosition);
                matchingPositions.add(position + currentValue.getTotalLength());
                matchingContainer = -1;
            }

            context.removeLastElement();
        }
    }

    protected boolean isLastFilter(int filterIndex)
    {
        return filterIndex + 1 == numFilterInstances;
    }

    public ImmutableIntList getMatchingPositions()
    {
        return matchingPositions;
    }

}
