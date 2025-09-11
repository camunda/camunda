import { OutputSchema } from './types.js';
export declare function buildSchemaTree(schema: any, components: Record<string, any>, seen?: Set<string>): OutputSchema;
export declare function resolveFieldSchema(fieldName: string, parentSchema: any, components: Record<string, any>): {
    kind: string;
    target?: any;
} | null;
