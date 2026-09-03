import { ILexingError, IRecognitionException } from "chevrotain";
import { ContextProvider } from "./types";
export { ExpressionParser } from "./parser";
export declare class ExpressionError extends Error {
    lexErrors: ILexingError[];
    parseErrors: IRecognitionException[];
    constructor(lexErrors: ILexingError[], parseErrors: IRecognitionException[]);
}
export declare function parseExpression(expression: string): import("chevrotain").CstNode;
/**
 * Evaluates a single expression with the given context
 *
 * @param expression Expression to evaluate, with or without ${{ }} marker
 * @param contextProvider Context provider for evaluation
 */
export declare function evaluateExpression(expression: string, contextProvider: ContextProvider): any;
/**
 * Evaluates and replaces zero or more expressions in a string. Expressions must be surrounded with
 * ${{ <expr> }} and will be replaced with their evaluation result in the returned string.
 *
 * @param input String containing zero or more expression
 * @param contextProvider Context provider for evaluation
 */
export declare function replaceExpressions(input: string, contextProvider: ContextProvider): string;
