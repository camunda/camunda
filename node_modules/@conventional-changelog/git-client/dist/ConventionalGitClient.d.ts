import type { ParserStreamOptions, Commit } from 'conventional-commits-parser';
import type { GetCommitsParams, GetSemverTagsParams } from './types.js';
import { GitClient } from './GitClient.js';
/**
 * Helper to get package tag prefix.
 * @param packageName
 * @returns Tag prefix.
 */
export declare function packagePrefix(packageName?: string): string | RegExp;
/**
 * Wrapper around Git CLI with conventional commits support.
 */
export declare class ConventionalGitClient extends GitClient {
    private deps;
    private loadDeps;
    /**
     * Get parsed commits stream.
     * @param params
     * @param params.path - Read commits from specific path.
     * @param params.from - Start commits range.
     * @param params.to - End commits range.
     * @param params.format - Commits format.
     * @param parserOptions - Commit parser options.
     * @yields Raw commits data.
     */
    getCommits(params?: GetCommitsParams, parserOptions?: ParserStreamOptions): AsyncIterable<Commit>;
    /**
     * Get semver tags stream.
     * @param params
     * @param params.prefix - Get semver tags with specific prefix.
     * @param params.skipUnstable - Skip semver tags with unstable versions.
     * @param params.clean - Clean version from prefix and trash.
     * @yields Semver tags.
     */
    getSemverTags(params?: GetSemverTagsParams): AsyncGenerator<string, void, unknown>;
    /**
     * Get last semver tag.
     * @param params - getSemverTags params.
     * @returns Last semver tag, `null` if not found.
     */
    getLastSemverTag(params?: GetSemverTagsParams): Promise<string | null>;
    /**
     * Get current sematic version from git tags.
     * @param params - Additional git params.
     * @returns Current sematic version, `null` if not found.
     */
    getVersionFromTags(params?: GetSemverTagsParams): Promise<string | null>;
}
//# sourceMappingURL=ConventionalGitClient.d.ts.map