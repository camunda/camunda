import { BaseCstVisitor } from "./parser";
import { PropertyPath } from "../utils/path";
import { ContextProvider } from "./types";
export interface ExpressionContext {
    contextProvider: ContextProvider;
}
/**
 * This evaluates an expression by operation on the CST produced by the parser.
 */
export declare class ExpressionEvaluator extends BaseCstVisitor {
    constructor();
    expression(ctx: any, context: ExpressionContext): any;
    subExpression(ctx: any, context: ExpressionContext): any;
    contextAccess(ctx: any, context: ExpressionContext): any;
    protected getContextValue(contextName: string, path: PropertyPath, context: ExpressionContext): any;
    contextMember(ctx: any, { path, context }: {
        path: PropertyPath;
        context: ExpressionContext;
    }): any;
    contextDotMember(ctx: any, path: PropertyPath): void;
    contextBoxMember(ctx: any, { path, context }: {
        path: PropertyPath;
        context: ExpressionContext;
    }): void;
    logicalGrouping(ctx: any, context: ExpressionContext): any;
    array(ctx: any): any[];
    functionCall(ctx: any, context: ExpressionContext): any;
    functionParameters(ctx: any, context: ExpressionContext): any;
    value(ctx: any): any;
    booleanValue(ctx: any): boolean;
    private _coerceValue;
    private _removeQuotes;
}
export declare const evaluator: ExpressionEvaluator;
