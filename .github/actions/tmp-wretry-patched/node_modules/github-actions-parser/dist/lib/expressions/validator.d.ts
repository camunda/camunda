import { ContextProvider } from "./types";
import { Diagnostic } from "../../types";
export declare function validateExpression(input: string, posOffset: number, diagnostics: Diagnostic[], contextProvider: ContextProvider): any;
export declare function validateExpressions(input: string, posOffset: number, errors: Diagnostic[], contextProvider: ContextProvider): void;
