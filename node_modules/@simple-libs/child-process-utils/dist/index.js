import { concatBufferStream } from '@simple-libs/stream-utils';
/**
 * Wait for a child process to exit and return its exit code.
 * @param process
 * @returns A promise that resolves to the exit code of the process.
 */
export async function exitCode(process) {
    if (process.exitCode !== null) {
        return process.exitCode;
    }
    return new Promise(resolve => process.once('close', resolve));
}
/**
 * Catch error from a child process.
 * Also captures stderr output.
 * @param process
 * @returns A promise that resolves to an Error if the process exited with a non-zero code, or null if it exited successfully.
 */
export async function catchProcessError(process) {
    let error = new Error('Process exited with non-zero code');
    let stderr = '';
    process.on('error', (err) => {
        error = err;
    });
    if (process.stderr) {
        let chunk;
        for await (chunk of process.stderr) {
            stderr += chunk.toString();
        }
    }
    const code = await exitCode(process);
    if (stderr) {
        error = new Error(stderr);
    }
    return code ? error : null;
}
/**
 * Throws an error if the child process exits with a non-zero code.
 * @param process
 */
export async function throwProcessError(process) {
    const error = await catchProcessError(process);
    if (error) {
        throw error;
    }
}
/**
 * Yields the stdout of a child process.
 * It will throw an error if the process exits with a non-zero code.
 * @param process
 * @yields The stdout of the process.
 */
export async function* outputStream(process) {
    const { stdout } = process;
    const errorPromise = catchProcessError(process);
    if (stdout) {
        stdout.on('error', (err) => {
            // Iteration was interrupted, e.g. by `break` or `return` in a for-await loop.
            if (err.name === 'AbortError' && process.exitCode === null) {
                process.kill('SIGKILL');
            }
        });
        yield* stdout;
    }
    // Handle error only if iteration was not interrupted.
    const error = await errorPromise;
    if (error) {
        throw error;
    }
}
/**
 * Collects the stdout of a child process into a single Buffer.
 * It will throw an error if the process exits with a non-zero code.
 * @param process
 * @returns A promise that resolves to a Buffer containing the stdout of the process.
 */
