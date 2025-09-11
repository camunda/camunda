import { OutputMapEntry, OutputSchema } from './lib/types.js';
export declare function extractResponses(doc: any): OutputMapEntry[];
export declare function pruneSchema(schema: OutputSchema): void;
export declare function generate(): Promise<void>;
