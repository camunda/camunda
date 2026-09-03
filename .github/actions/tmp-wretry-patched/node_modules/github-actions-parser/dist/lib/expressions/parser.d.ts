import * as chevrotain from "chevrotain";
export declare const RParens: chevrotain.TokenType;
export declare const RSquare: chevrotain.TokenType;
export declare const Comma: chevrotain.TokenType;
/**
 * Expressions cannot use arbitrary variables, everything needs to be access via a context,
 * so define all supported ones.
 * see https://help.github.com/en/actions/reference/context-and-expression-syntax-for-github-actions#contexts
 */
export declare const Dot: chevrotain.TokenType;
export declare const ContextMemberOrKeyword: chevrotain.TokenType;
export declare const ContextMember: chevrotain.TokenType;
export declare const Context: chevrotain.TokenType;
export declare const Contexts: chevrotain.TokenType[];
export declare const Operator: chevrotain.TokenType;
export declare const And: chevrotain.TokenType;
export declare const Or: chevrotain.TokenType;
export declare const Eq: chevrotain.TokenType;
export declare const NEq: chevrotain.TokenType;
export declare const LT: chevrotain.TokenType;
export declare const LTE: chevrotain.TokenType;
export declare const GT: chevrotain.TokenType;
export declare const GTE: chevrotain.TokenType;
export declare const Not: chevrotain.TokenType;
export declare const Function: chevrotain.TokenType;
export declare const contains: chevrotain.TokenType;
export declare const startsWith: chevrotain.TokenType;
export declare const endsWith: chevrotain.TokenType;
export declare const join: chevrotain.TokenType;
export declare const toJSON: chevrotain.TokenType;
export declare const fromJSON: chevrotain.TokenType;
export declare const hashFiles: chevrotain.TokenType;
export declare const success: chevrotain.TokenType;
export declare const always: chevrotain.TokenType;
export declare const failure: chevrotain.TokenType;
export declare const format: chevrotain.TokenType;
export declare const cancelled: chevrotain.TokenType;
export declare const StringLiteral: chevrotain.TokenType;
export declare const NumberLiteral: chevrotain.TokenType;
export declare const WhiteSpace: chevrotain.TokenType;
declare const ExpressionLexer: chevrotain.Lexer;
export declare class ExpressionParser extends chevrotain.CstParser {
    constructor();
    expression: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    subExpression: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    contextAccess: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    contextMember: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    contextDotMember: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    contextBoxMember: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    array: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    logicalGrouping: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    functionCall: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    functionParameters: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    value: chevrotain.ParserMethod<[], chevrotain.CstNode>;
    booleanValue: chevrotain.ParserMethod<[], chevrotain.CstNode>;
}
export declare const defaultRule = "expression";
export declare const parser: ExpressionParser;
export declare const BaseCstVisitor: new (...args: any[]) => chevrotain.ICstVisitor<any, any>;
export { ExpressionLexer };
