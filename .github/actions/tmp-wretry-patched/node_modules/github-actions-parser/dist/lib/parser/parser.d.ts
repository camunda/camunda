import { Diagnostic } from "../../types";
import { YAMLNode } from "yaml-ast-parser";
import { ContextProviderFactory } from "./complete";
import { NodeDesc } from "./schema";
import { Workflow } from "../workflow";
export interface WorkflowDocument {
    /** Normalized workflow */
    workflow?: Workflow;
    /** Errors and warnings found during parsing */
    diagnostics: Diagnostic[];
    /** Workflow AST */
    workflowST: YAMLNode;
    /** Mapping of AST nodes to mapped schema descriptions */
    nodeToDesc: Map<YAMLNode, NodeDesc>;
}
export declare function parse(filename: string, input: string, schema: NodeDesc, contextProviderFactory: ContextProviderFactory): Promise<WorkflowDocument>;
