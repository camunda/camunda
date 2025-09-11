import { FieldSpec, FlattenResult, OutputSchema } from './types.js';
export declare function flatten(schema: any, components: Record<string, any>, seen?: Set<string>): OutputSchema;
export declare function flattenInternal(schema: any, components: Record<string, any>, seen?: Set<string>): FlattenResult;
export declare function describeType(schema: any, components: Record<string, any>, stack?: string[]): string;
export declare function deriveFieldMetadata(schema: any, components: Record<string, any>): Partial<FieldSpec>;
export declare function isObjectLike(s: any): boolean;
export declare function primitiveFromSchema(schema: any, components: Record<string, any>, stack: string[]): string | null;
