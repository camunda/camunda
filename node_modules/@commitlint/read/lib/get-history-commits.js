import { getRawCommits } from "git-raw-commits";
// Get commit messages from history
export async function getHistoryCommits(options, opts = {}) {
    // Note: git-raw-commits v5 drops support for arbitrary git log arguments.
    // We extract and handle 'skip' manually here to preserve backward compatibility.
    // Other arbitrary arguments passed via gitLogArgs may be silently ignored by v5.
    const { skip: skipRaw, ...gitOptions } = options;
    let skipNum = 0;
    if (skipRaw !== undefined) {
        skipNum = Number(skipRaw);
        if (!Number.isInteger(skipNum) || skipNum < 0) {
            throw new TypeError(`Invalid skip value: ${skipRaw}`);
        }
    }
    const data = [];
    for await (const commit of getRawCommits({ ...gitOptions, cwd: opts.cwd })) {
        if (skipNum > 0) {
            skipNum--;
            continue;
        }
        data.push(commit);
    }
    return data;
}
//# sourceMappingURL=get-history-commits.js.map