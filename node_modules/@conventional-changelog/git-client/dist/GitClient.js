import { spawn } from 'child_process';
import { firstFromStream, splitStream } from '@simple-libs/stream-utils';
import { output, outputStream } from '@simple-libs/child-process-utils';
import { formatArgs, toArray } from './utils.js';
const SCISSOR = '------------------------ >8 ------------------------';
/**
 * Wrapper around Git CLI.
 */
export class GitClient {
    cwd;
    debug;
    constructor(cwd, debug) {
        this.cwd = cwd;
        this.debug = debug;
    }
    formatArgs(...args) {
        const finalArgs = formatArgs(...args);
        if (this.debug) {
            this.debug(finalArgs);
        }
        return finalArgs;
    }
    /**
     * Raw exec method to run git commands.
     * @param args
     * @returns Stdout string output of the command.
     */
    async exec(...args) {
        return (await output(spawn('git', this.formatArgs(...args), {
            cwd: this.cwd
        }))).toString().trim();
    }
    /**
     * Raw exec method to run git commands with stream output.
     * @param args
     * @returns Stdout stream of the command.
     */
    execStream(...args) {
        return outputStream(spawn('git', this.formatArgs(...args), {
            cwd: this.cwd
        }));
    }
    /**
     * Initialize a new git repository.
     * @returns Boolean result.
     */
    async init() {
        try {
            await this.exec('init');
            return true;
        }
        catch {
            return false;
        }
    }
    /**
     * Get raw commits stream.
     * @param params
     * @param params.path - Read commits from specific path.
     * @param params.from - Start commits range.
     * @param params.to - End commits range.
     * @param params.format - Commits format.
     * @yields Raw commits data.
     */
    async *getRawCommits(params = {}) {
        const { path, from = '', to = 'HEAD', format = '%B', ignore, reverse, merges, since, firstParent } = params;
        const shouldNotIgnore = ignore
            ? (chunk) => !ignore.test(chunk)
            : () => true;
        const stdout = this.execStream('log', `--format=${format}%n${SCISSOR}`, since && `--since=${since instanceof Date ? since.toISOString() : since}`, reverse && '--reverse', merges && '--merges', merges === false && '--no-merges', firstParent && '--first-parent', [from, to].filter(Boolean).join('..'), ...path ? ['--', ...toArray(path)] : []);
        const commitsStream = splitStream(stdout, `${SCISSOR}\n`);
        let chunk;
        for await (chunk of commitsStream) {
            if (shouldNotIgnore(chunk)) {
                yield chunk;
            }
        }
    }
    /**
     * Get tags stream.
     * @param params
     * @yields Tags
     */
    async *getTags(params = {}) {
        const { path, from = '', to = 'HEAD', since } = params;
        const tagRegex = /tag:\s*(.+?)[,)]/gi;
        const stdout = this.execStream('log', '--decorate', '--no-color', '--date-order', since && `--since=${since instanceof Date ? since.toISOString() : since}`, [from, to].filter(Boolean).join('..'), ...path ? ['--', ...toArray(path)] : []);
        let chunk;
        let matches;
        let tag;
        for await (chunk of stdout) {
            matches = chunk.toString().trim().matchAll(tagRegex);
            for ([, tag] of matches) {
                yield tag;
            }
        }
    }
    /**
     * Get last tag.
     * @param params
     * @returns Last tag, `null` if not found.
     */
    async getLastTag(params) {
        return firstFromStream(this.getTags(params));
    }
    /**
     * Check file is ignored via .gitignore.
     * @param file - Path to target file.
     * @returns Boolean value.
     */
    async checkIgnore(file) {
        try {
            await this.exec('check-ignore', '--', file);
            return true;
        }
        catch {
            return false;
        }
    }
    /**
     * Add files to git index.
     * @param files - Files to stage.
     */
    async add(files) {
        await this.exec('add', '--', ...toArray(files));
    }
    /**
     * Commit changes.
     * @param params
     * @param params.verify
     * @param params.sign
     * @param params.files
     * @param params.allowEmpty
     * @param params.message
     */
    async commit(params) {
        const { verify = true, sign = false, files = [], allowEmpty = false, message } = params;
        await this.exec('commit', !verify && '--no-verify', sign && '-S', allowEmpty && '--allow-empty', '-m', message, '--', ...files);
    }
    /**
     * Create a tag for the current commit.
     * @param params
     * @param params.sign
     * @param params.name
     * @param params.message
     */
    async tag(params) {
        let { sign = false, name, message } = params;
        if (sign) {
            message = '';
        }
        await this.exec('tag', sign && '-s', message && '-a', ...message ? ['-m', message] : [], '--', name);
    }
    /**
     * Get current branch name.
     * @returns Current branch name.
     */
    async getCurrentBranch() {
        const branch = await this.exec('rev-parse', '--abbrev-ref', 'HEAD');
        return branch;
    }
    /**
     * Get default branch name.
     * @returns Default branch name.
     */
    async getDefaultBranch() {
        const branch = (await this.exec('rev-parse', '--abbrev-ref', 'origin/HEAD')).replace(/^origin\//, '');
        return branch;
    }
    /**
     * Push changes to remote.
     * @param branch
     * @param params
     * @param params.verify
     */
    async push(branch, params = {}) {
        const { verify = true, tags = false, followTags = false, force = false } = params;
        await this.exec('push', followTags && '--follow-tags', tags && '--tags', !verify && '--no-verify', force && '--force', 'origin', '--', branch);
    }
    /**
     * Verify rev exists.
     * @param rev
     * @param safe - If `true`, will not throw error if rev not found.
     * @returns Target hash.
     */
    async verify(rev, safe) {
        let git = this.exec('rev-parse', '--verify', rev);
        if (safe) {
            git = git.catch(() => '');
        }
        return await git;
    }
    /**
     * Get config value by key.
     * @param key - Config key.
     * @returns Config value.
     */
    async getConfig(key) {
        return await this.exec('config', '--get', '--', key);
    }
    /**
     * Set config value by key.
     * @param key - Config key.
     * @param value - Config value.
     */
    async setConfig(key, value) {
        await this.exec('config', '--', key, value);
    }
    /**
     * Fetch changes from remote.
     * @param params
     */
    async fetch(params = {}) {
        const { prune = false, unshallow = false, tags = false, all = false, remote, branch } = params;
        await this.exec('fetch', prune && '--prune', unshallow && '--unshallow', tags && '--tags', all && '--all', ...remote && branch
            ? [
                '--',
                remote,
                branch
            ]
            : []);
    }
    /**
     * Create a new branch.
     * @param branch - Branch name.
     */
    async createBranch(branch) {
        await this.exec('checkout', '-b', branch);
    }
    /**
     * Delete a branch.
     * @param branch - Branch name.
     */
    async deleteBranch(branch) {
        await this.exec('branch', '-D', '--', branch);
    }
    /**
     * Checkout a branch.
     * @param branch - Branch name.
     */
    async checkout(branch) {
        await this.exec('checkout', branch);
    }
}
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiR2l0Q2xpZW50LmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiLi4vc3JjL0dpdENsaWVudC50cyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiQUFBQSxPQUFPLEVBQUUsS0FBSyxFQUFFLE1BQU0sZUFBZSxDQUFBO0FBQ3JDLE9BQU8sRUFDTCxlQUFlLEVBQ2YsV0FBVyxFQUNaLE1BQU0sMkJBQTJCLENBQUE7QUFDbEMsT0FBTyxFQUNMLE1BQU0sRUFDTixZQUFZLEVBQ2IsTUFBTSxrQ0FBa0MsQ0FBQTtBQUN6QyxPQUFPLEVBQ0wsVUFBVSxFQUNWLE9BQU8sRUFDUixNQUFNLFlBQVksQ0FBQTtBQVduQixNQUFNLE9BQU8sR0FBRyxzREFBc0QsQ0FBQTtBQUV0RTs7R0FFRztBQUNILE1BQU0sT0FBTyxTQUFTO0lBRVQ7SUFDRjtJQUZULFlBQ1csR0FBVyxFQUNiLEtBQTZDO1FBRDNDLFFBQUcsR0FBSCxHQUFHLENBQVE7UUFDYixVQUFLLEdBQUwsS0FBSyxDQUF3QztJQUNuRCxDQUFDO0lBRUksVUFBVSxDQUFDLEdBQUcsSUFBVztRQUMvQixNQUFNLFNBQVMsR0FBRyxVQUFVLENBQUMsR0FBRyxJQUFJLENBQUMsQ0FBQTtRQUVyQyxJQUFJLElBQUksQ0FBQyxLQUFLLEVBQUUsQ0FBQztZQUNmLElBQUksQ0FBQyxLQUFLLENBQUMsU0FBUyxDQUFDLENBQUE7UUFDdkIsQ0FBQztRQUVELE9BQU8sU0FBUyxDQUFBO0lBQ2xCLENBQUM7SUFFRDs7OztPQUlHO0lBQ0gsS0FBSyxDQUFDLElBQUksQ0FBQyxHQUFHLElBQVc7UUFDdkIsT0FBTyxDQUNMLE1BQU0sTUFBTSxDQUFDLEtBQUssQ0FBQyxLQUFLLEVBQUUsSUFBSSxDQUFDLFVBQVUsQ0FBQyxHQUFHLElBQUksQ0FBQyxFQUFFO1lBQ2xELEdBQUcsRUFBRSxJQUFJLENBQUMsR0FBRztTQUNkLENBQUMsQ0FBQyxDQUNKLENBQUMsUUFBUSxFQUFFLENBQUMsSUFBSSxFQUFFLENBQUE7SUFDckIsQ0FBQztJ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