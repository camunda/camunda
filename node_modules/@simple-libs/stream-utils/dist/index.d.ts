import { Readable } from 'stream';
/**
 * Get all items from an async iterable and return them as an array.
 * @param iterable
 * @returns A promise that resolves to an array of items.
 */
export declare function toArray<T>(iterable: AsyncIterable<T>): Promise<T[]>;
/**
 * Concatenate all buffers from an async iterable into a single Buffer.
 * @param iterable
 * @returns A promise that resolves to a single Buffer containing all concatenated buffers.
 */
export declare function concatBufferStream(iterable: AsyncIterable<Buffer>): Promise<Buffer<ArrayBuffer>>;
/**
 * Concatenate all strings from an async iterable into a single string.
 * @param iterable
 * @returns A promise that resolves to a single string containing all concatenated strings.
 */
export declare function concatStringStream(iterable: AsyncIterable<string>): Promise<string>;
/**
 * Get the first item from an async iterable.
 * @param stream
 * @returns A promise that resolves to the first item, or null if the iterable is empty.
 */
export declare function firstFromStream<T>(stream: AsyncIterable<T>): Promise<T | null>;
export interface MergedReadableChunk<K extends string, T = Buffer> {
    source: K;
    chunk: T;
}
/**
 * Merges multiple Readable streams into a single Readable stream.
 * Each chunk will be an object containing the source stream name and the chunk data.
 * @param streams - An object where keys are stream names and values are Readable streams.
 * @returns A merged Readable stream.
 */
export declare function mergeReadables<K extends string, T = Buffer>(streams: Record<K, Readable>): Readable & AsyncIterable<MergedReadableChunk<K, T>>;
/**
 * Split stream by separator.
 * @param stream
 * @param separator
 * @yields String chunks.
 */
export declare function splitStream(stream: AsyncIterable<string | Buffer>, separator: string): AsyncGenerator<string, void, unknown>;
/**
 * Parse JSON objects from a stream, separated by a delimiter (default is newline).
 * @param stream
 * @param delimiter
 * @yields Parsed JSON objects of type T.
 */
export declare function parseJsonStream<T>(stream: AsyncIterable<string | Buffer>, delimiter?: RegExp): AsyncGenerator<Awaited<T>, void, unknown>;
//# sourceMappingURL=index.d.ts.map