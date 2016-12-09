package org.camunda.tngp.example.msgpack.impl.newidea;

import java.util.Stack;

import org.camunda.tngp.example.msgpack.impl.ImmutableIntList;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

public class MsgPackTokenVisitor
{

    protected Stack<ContainerContext> context = new Stack<>();
    protected MsgPackFilter[] filters;
    protected ImmutableIntList matchingPositions = new ImmutableIntList(100);
    protected int matchingContainer = -1;
    protected int matchingContainerStartPosition;

    public MsgPackTokenVisitor(MsgPackFilter[] filters)
    {
        this.filters = filters;
    }

    public void visitElement(int position, MsgPackToken currentValue)
    {
        // count element in current context
        ContainerContext currentContainer = this.context.isEmpty() ? null : this.context.peek();
        int currentFilter = 0;
        if (currentContainer != null)
        {
            currentContainer.currentElement++;
            currentFilter = currentContainer.applyingFilter;
        }



        // evaluate filter
        boolean filterMatch = false;
        if (currentFilter >= 0)
        {
            MsgPackFilter filter = filters[currentFilter];
            filterMatch = filter.matches(context, currentValue);
        }

        // build new context
        if (MsgPackType.ARRAY == currentValue.getType() || MsgPackType.MAP == currentValue.getType())
        {
//            int filterIndex;
//            if (currentContainer != null)
//            {
//                if (filterMatch && !isLastFilter(currentContainer.applyingFilter))
//                {
//                    filterIndex = currentContainer.applyingFilter + 1;
//                }
//                else
//                {
//                    filterIndex = -1;
//                }
//            }
//            else
//            {
//                filterIndex = 0;
//            }

            currentContainer = new ContainerContext(currentValue.getSize(), -1, -1, MsgPackType.MAP == currentValue.getType());
            this.context.push(currentContainer);
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
                currentContainer.applyingFilter = currentFilter + 1;
            }
        }



        // destroy context
        currentContainer = this.context.isEmpty() ? null : this.context.peek();

        while (currentContainer != null && currentContainer.currentElement + 1 >= currentContainer.numElements)
        {
            if (matchingContainer == this.context.size() - 1)
            {
                matchingPositions.add(matchingContainerStartPosition);
                matchingPositions.add(position + currentValue.getTotalLength());
                matchingContainer = -1;
            }

            this.context.pop();
            currentContainer = this.context.isEmpty() ? null : this.context.peek();
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
