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
package io.zeebe.msgpack.el;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;

public class JsonConditionValidator
{

    public static String validate(JsonCondition condition)
    {
        final List<String> errors = new ArrayList<>();

        validateCondition(errors, condition);

        if (errors.isEmpty())
        {
            return null;
        }
        else
        {
            final StringBuilder builder = new StringBuilder();

            for (String error : errors)
            {
                builder.append(error);
                builder.append("\n");
            }

            return builder.toString();
        }
    }

    private static void validateCondition(List<String> errors, JsonCondition condition)
    {
        if (condition instanceof Comparison)
        {
            validateComparison(errors, (Comparison) condition);
        }
        else if (condition instanceof Operator)
        {
            final Operator operator = (Operator) condition;

            validateCondition(errors, operator.x());
            validateCondition(errors, operator.y());
        }
        else
        {
            errors.add(String.format("Illegal condition: %s", condition));
        }
    }

    private static void validateComparison(List<String> errors, Comparison comparison)
    {
        validateObject(errors, comparison.x());
        validateObject(errors, comparison.y());
    }

    private static void validateObject(List<String> errors, JsonObject object)
    {
        if (object instanceof JsonPath)
        {
            final JsonPath path = (JsonPath) object;
            final JsonPathQuery query = path.query();

            if (!query.isValid())
            {
                errors.add(query.getErrorReason());
            }
        }
    }

}
