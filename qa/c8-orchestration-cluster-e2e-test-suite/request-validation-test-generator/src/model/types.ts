export interface OperationModel {
  operationId: string;
  method: string; // GET, POST ...
  path: string;
  tags: string[];
  requestBodySchema?: any; // dereferenced schema (JSON)
  requiredProps?: string[]; // top-level required fields (object bodies)
  parameters: ParameterModel[];
  rootOneOf?: any[]; // array of variant schemas if oneOf at root
  discriminator?: { propertyName: string; mapping?: Record<string,string> };
}

export interface ParameterModel {
  name: string;
  in: 'path' | 'query' | 'header' | 'cookie';
  required: boolean;
  schema?: any;
}

export interface SpecModel {
  operations: OperationModel[];
}

export type ScenarioKind =
  | 'missing-required'
  | 'missing-required-combo'
  | 'type-mismatch'
  | 'union'
  | 'constraint-violation'
  | 'enum-violation'
  | 'additional-prop'
  | 'oneof-ambiguous'
  | 'oneof-none-match'
  | 'discriminator-mismatch'
  | 'param-missing'
  | 'param-type-mismatch'
  | 'param-enum-violation'
  | 'missing-body'
  | 'body-top-type-mismatch'
  | 'nested-additional-prop'
  | 'unique-items-violation'
  | 'multiple-of-violation'
  | 'format-invalid'
  | 'additional-prop-general'
  | 'oneof-multi-ambiguous'
  | 'oneof-cross-bleed'
  | 'discriminator-structure-mismatch'
  | 'allof-missing-required'
  | 'allof-conflict';

export interface ValidationScenario {
  id: string;
  operationId: string;
  method: string;
  path: string;
  type: ScenarioKind;
  target?: string; // field or parameter
  requestBody?: any;
  params?: Record<string, string>;
  expectedStatus: number; // usually 400
  description: string;
  headersAuth: boolean; // whether to send auth headers
  source?: 'body' | 'query' | 'path' | 'header' | 'cookie';
}
