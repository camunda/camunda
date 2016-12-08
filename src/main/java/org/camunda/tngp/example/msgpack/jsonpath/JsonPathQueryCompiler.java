package org.camunda.tngp.example.msgpack.jsonpath;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.newidea.ArrayIndexFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MapValueWithKeyFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.RootCollectionFilter;

public class JsonPathQueryCompiler implements JsonPathTokenVisitor
{

    protected JsonPathQuery jsonPathQuery = new JsonPathQuery();
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

        System.out.println("Token: " + type);

        if (mode == ParsingMode.DEFAULT)
        {
            switch (type)
            {
                case ROOT_OBJECT:
                    jsonPathQuery.addFilter(new RootCollectionFilter());
                    System.out.println("adding root filter");
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
                        jsonPathQuery.addFilter(new ArrayIndexFilter(ByteUtil.parseInteger(valueBuffer, valueOffset, valueLength)));
                        System.out.println("adding map key filter");
                    }
                    else
                    {
                        jsonPathQuery.addFilter(new MapValueWithKeyFilter(valueBuffer, valueOffset, valueLength));
                        System.out.println("adding array index filter");
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
