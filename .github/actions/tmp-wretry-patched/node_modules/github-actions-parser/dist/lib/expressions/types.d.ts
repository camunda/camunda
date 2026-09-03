export interface ContextProvider {
    get(context: "github" | "env" | "job" | "steps" | "runner" | "secrets" | "strategy" | "matrix" | "needs"): Object;
}
export declare const DynamicContext: {};
