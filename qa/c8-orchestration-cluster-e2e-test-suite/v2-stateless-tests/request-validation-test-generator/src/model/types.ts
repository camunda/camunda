// NOTE: Many schema shapes are dynamic OpenAPI fragments; using `any` with selective lint disable for pragmatism.
export interface OperationModel {
  operationId: string;
  method: string; // GET, POST ...
  path: string;
  tags: string[];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  requestBodySchema?: any; // dereferenced schema (JSON)
  /** True if the OpenAPI operation-level requestBody object is marked required: true */
  bodyRequired?: boolean;
  requiredProps?: string[]; // top-level required fields (object bodies)
  parameters: ParameterModel[];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rootOneOf?: any[]; // array of variant schemas if oneOf at root
  discriminator?: {propertyName: string; mapping?: Record<string, string>};
  /** multipart/form-data schema if present (raw dereferenced) */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  multipartSchema?: any;
  /** Required properties for multipart schema (if any) */
  multipartRequiredProps?: string[];
  /** All request body media types advertised by the operation */
  mediaTypes?: string[];
}

export interface ParameterModel {
  name: string;
  in: 'path' | 'query' | 'header' | 'cookie';
  required: boolean;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
  | 'param-constraint-violation'
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
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  requestBody?: any;
  params?: Record<string, string>;
  expectedStatus: number; // usually 400
  description: string;
  headersAuth: boolean; // whether to send auth headers
  source?: 'body' | 'query' | 'path' | 'header' | 'cookie';
  /** How to encode the body; defaults to json if omitted */
  bodyEncoding?: 'json' | 'multipart';
  /** Multipart form fields (used when bodyEncoding === 'multipart') */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  multipartForm?: Record<string, any>;
  /** Additional metadata for constraint-based scenarios */
  constraintKind?: string; // e.g. pattern | length-min | length-max | enum
  constraintOrigin?: 'body' | 'param';
}
