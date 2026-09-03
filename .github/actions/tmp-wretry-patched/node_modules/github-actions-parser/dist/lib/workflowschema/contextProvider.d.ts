import { ContextProvider, DynamicContext } from "../expressions/types";
import { PropertyPath } from "../utils/path";
import { Workflow } from "../workflow";
export declare class EditContextProvider implements ContextProvider {
    private workflow;
    private path;
    private secrets;
    constructor(workflow: Workflow, path: PropertyPath, secrets: string[] | typeof DynamicContext);
    get(context: "github" | "env" | "job" | "steps" | "runner" | "secrets" | "strategy" | "matrix" | "needs"): Object;
}
