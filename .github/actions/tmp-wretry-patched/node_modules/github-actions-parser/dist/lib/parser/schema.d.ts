import { PropertyPath } from "../utils/path";
import { Workflow } from "../workflow";
export declare type NodeDescMap = {
    [key: string]: NodeDesc;
};
declare type Desc = {
    /** Description for this node, can contain markdown */
    description?: string;
    /**
     * Custom value provider, used for auto-complete and validation
     *
     * @param desc Description for node to provide value for
     * @param workflow Workflow if it could be parsed
     * @param path Path in the workflow
     */
    customValueProvider?: CustomValueProvider;
};
declare type OneOfNodeDesc = {
    type: "oneOf";
    oneOf: NodeDesc[];
} & Desc;
export declare type MapNodeDesc = {
    type: "map";
    /**
     * Map of key to Node or Node array.
     */
    keys?: NodeDescMap;
    /**
     * Specify a template to validate unknown keys against
     */
    itemDesc?: NodeDesc;
    /**
     * Should unknown keys be allowed
     */
    allowUnknownKeys?: boolean;
    /**
     * Required keys for the map
     */
    required?: string[];
} & Desc;
declare type SequenceNodeDesc = {
    type: "sequence";
    itemDesc?: NodeDesc;
} & Desc;
export declare type ValueDesc = {
    value: string;
    description?: string;
} & Desc;
export declare type ValueNodeDesc = {
    type: "value";
    /** Allowed values */
    allowedValues?: ValueDesc[];
    /** If the node allows omitting ${{ }} to enter an expression */
    isExpression?: boolean;
    /**
     * If a value can be set using an expression, this will disable checking
     * against `allowedValues` when set
     */
    supportsExpression?: boolean;
} & Desc;
export declare type NodeDesc = ValueNodeDesc | SequenceNodeDesc | MapNodeDesc | OneOfNodeDesc;
export declare enum CustomValueValidation {
    /** Default, no special handling */
    None = 0,
    /**
     * Value/key must be provided
     *
     * This is mostly used for requiring certain keys to be provided
     **/
    Required = 1
}
export interface CustomValue {
    value: string;
    description?: string;
    validation?: CustomValueValidation;
}
export declare type CustomValueProvider = (desc: NodeDesc, workflow: Workflow | undefined, path: PropertyPath) => Promise<CustomValue[] | undefined>;
export {};
