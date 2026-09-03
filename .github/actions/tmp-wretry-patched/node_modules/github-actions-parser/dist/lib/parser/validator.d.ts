import { YAMLNode } from "yaml-ast-parser";
import { Diagnostic } from "../../types";
import { Workflow } from "../workflow";
import { ContextProviderFactory } from "./complete";
import { NodeDesc } from "./schema";
export interface ValidationResult {
    errors: Diagnostic[];
    nodeToDesc: Map<YAMLNode, NodeDesc>;
}
export declare function validate(root: YAMLNode, schema: NodeDesc, workflow: Workflow | undefined, contextProviderFactory: ContextProviderFactory): Promise<ValidationResult>;
