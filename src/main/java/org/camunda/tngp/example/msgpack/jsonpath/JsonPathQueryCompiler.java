package org.camunda.tngp.example.msgpack.jsonpath;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.newidea.ArrayIndexFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MapValueWithKeyFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilterContext;
import org.camunda.tngp.example.msgpack.impl.newidea.RootCollectionFilter;

public class JsonPathQueryCompiler implements JsonPathTokenVisitor
{
    protected static final int ROOT_COLLECTION_FILTER_ID = 0;
    protected static final int MAP_VALUE_FILTER_ID = 1;
    protected static final int ARRAY_INDEX_FILTER_ID = 2;

    protected static final MsgPackFilter[] JSON_PATH_FILTERS = new MsgPackFilter[3];

    static {
        JSON_PATH_FILTERS[ROOT_COLLECTION_FILTER_ID] = new RootCollectionFilter();
        JSON_PATH_FILTERS[MAP_VALUE_FILTER_ID] = new MapValueWithKeyFilter();
        JSON_PATH_FILTERS[ARRAY_INDEX_FILTER_ID] = new ArrayIndexFilter();
    }

    protected JsonPathQuery jsonPathQuery = new JsonPathQuery(JSON_PATH_FILTERS);
    protected JsonPathTokenizer tokenizer = new JsonPathTokenizer();

    protected UnsafeBuffer expressionBuffer = new UnsafeBuffer(0, 0);
    protected ParsingMode mode;

    public JsonPathQuery compile(String jsonPathExpression)
    {
        // TODO: syntactically validate expression

        jsonPathQuery.reset();
        expressionBuffer.wrap(jsonPathExpression.getBytes(StandardCharsets.UTF_8));
        mode = ParsingMode.DEFAULT;
        tokenizer.tokenize(expressionBuffer, 0, expressionBuffer.capacity(), this);

        return jsonPathQuery;
    }

    @Override
    public void visit(JsonPathToken type, DirectBuffer valueBuffer, int valueOffset, int valueLength)
    {

        MsgPackFilterContext filterInstances = jsonPathQuery.getFilterInstances();

        if (mode == ParsingMode.DEFAULT)
        {
            switch (type)
            {
                case ROOT_OBJECT:
                    filterInstances.appendElement();
                    filterInstances.filterId(ROOT_COLLECTION_FILTER_ID);
                    return;
                case CHILD_OPERATOR:
                case SUBSCRIPT_OPERATOR_BEGIN:
                    mode = ParsingMode.SUBORDINATE;
                    return;
                case START_INPUT:
                case END_INPUT:
                case SUBSCRIPT_OPERATOR_END:
                    return; // ignore
                default:
                    throw new RuntimeException("Unexpected json-path token " + type);
            }

        }
        else if (mode == ParsingMode.SUBORDINATE)
        {
            switch (type)
            {
                case LITERAL:
                    if (ByteUtil.isNumeric(valueBuffer, valueOffset, valueLength))
                    {
                        int arrayIndex = ByteUtil.parseInteger(valueBuffer, valueOffset, valueLength);
                        filterInstances.appendElement();
                        filterInstances.filterId(ARRAY_INDEX_FILTER_ID);
                        ArrayIndexFilter.encodeDynamicContext(filterInstances.dynamicContext(), arrayIndex);
                    }
                    else
                    {
                        filterInstances.appendElement();
                        filterInstances.filterId(MAP_VALUE_FILTER_ID);
                        MapValueWithKeyFilter.encodeDynamicContext(filterInstances.dynamicContext(),
                                valueBuffer, valueOffset, valueLength);
                    }
                    mode = ParsingMode.DEFAULT;
                    return;
                case START_INPUT:
                case END_INPUT:
                    return; // ignore
                default:
                    throw new RuntimeException("Unexpected json-path token " + type);
            }
        }

    }

    protected enum ParsingMode
    {
        DEFAULT,
        SUBORDINATE,
    }

}
