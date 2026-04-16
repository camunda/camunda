import type { GitLogParams, GitLogTagsParams, GitCommitParams, GitTagParams, GitPushParams, GitFetchParams, Arg } from './types.js';
/**
 * Wrapper around Git CLI.
 */
export declare class GitClient {
    readonly cwd: string;
    debug?: ((log: string[]) => void) | undefined;
    constructor(cwd: string, debug?: ((log: string[]) => void) | undefined);
    private formatArgs;
    /**
     * Raw exec method to run git commands.
     * @param args
     * @returns Stdout string output of the command.
     */
    exec(...args: Arg[]): Promise<string>;
    /**
     * Raw exec method to run git commands with stream output.
     * @param args
     * @returns Stdout stream of the command.
     */
    execStream(...args: Arg[]): AsyncGenerator<Buffer<ArrayBufferLike>, void, undefined>;
    /**
     * Initialize a new git repository.
     * @returns Boolean result.
     */
    init(): Promise<boolean>;
    /**
     * Get raw commits stream.
     * @param params
     * @param params.path - Read commits from specific path.
     * @param params.from - Start commits range.
     * @param params.to - End commits range.
     * @param params.format - Commits format.
     * @yields Raw commits data.
     */
    getRawCommits(params?: GitLogParams): AsyncGenerator<string, void, unknown>;
    /**
     * Get tags stream.
     * @param params
     * @yields Tags
     */
    getTags(params?: GitLogTagsParams): AsyncGenerator<string, void, unknown>;
    /**
     * Get last tag.
     * @param params
     * @returns Last tag, `null` if not found.
     */
    getLastTag(params?: GitLogTagsParams): Promise<string | null>;
    /**
     * Check file is ignored via .gitignore.
     * @param file - Path to target file.
     * @returns Boolean value.
     */
    checkIgnore(file: string): Promise<boolean>;
    /**
     * Add files to git index.
     * @param files - Files to stage.
     */
    add(files: string | string[]): Promise<void>;
    /**
     * Commit changes.
     * @param params
     * @param params.verify
     * @param params.sign
     * @param params.files
     * @param params.allowEmpty
     * @param params.message
     */
    commit(params: GitCommitParams): Promise<void>;
    /**
     * Create a tag for the current commit.
     * @param params
     * @param params.sign
     * @param params.name
     * @param params.message
     */
    tag(params: GitTagParams): Promise<void>;
    /**
     * Get current branch name.
     * @returns Current branch name.
     */
    getCurrentBranch(): Promise<string>;
    /**
     * Get default branch name.
     * @returns Default branch name.
     */
    getDefaultBranch(): Promise<string>;
    /**
     * Push changes to remote.
     * @param branch
     * @param params
     * @param params.verify
     */
    push(branch: string, params?: GitPushParams): Promise<void>;
    /**
     * Verify rev exists.
     * @param rev
     * @param safe - If `true`, will not throw error if rev not found.
     * @returns Target hash.
     */
    verify(rev: string, safe?: boolean): Promise<string>;
    /**
     * Get config value by key.
     * @param key - Config key.
     * @returns Config value.
     */
    getConfig(key: string): Promise<string>;
    /**
     * Set config value by key.
     * @param key - Config key.
     * @param value - Config value.
     */
    setConfig(key: string, value: string): Promise<void>;
    /**
     * Fetch changes from remote.
     * @param params
     */
    fetch(params?: GitFetchParams): Promise<void>;
    /**
     * Create a new branch.
     * @param branch - Branch name.
     */
    createBranch(branch: string): Promise<void>;
    /**
     * Delete a branch.
     * @param branch - Branch name.
     */
    deleteBranch(branch: string): Promise<void>;
    /**
     * Checkout a branch.
     * @param branch - Branch name.
     */
    checkout(branch: string): Promise<void>;
}
//# sourceMappingURL=GitClient.d.ts.map