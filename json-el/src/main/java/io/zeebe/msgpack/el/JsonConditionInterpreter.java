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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackType;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class JsonConditionInterpreter {
  private final MsgPackQueryExecutor visitor = new MsgPackQueryExecutor();
  private final MsgPackTraverser traverser = new MsgPackTraverser();

  private final MsgPackReader msgPackReader1 = new MsgPackReader();
  private final MsgPackReader msgPackReader2 = new MsgPackReader();

  private final JsonPathCache cache = new JsonPathCache();

  public boolean eval(final CompiledJsonCondition condition, final DirectBuffer json) {
    cache.wrap(json);
    try {
      return evalCondition(condition.getCondition(), json);
    } catch (Exception e) {
      throw new JsonConditionException(condition, e);
    }
  }

  private boolean evalCondition(final JsonCondition condition, final DirectBuffer json) {
    final boolean isFulFilled;

    if (condition instanceof Comparison) {
      isFulFilled = evalComparison((Comparison) condition, json);
    } else if (condition instanceof Disjunction) {
      final Disjunction disjunction = (Disjunction) condition;

      isFulFilled = evalCondition(disjunction.x(), json) || evalCondition(disjunction.y(), json);
    } else if (condition instanceof Conjunction) {
      final Conjunction conjunction = (Conjunction) condition;

      isFulFilled = evalCondition(conjunction.x(), json) && evalCondition(conjunction.y(), json);
    } else {
      throw new RuntimeException(String.format("Illegal condition: %s", condition));
    }

    return isFulFilled;
  }

  private boolean evalComparison(Comparison comparison, DirectBuffer json) {
    final MsgPackToken x = getToken(comparison.x(), json, msgPackReader1);
    final MsgPackToken y = getToken(comparison.y(), json, msgPackReader2);

    if (comparison instanceof Equal) {
      return equals(x, y);
    } else if (comparison instanceof NotEqual) {
      return notEquals(x, y);
    } else if (comparison instanceof LessThan) {
      return lessThan(x, y);
    } else if (comparison instanceof LessOrEqual) {
      return lessThanOrEqual(x, y);
    } else if (comparison instanceof GreaterThan) {
      return greaterThan(x, y);
    } else if (comparison instanceof GreaterOrEqual) {
      return greaterThanOrEqual(x, y);
    } else {
      throw new RuntimeException(String.format("Illegal comparison: %s", comparison));
    }
  }

  private MsgPackToken getToken(JsonObject value, DirectBuffer json, MsgPackReader msgPackReader) {
    if (value instanceof JsonConstant) {
      final JsonConstant constant = (JsonConstant) value;

      return constant.token();
    } else if (value instanceof JsonPath) {
      final JsonPath jsonPath = (JsonPath) value;

      return getPathResult(jsonPath, json, msgPackReader);
    } else {
      throw new RuntimeException(String.format("Illegal value: %s", value));
    }
  }

  private MsgPackToken getPathResult(
      final JsonPath path, DirectBuffer json, MsgPackReader msgPackReader) {
    final int pathId = path.id();
    // id > 0 if the path is used more than once in the condition
    final boolean cachable = pathId > 0;

    final DirectBuffer resultBuffer = cachable ? cache.get(pathId) : null;

    if (resultBuffer != null) {
      if (resultBuffer.capacity() == 0) {
        return MsgPackToken.NIL;
      }
      msgPackReader.wrap(resultBuffer, 0, resultBuffer.capacity());
    } else {
      if (!readQueryResult(path.query(), json)) {
        if (cachable) {
          cache.put(pathId, 0, 0);
        }
        return MsgPackToken.NIL;
      }

      final int offset = visitor.currentResultPosition();
      final int length = visitor.currentResultLength();

      msgPackReader.wrap(json, offset, length);

      if (cachable) {
        cache.put(pathId, offset, length);
      }
    }

    return msgPackReader.readToken();
  }

  private boolean readQueryResult(JsonPathQuery query, DirectBuffer json) {
    visitor.init(query.getFilters(), query.getFilterInstances());
    traverser.wrap(json, 0, json.capacity());

    traverser.traverse(visitor);

    if (visitor.numResults() == 0) {
      return false;
    } else if (visitor.numResults() > 1) {
      // such a JSON path expression should not be valid
      throw new JsonConditionException(
          String.format(
              "JSON path '%s' has more than one result.", bufferAsString(query.getExpression())));
    }

    visitor.moveToResult(0);
    return true;
  }

  private boolean equals(MsgPackToken x, MsgPackToken y) {
    if (x.getType() == MsgPackType.NIL || y.getType() == MsgPackType.NIL) {
      return x.getType() == y.getType();
    } else {
      ensureSameType(x, y);

      switch (x.getType()) {
        case STRING:
          return BufferUtil.equals(x.getValueBuffer(), y.getValueBuffer());

        case BOOLEAN:
          return x.getBooleanValue() == y.getBooleanValue();

        case INTEGER:
          return x.getIntegerValue() == y.getIntegerValue();

        case FLOAT:
          return x.getFloatValue() == y.getFloatValue();

        default:
          throw new JsonConditionException(
              String.format("Cannot compare value of type: %s", x.getType()));
      }
    }
  }

  private boolean notEquals(MsgPackToken x, MsgPackToken y) {
    return !equals(x, y);
  }

  private boolean lessThan(MsgPackToken x, MsgPackToken y) {
    ensureSameType(x, y);
    ensureNumber(x);

    if (x.getType() == MsgPackType.INTEGER) {
      return x.getIntegerValue() < y.getIntegerValue();
    } else {
      return x.getFloatValue() < y.getFloatValue();
    }
  }

  private boolean lessThanOrEqual(MsgPackToken x, MsgPackToken y) {
    ensureSameType(x, y);
    ensureNumber(x);

    if (x.getType() == MsgPackType.INTEGER) {
      return x.getIntegerValue() <= y.getIntegerValue();
    } else {
      return x.getFloatValue() <= y.getFloatValue();
    }
  }

  private boolean greaterThan(MsgPackToken x, MsgPackToken y) {
    ensureSameType(x, y);
    ensureNumber(x);

    if (x.getType() == MsgPackType.INTEGER) {
      return x.getIntegerValue() > y.getIntegerValue();
    } else {
      return x.getFloatValue() > y.getFloatValue();
    }
  }

  private boolean greaterThanOrEqual(MsgPackToken x, MsgPackToken y) {
    ensureSameType(x, y);
    ensureNumber(x);

    if (x.getType() == MsgPackType.INTEGER) {
      return x.getIntegerValue() >= y.getIntegerValue();
    } else {
      return x.getFloatValue() >= y.getFloatValue();
    }
  }

  private void ensureSameType(MsgPackToken x, MsgPackToken y) {
    // transform number types for comparison
    if (x.getType() == MsgPackType.INTEGER && y.getType() == MsgPackType.FLOAT) {
      x.setType(MsgPackType.FLOAT);
      x.setValue((double) x.getIntegerValue());
    } else if (x.getType() == MsgPackType.FLOAT && y.getType() == MsgPackType.INTEGER) {
      y.setType(MsgPackType.FLOAT);
      y.setValue((double) y.getIntegerValue());
    } else if (x.getType() != y.getType()) {
      throw new JsonConditionException(
          String.format(
              "Cannot compare values of different types: %s and %s", x.getType(), y.getType()));
    }
  }

  private void ensureNumber(MsgPackToken x) {
    if (x.getType() != MsgPackType.INTEGER && x.getType() != MsgPackType.FLOAT) {
      throw new JsonConditionException(
          String.format("Cannot compare values. Expected number but found: %s", x.getType()));
    }
  }
}
