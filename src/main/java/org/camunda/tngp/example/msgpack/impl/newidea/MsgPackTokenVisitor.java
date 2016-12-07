package org.camunda.tngp.example.msgpack.impl.newidea;

import java.util.Stack;

import org.camunda.tngp.example.msgpack.impl.ImmutableIntList;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

public class MsgPackTokenVisitor
{

    protected Stack<ContainerContext> context = new Stack<>();
    protected MsgPackFilter[] filters;
    protected ImmutableIntList matchingPositions = new ImmutableIntList(100);

    public MsgPackTokenVisitor(MsgPackFilter[] filters)
    {
        this.filters = filters;
    }

    public void visitElement(int position, MsgPackToken currentValue)
    {
        // count element in current context
        ContainerContext currentContainer = this.context.isEmpty() ? null : this.context.peek();
        if (currentContainer != null)
        {
            currentContainer.currentElement++;
        }

        // evaluate filter
        boolean filterMatch = false;
        if (currentContainer != null && currentContainer.applyingFilter >= 0)
        {
            MsgPackFilter filter = filters[currentContainer.applyingFilter];
            filterMatch = filter.matches(context, currentValue);
        }

        if (filterMatch && isLastFilter(currentContainer.applyingFilter))
        {
            matchingPositions.add(position);
        }


        // build new context
        if (MsgPackType.ARRAY == currentValue.getType() || MsgPackType.MAP == currentValue.getType())
        {
            int filterIndex;
            if (currentContainer != null)
            {
                if (filterMatch)
                {
                    filterIndex = currentContainer.applyingFilter + 1;
                }
                else
                {
                    filterIndex = -1;
                }
            }
            else
            {
                filterIndex = 0;
            }

            ContainerContext context = new ContainerContext(currentValue.getSize(), -1, filterIndex, MsgPackType.MAP == currentValue.getType());
            this.context.push(context);
        }

        // destroy context
        currentContainer = this.context.isEmpty() ? null : this.context.peek();

        while (currentContainer != null && currentContainer.currentElement + 1 >= currentContainer.numElements)
        {
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