export function output(process) {
    return concatBufferStream(outputStream(process));
}
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiaW5kZXguanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi9zcmMvaW5kZXgudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQ0EsT0FBTyxFQUFFLGtCQUFrQixFQUFFLE1BQU0sMkJBQTJCLENBQUE7QUFFOUQ7Ozs7R0FJRztBQUNILE1BQU0sQ0FBQyxLQUFLLFVBQVUsUUFBUSxDQUFDLE9BQXFCO0lBQ2xELElBQUksT0FBTyxDQUFDLFFBQVEsS0FBSyxJQUFJLEVBQUUsQ0FBQztRQUM5QixPQUFPLE9BQU8sQ0FBQyxRQUFRLENBQUE7SUFDekIsQ0FBQztJQUVELE9BQU8sSUFBSSxPQUFPLENBQVMsT0FBTyxDQUFDLEVBQUUsQ0FBQyxPQUFPLENBQUMsSUFBSSxDQUFDLE9BQU8sRUFBRSxPQUFPLENBQUMsQ0FBQyxDQUFBO0FBQ3ZFLENBQUM7QUFFRDs7Ozs7R0FLRztBQUNILE1BQU0sQ0FBQyxLQUFLLFVBQVUsaUJBQWlCLENBQUMsT0FBcUI7SUFDM0QsSUFBSSxLQUFLLEdBQUcsSUFBSSxLQUFLLENBQUMsbUNBQW1DLENBQUMsQ0FBQTtJQUMxRCxJQUFJLE1BQU0sR0FBRyxFQUFFLENBQUE7SUFFZixPQUFPLENBQUMsRUFBRSxDQUFDLE9BQU8sRUFBRSxDQUFDLEdBQVUsRUFBRSxFQUFFO1FBQ2pDLEtBQUssR0FBRyxHQUFHLENBQUE7SUFDYixDQUFDLENBQUMsQ0FBQTtJQUVGLElBQUksT0FBTyxDQUFDLE1BQU0sRUFBRSxDQUFDO1FBQ25CLElBQUksS0FBYSxDQUFBO1FBRWpCLElBQUksS0FBSyxFQUFFLEtBQUssSUFBSSxPQUFPLENBQUMsTUFBTSxFQUFFLENBQUM7WUFDbkMsTUFBTSxJQUFJLEtBQUssQ0FBQyxRQUFRLEVBQUUsQ0FBQTtRQUM1QixDQUFDO0lBQ0gsQ0FBQztJQUVELE1BQU0sSUFBSSxHQUFHLE1BQU0sUUFBUSxDQUFDLE9BQU8sQ0FBQyxDQUFBO0lBRXBDLElBQUksTUFBTSxFQUFFLENBQUM7UUFDWCxLQUFLLEdBQUcsSUFBSSxLQUFLLENBQUMsTUFBTSxDQUFDLENBQUE7SUFDM0IsQ0FBQztJQUVELE9BQU8sSUFBSSxDQUFDLENBQUMsQ0FBQyxLQUFLLENBQUMsQ0FBQyxDQUFDLElBQUksQ0FBQTtBQUM1QixDQUFDO0FBRUQ7OztHQUdHO0FBQ0gsTUFBTSxDQUFDLEtBQUssVUFBVSxpQkFBaUIsQ0FBQyxPQUFxQjtJQUMzRCxNQUFNLEtBQUssR0FBRyxNQUFNLGlCQUFpQixDQUFDLE9BQU8sQ0FBQyxDQUFBO0lBRTlDLElBQUksS0FBSyxFQUFFLENBQUM7UUFDVixNQUFNLEtBQUssQ0FBQTtJQUNiLENBQUM7QUFDSCxDQUFDO0FBRUQ7Ozs7O0dBS0c7QUFDSCxNQUFNLENBQUMsS0FBSyxTQUFTLENBQUMsQ0FBQyxZQUFZLENBQUMsT0FBcUI7SUFDdkQsTUFBTSxFQUFFLE1BQU0sRUFBRSxHQUFHLE9BQU8sQ0FBQTtJQUMxQixNQUFNLFlBQVksR0FBRyxpQkFBaUIsQ0FBQyxPQUFPLENBQUMsQ0FBQTtJQUUvQyxJQUFJLE1BQU0sRUFBRSxDQUFDO1FBQ1gsTUFBTSxDQUFDLEVBQUUsQ0FBQyxPQUFPLEVBQUUsQ0FBQyxHQUFHLEVBQUUsRUFBRTtZQUN6Qiw4RUFBOEU7WUFDOUUsSUFBSSxHQUFHLENBQUMsSUFBSSxLQUFLLFlBQVksSUFBSSxPQUFPLENBQUMsUUFBUSxLQUFLLElBQUksRUFBRSxDQUFDO2dCQUMzRCxPQUFPLENBQUMsSUFBSSxDQUFDLFNBQVMsQ0FBQyxDQUFBO1lBQ3pCLENBQUM7UUFDSCxDQUFDLENBQUMsQ0FBQTtRQUVGLEtBQUssQ0FBQyxDQUFDLE1BQStCLENBQUE7SUFDeEMsQ0FBQztJQUVELHNEQUFzRDtJQUV0RCxNQUFNLEtBQUssR0FBRyxNQUFNLFlBQVksQ0FBQTtJQUVoQyxJQUFJLEtBQUssRUFBRSxDQUFDO1FBQ1YsTUFBTSxLQUFLLENBQUE7SUFDYixDQUFDO0FBQ0gsQ0FBQztBQUVEOzs7OztHQUtHO0FBQ0gsTUFBTSxVQUFVLE1BQU0sQ0FBQyxPQUFxQjtJQUMxQyxPQUFPLGtCQUFrQixDQUFDLFlBQVksQ0FBQyxPQUFPLENBQUMsQ0FBQyxDQUFBO0FBQ2xELENBQUMifQ==