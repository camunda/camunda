/* Load static resources from a data directory.
 *
 * The `open()` function is relative to the location of the script, unless the path it tries to open is absolute.
 *
 * By default, it tries to open a file in the `../data` directory (which is convenient when testing locally with `k6 run`).
 *
 * When run from within Kubernetes, the DATA_DIR environment variable is explicitly configured to where the data files are mounted in.
 *
 * To override the DATA_DIR environment variable:
 *
 * * Either set it before calling `k6`: `DATA_DIR=/path/to/data k6 run ...`
 * * Or use the `-e` option to set it for the test: `k6 run -e DATA_DIR=/path/to/data ...`
 *
 */
const DATA_DIR = __ENV.DATA_DIR || "../data";

/* Read the file `name` from the data directory and return its content as a string.
 *
 * k6 only allows reading files in the `init` context, once per-VU: call this at module level. */
export function loadData(name) {
  const path = `${DATA_DIR}/${name}`;
  console.log(`Loading data file ${path}...`);
  return open(path);
}
