package io.zeebe.msgpack.el;

public class JsonConditionWalker
{

    public static void walk(JsonCondition condition, JsonConditionVisitor visitor)
    {
        if (condition instanceof Comparison)
        {
            visitComparison((Comparison) condition, visitor);
        }
        else if (condition instanceof Operator)
        {
            final Operator operator = (Operator) condition;

            walk(operator.x(), visitor);
            walk(operator.y(), visitor);
        }
        else
        {
            throw new RuntimeException(String.format("Illegal condition: %s", condition));
        }
    }

    private static void visitComparison(Comparison comparison, JsonConditionVisitor visitor)
    {
        visitor.visitObject(comparison.x());
        visitor.visitObject(comparison.y());
    }

}
