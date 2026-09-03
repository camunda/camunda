import { CompletionOption, Context, Hover } from "../../types";
import { WorkflowDocument } from "../parser/parser";
import { NodeDesc } from "../parser/schema";
export declare function _getSchema(context: Context): NodeDesc;
export declare function parse(context: Context, filename: string, input: string): Promise<WorkflowDocument>;
export declare function complete(context: Context, filename: string, input: string, pos: number): Promise<CompletionOption[]>;
export declare function hover(context: Context, filename: string, input: string, pos: number): Promise<Hover | undefined>;
