import type { ChildProcess } from 'child_process';
/**
 * Wait for a child process to exit and return its exit code.
 * @param process
 * @returns A promise that resolves to the exit code of the process.
 */
export declare function exitCode(process: ChildProcess): Promise<number>;
/**
 * Catch error from a child process.
 * Also captures stderr output.
 * @param process
 * @returns A promise that resolves to an Error if the process exited with a non-zero code, or null if it exited successfully.
 */
export declare function catchProcessError(process: ChildProcess): Promise<Error | null>;
/**
 * Throws an error if the child process exits with a non-zero code.
 * @param process
 */
export declare function throwProcessError(process: ChildProcess): Promise<void>;
/**
 * Yields the stdout of a child process.
 * It will throw an error if the process exits with a non-zero code.
 * @param process
 * @yields The stdout of the process.
 */
export declare function outputStream(process: ChildProcess): AsyncGenerator<Buffer<ArrayBufferLike>, void, any>;
/**
 * Collects the stdout of a child process into a single Buffer.
 * It will throw an error if the process exits with a non-zero code.
 * @param process
 * @returns A promise that resolves to a Buffer containing the stdout of the process.
 */
export declare function output(process: ChildProcess): Promise<Buffer<ArrayBuffer>>;
//# sourceMappingURL=index.d.ts.map