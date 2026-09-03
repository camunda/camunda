import { CompletionOption } from "../../types";
import { NodeDesc } from "./schema";
import { ContextProvider } from "../expressions/types";
import { PropertyPath } from "../utils/path";
import { Workflow } from "../workflow";
export interface ContextProviderFactory {
    get(workflow: Workflow | undefined, path: PropertyPath): Promise<ContextProvider>;
}
export declare function complete(filename: string, input: string, pos: number, schema: NodeDesc, contextProviderFactory: ContextProviderFactory): Promise<CompletionOption[]>;
