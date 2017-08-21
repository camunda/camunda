/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.jsonpath;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.msgpack.filter.ArrayIndexFilter;
import io.zeebe.msgpack.filter.MapValueWithKeyFilter;
import io.zeebe.msgpack.filter.MsgPackFilter;
import io.zeebe.msgpack.filter.RootCollectionFilter;
import io.zeebe.msgpack.filter.WildcardFilter;
import io.zeebe.msgpack.query.MsgPackFilterContext;
import io.zeebe.msgpack.util.ByteUtil;

public class JsonPathQueryCompiler implements JsonPathTokenVisitor
{
    protected static final int ROOT_COLLECTION_FILTER_ID = 0;
    protected static final int MAP_VALUE_FILTER_ID = 1;
    protected static final int ARRAY_INDEX_FILTER_ID = 2;
    protected static final int WILDCARD_FILTER_ID = 3;

    protected static final MsgPackFilter[] JSON_PATH_FILTERS = new MsgPackFilter[4];

    static
    {
        JSON_PATH_FILTERS[ROOT_COLLECTION_FILTER_ID] = new RootCollectionFilter();
        JSON_PATH_FILTERS[MAP_VALUE_FILTER_ID] = new MapValueWithKeyFilter();
        JSON_PATH_FILTERS[ARRAY_INDEX_FILTER_ID] = new ArrayIndexFilter();
        JSON_PATH_FILTERS[WILDCARD_FILTER_ID] = new WildcardFilter();
    }

    protected JsonPathQuery jsonPathQuery = new JsonPathQuery(JSON_PATH_FILTERS);
    protected JsonPathTokenizer tokenizer = new JsonPathTokenizer();

    protected UnsafeBuffer expressionBuffer = new UnsafeBuffer(0, 0);
    protected ParsingMode mode;

    public JsonPathQuery compile(String jsonPathExpression)
    {
        expressionBuffer.wrap(jsonPathExpression.getBytes(StandardCharsets.UTF_8));
        return compile(expressionBuffer, 0, expressionBuffer.capacity());
    }

    public JsonPathQuery compile(DirectBuffer buffer, int offset, int length)
    {
        // TODO: syntactically validate expression
        jsonPathQuery.wrap(buffer, offset, length);

        mode = ParsingMode.DEFAULT;
        tokenizer.tokenize(buffer, offset, length, this);
        return jsonPathQuery;
    }

    @Override
    public void visit(JsonPathToken type, DirectBuffer valueBuffer, int valueOffset, int valueLength)
    {
        if (!jsonPathQuery.isValid())
        {
            // ignore tokens once query is invalid
            return;
        }

        final MsgPackFilterContext filterInstances = jsonPathQuery.getFilterInstances();

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
                case CHILD_BRACKET_OPERATOR_BEGIN:
                    mode = ParsingMode.SUBORDINATE;
                    return;
                case START_INPUT:
                case END_INPUT:
                case SUBSCRIPT_OPERATOR_END:
                case CHILD_BRACKET_OPERATOR_END:
                    return; // ignore
                default:
                    jsonPathQuery.invalidate(valueOffset, "Unexpected json-path token " + type);
            }

        }
        else if (mode == ParsingMode.SUBORDINATE)
        {
            switch (type)
            {
                case LITERAL:
                    if (ByteUtil.isNumeric(valueBuffer, valueOffset, valueLength))
                    {
                        final int arrayIndex = ByteUtil.parseInteger(valueBuffer, valueOffset, valueLength);
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
                case WILDCARD:
                    filterInstances.appendElement();
                    filterInstances.filterId(WILDCARD_FILTER_ID);
                    return;
                default:
                    jsonPathQuery.invalidate(valueOffset, "Unexpected json-path token " + type);
            }
        }

    }

    protected enum ParsingMode
    {
        DEFAULT,
        SUBORDINATE,
    }

}
