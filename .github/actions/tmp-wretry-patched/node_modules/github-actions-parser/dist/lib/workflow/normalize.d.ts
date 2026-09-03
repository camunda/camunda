import { Expression, MatrixInvocations, Workflow } from "../workflow";
export declare function normalizeWorkflow(filename: string, workflow: Workflow): void;
export declare function normalizeMatrix(matrix: {
    include?: Object[];
    exclude?: Object[];
    [key: string]: (string | number | boolean)[];
} | Expression): MatrixInvocations | Expression;
export declare function crossProduct(inputs: {
    [inputKey: string]: (string | number | boolean)[];
}): {
    [key: string]: string | number | boolean;
}[];
