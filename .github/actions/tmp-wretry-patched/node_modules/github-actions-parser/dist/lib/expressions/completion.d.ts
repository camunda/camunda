import { CompletionOption } from "../../types";
import { ContextProvider } from "./types";
export declare function inExpression(input: string, pos: number): boolean;
export declare function completeExpression(input: string, pos: number, contextProvider: ContextProvider): Promise<CompletionOption[]>;
