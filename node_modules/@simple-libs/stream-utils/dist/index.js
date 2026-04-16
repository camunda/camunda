import { Readable } from 'stream';
/**
 * Get all items from an async iterable and return them as an array.
 * @param iterable
 * @returns A promise that resolves to an array of items.
 */
export async function toArray(iterable) {
    const result = [];
    for await (const item of iterable) {
        result.push(item);
    }
    return result;
}
/**
 * Concatenate all buffers from an async iterable into a single Buffer.
 * @param iterable
 * @returns A promise that resolves to a single Buffer containing all concatenated buffers.
 */
export async function concatBufferStream(iterable) {
    return Buffer.concat(await toArray(iterable));
}
/**
 * Concatenate all strings from an async iterable into a single string.
 * @param iterable
 * @returns A promise that resolves to a single string containing all concatenated strings.
 */
export async function concatStringStream(iterable) {
    return (await toArray(iterable)).join('');
}
/**
 * Get the first item from an async iterable.
 * @param stream
 * @returns A promise that resolves to the first item, or null if the iterable is empty.
 */
export async function firstFromStream(stream) {
    for await (const tag of stream) {
        return tag;
    }
    return null;
}
/**
 * Merges multiple Readable streams into a single Readable stream.
 * Each chunk will be an object containing the source stream name and the chunk data.
 * @param streams - An object where keys are stream names and values are Readable streams.
 * @returns A merged Readable stream.
 */
export function mergeReadables(streams) {
    const mergedStream = new Readable({
        objectMode: true,
        read() { }
    });
    let ended = 0;
    Object.entries(streams).forEach(([name, stream], _i, entries) => {
        stream
            .on('data', (chunk) => mergedStream.push({
            source: name,
            chunk
        }))
            .on('end', () => {
            ended += 1;
            if (ended === entries.length) {
                mergedStream.push(null);
            }
        })
            .on('error', err => mergedStream.destroy(err));
    });
    return mergedStream;
}
/**
 * Split stream by separator.
 * @param stream
 * @param separator
 * @yields String chunks.
 */
export async function* splitStream(stream, separator) {
    let chunk;
    let payload;
    let buffer = '';
    for await (chunk of stream) {
        buffer += chunk.toString();
        if (buffer.includes(separator)) {
            payload = buffer.split(separator);
            buffer = payload.pop() || '';
            yield* payload;
        }
    }
    if (buffer) {
        yield buffer;
    }
}
/**
 * Parse JSON objects from a stream, separated by a delimiter (default is newline).
 * @param stream
 * @param delimiter
 * @yields Parsed JSON objects of type T.
 */
export async function* parseJsonStream(stream, delimiter = /\r?\n/) {
    let chunk;
    let payload;
    let buffer = '';
    let json;
    for await (chunk of stream) {
        buffer += chunk.toString();
        if (delimiter.test(buffer)) {
            payload = buffer.split(delimiter);
            buffer = payload.pop() || '';
            for (json of payload) {
                yield JSON.parse(json);
            }
        }
    }
    if (buffer) {
        yield JSON.parse(buffer);
    }
}
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiaW5kZXguanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi9zcmMvaW5kZXgudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUEsT0FBTyxFQUFFLFFBQVEsRUFBRSxNQUFNLFFBQVEsQ0FBQTtBQUVqQzs7OztHQUlHO0FBQ0gsTUFBTSxDQUFDLEtBQUssVUFBVSxPQUFPLENBQUksUUFBMEI7SUFDekQsTUFBTSxNQUFNLEdBQVEsRUFBRSxDQUFBO0lBRXRCLElBQUksS0FBSyxFQUFFLE1BQU0sSUFBSSxJQUFJLFFBQVEsRUFBRSxDQUFDO1FBQ2xDLE1BQU0sQ0FBQyxJQUFJLENBQUMsSUFBSSxDQUFDLENBQUE7SUFDbkIsQ0FBQztJQUVELE9BQU8sTUFBTSxDQUFBO0FBQ2YsQ0FBQztBQUVEOzs7O0dBSUc7QUFDSCxNQUFNLENBQUMsS0FBSyxVQUFVLGtCQUFrQixDQUFDLFFBQStCO0lBQ3RFLE9BQU8sTUFBTSxDQUFDLE1BQU0sQ0FBQyxNQUFNLE9BQU8sQ0FBQyxRQUFRLENBQUMsQ0FBQyxDQUFBO0FBQy9DLENBQUM7QUFFRDs7OztHQUlHO0FBQ0gsTUFBTSxDQUFDLEtBQUssVUFBVSxrQkFBa0IsQ0FBQyxRQUErQjtJQUN0RSxPQUFPLENBQUMsTUFBTSxPQUFPLENBQUMsUUFBUSxDQUFDLENBQUMsQ0FBQyxJQUFJLENBQUMsRUFBRSxDQUFDLENBQUE7QUFDM0MsQ0FBQztBQUVEOzs7O0dBSUc7QUFDSCxNQUFNLENBQUMsS0FBSyxVQUFVLGVBQWUsQ0FBSSxNQUF3QjtJQUMvRCxJQUFJLEtBQUssRUFBRSxNQUFNLEdBQUcsSUFBSSxNQUFNLEVBQUUsQ0FBQztRQUMvQixPQUFPLEdBQUcsQ0FBQTtJQUNaLENBQUM7SUFFRCxPQUFPLElBQUksQ0FBQTtBQUNiLENBQUM7QUFPRDs7Ozs7R0FLRztBQUNILE1BQU0sVUFBVSxjQUFjLENBSTVCLE9BQTRCO0lBRTVCLE1BQU0sWUFBWSxHQUFHLElBQUksUUFBUSxDQUFDO1FBQ2hDLFVBQVUsRUFBRSxJ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