export declare const expressionMarker: RegExp;
export declare function containsExpression(input: string): boolean;
export declare function removeExpressionMarker(input: string): string;
export declare function iterateExpressions(input: string, f: (expression: string, pos: number, length: number) => void): void;
