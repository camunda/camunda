package io.zeebe.msgpack.jsonpath;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import io.zeebe.msgpack.util.ByteUtil;

public class JsonPathTokenizer
{

    public static final StaticToken SYMBOL_ROOT_OBJECT = new StaticToken(JsonPathToken.ROOT_OBJECT, "$".getBytes(StandardCharsets.UTF_8));
    public static final StaticToken SYMBOL_CHILD_OPERATOR = new StaticToken(JsonPathToken.CHILD_OPERATOR, ".".getBytes(StandardCharsets.UTF_8));
    public static final StaticToken SYMBOL_RECURSION_OPERATOR = new StaticToken(JsonPathToken.RECURSION_OPERATOR, "..".getBytes(StandardCharsets.UTF_8));
    public static final StaticToken SYMBOL_WILDCARD = new StaticToken(JsonPathToken.WILDCARD, "*".getBytes(StandardCharsets.UTF_8));
    public static final StaticToken SYMBOL_SUBSCRIPT_OPERATOR_BEGIN = new StaticToken(JsonPathToken.SUBSCRIPT_OPERATOR_BEGIN, "[".getBytes(StandardCharsets.UTF_8));
    public static final StaticToken SYMBOL_SUBSCRIPT_OPERATOR_END = new StaticToken(JsonPathToken.SUBSCRIPT_OPERATOR_END, "]".getBytes(StandardCharsets.UTF_8));

    protected static final StaticToken[] STATIC_TOKENS = new StaticToken[6];
    static
    {
        STATIC_TOKENS[0] = SYMBOL_ROOT_OBJECT;
        STATIC_TOKENS[1] = SYMBOL_RECURSION_OPERATOR;
        STATIC_TOKENS[2] = SYMBOL_CHILD_OPERATOR;
        STATIC_TOKENS[3] = SYMBOL_WILDCARD;
        STATIC_TOKENS[4] = SYMBOL_SUBSCRIPT_OPERATOR_BEGIN;
        STATIC_TOKENS[5] = SYMBOL_SUBSCRIPT_OPERATOR_END;
    }

    public void tokenize(DirectBuffer buffer, int offset, int length, JsonPathTokenVisitor tokenVisitor)
    {
        int position = offset;
        int lastStaticTokenEndPosition = position;
        tokenVisitor.visit(JsonPathToken.START_INPUT, buffer, offset, length);

        while (position < length)
        {
            JsonPathToken currentToken = null;
            for (int i = 0; i < STATIC_TOKENS.length && currentToken == null; i++)
            {
                final StaticToken token = STATIC_TOKENS[i];
                final byte[] tokenRepresentation = token.representation;
                if (ByteUtil.equal(tokenRepresentation, buffer, position, tokenRepresentation.length))
                {
                    if (lastStaticTokenEndPosition < position)
                    {
                        tokenVisitor.visit(JsonPathToken.LITERAL, buffer, lastStaticTokenEndPosition, position - lastStaticTokenEndPosition);
                    }

                    tokenVisitor.visit(token.token, buffer, position, tokenRepresentation.length);
                    position += tokenRepresentation.length;
                    lastStaticTokenEndPosition = position;
                    currentToken = token.token;
                }
            }

            if (currentToken == null)
            {
                position++;
            }
        }

        if (lastStaticTokenEndPosition < position)
        {
            tokenVisitor.visit(JsonPathToken.LITERAL, buffer, lastStaticTokenEndPosition, position - lastStaticTokenEndPosition);
        }
        tokenVisitor.visit(JsonPathToken.END_INPUT, buffer, offset, length);
    }

    protected static class StaticToken
    {
        protected JsonPathToken token;
        protected byte[] representation;

        public StaticToken(JsonPathToken token, byte[] representation)
        {
            this.token = token;
            this.representation = representation;
        }
    }

}
