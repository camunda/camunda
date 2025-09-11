export interface FieldSpec {
  name: string;
  type: string; // normalized display type (primitive | array<...> | object | ref name)
  children?: OutputSchema; // present for object or array<object>
  enumValues?: string[];
  underlyingPrimitive?: string;
  rawRefName?: string; // original $ref name
  wrapper?: boolean; // true if a named wrapper schema over a primitive
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

export interface FlattenResult { required: FieldSpec[]; optional: FieldSpec[] }
