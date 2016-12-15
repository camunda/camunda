package org.camunda.tngp.msgpack.query;

import java.nio.ByteBuffer;

import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.list.CompactList;
import org.camunda.tngp.msgpack.filter.MsgPackFilter;
import org.camunda.tngp.msgpack.spec.MsgPackToken;
import org.camunda.tngp.msgpack.spec.MsgPackType;

public class MsgPackQueryExecutor implements MsgPackTokenVisitor
{

    protected static final int MAX_TRAVERSAL_DEPTH = 30;
    protected static final int MAX_RESULTS = 1000;

    protected static final int RESULT_SIZE = BitUtil.SIZE_OF_INT * 2;
    protected static final int RESULT_POSITION_OFFSET = 0;
    protected static final int RESULT_LENGTH_OFFSET = BitUtil.SIZE_OF_INT;

    protected MsgPackFilter[] filters;
    protected MsgPackFilterContext filterInstances;
    protected int numFilterInstances;

    protected CompactList matchingPositions;
    protected UnsafeBuffer currentResultView = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer resultWriteBuffer = new UnsafeBuffer(new byte[RESULT_SIZE]);

    protected int matchingContainer = -1;
    protected int matchingContainerStartPosition;

    protected MsgPackTraversalContext context = new MsgPackTraversalContext(MAX_TRAVERSAL_DEPTH, BitUtil.SIZE_OF_INT);

    public MsgPackQueryExecutor()
    {
        this.matchingPositions = new CompactList(RESULT_SIZE, MAX_RESULTS, (size) -> ByteBuffer.allocate(size));
    }

    public void init(MsgPackFilter[] filters, MsgPackFilterContext filterInstances)
    {
        this.filters = filters;
        this.filterInstances = filterInstances;
        this.numFilterInstances = filterInstances.size();
        this.matchingPositions.clear();
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
            final MsgPackFilter filter = filters[filterInstances.filterId()];
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
                    addResult(position, currentValue.getTotalLength());
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
                addResult(matchingContainerStartPosition,
                        position + currentValue.getTotalLength() - matchingContainerStartPosition);
                matchingContainer = -1;
            }

            context.removeLastElement();
        }
    }

    protected boolean isLastFilter(int filterIndex)
    {
        return filterIndex + 1 == numFilterInstances;
    }

    public int numResults()
    {
        return matchingPositions.size();
    }

    public void moveToResult(int index)
    {
        matchingPositions.wrap(index, currentResultView);
    }

    public int currentResultPosition()
    {
        return currentResultView.getInt(RESULT_POSITION_OFFSET);
    }

    public int currentResultLength()
    {
        return currentResultView.getInt(RESULT_LENGTH_OFFSET);
    }

    protected void addResult(int position, int length)
    {
        resultWriteBuffer.putInt(RESULT_POSITION_OFFSET, position);
        resultWriteBuffer.putInt(RESULT_LENGTH_OFFSET, length);
        matchingPositions.add(resultWriteBuffer);
    }

}
