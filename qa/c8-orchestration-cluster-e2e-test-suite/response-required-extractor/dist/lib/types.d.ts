export interface FieldSpec {
    name: string;
    type: string;
    children?: OutputSchema;
    enumValues?: string[];
    underlyingPrimitive?: string;
    rawRefName?: string;
    wrapper?: boolean;
}
export interface OutputSchema {
    required: FieldSpec[];
    optional: FieldSpec[];
}
export interface OutputMapEntry {
    path: string;
    method: string;
    status: string;
    schema: OutputSchema;
}
export interface OutputFile {
    metadata: {
        sourceRepo: string;
        commit: string;
        generatedAt: string;
        specPath: string;
        specSha256: string;
    };
    responses: OutputMapEntry[];
}
export interface FlattenResult {
    required: FieldSpec[];
    optional: FieldSpec[];
}
