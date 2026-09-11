/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ 233:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.hasEligibleRefs = hasEligibleRefs;
exports.decideAttribution = decideAttribution;
exports.evaluatePostGateAnomaly = evaluatePostGateAnomaly;
/** Sources only reachable when the section contract wasn't observed (gate bypass, outage, post-merge body edit). */
const FALLBACK_SOURCES = new Set(['closingIssuesReferences', 'legacyBodyScan']);
/** A `Backport of #N` marker is a delivery-hop signal, not an attribution ref; cross-repo refs never attribute. */
function eligible(refs) {
    return refs.filter((ref) => !ref.crossRepo && ref.kind !== 'backport');
}
/** Whether the section carries anything the chain can terminate on, so a
 *  caller can tell in advance that the later steps will not be consulted. */
function hasEligibleRefs(refs) {
    return eligible(refs).length > 0;
}
function uniqueNumbers(refs) {
    return [...new Set(refs.map((ref) => ref.number))];
}
/**
 * The unconditional attribution chain (D20): section refs, then GitHub's native
 * field, then a legacy body-wide scan. Pure — decides from one PR's own
 * already-resolved facts; the backport hop is the caller's composition.
 */
function decideAttribution(input) {
    if (input.optOut) {
        return { source: 'optOut', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
    }
    const sectionEligible = eligible(input.sectionRefs);
    if (sectionEligible.length > 0) {
        const live = sectionEligible.filter((ref) => ref.target === 'issue');
        const notLive = sectionEligible.filter((ref) => ref.target !== 'issue');
        const reasons = notLive.length
            ? [`These section refs do not resolve to a live issue in this repo: ${uniqueNumbers(notLive).map((n) => `#${n}`).join(', ')}.`]
            : [];
        if (live.length > 0) {
            return { source: 'section', issueNumbers: uniqueNumbers(live), deliveryPath: 'direct', reasons };
        }
        return { source: 'resolutionFailed', issueNumbers: [], deliveryPath: 'direct', reasons };
    }
    if (input.closingIssuesReferences.length > 0) {
        return {
            source: 'closingIssuesReferences',
            issueNumbers: [...new Set(input.closingIssuesReferences)],
            deliveryPath: 'direct',
            reasons: [],
        };
    }
    const legacyLive = eligible(input.legacyRefs).filter((ref) => ref.target === 'issue');
    if (legacyLive.length > 0) {
        return { source: 'legacyBodyScan', issueNumbers: uniqueNumbers(legacyLive), deliveryPath: 'direct', reasons: [] };
    }
    return { source: 'unattributed', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
}
/**
 * D20: a PR merged after its branch's gate watermark terminates at the section
 * step by construction, so any fallback source past that point means the
 * section contract wasn't observed. `mergedAt` must be the PR the decision came
 * FROM — for a backport hop, the original's.
 */
function evaluatePostGateAnomaly(input) {
    if (input.gateRequiredAt === null)
        return undefined;
    if (!FALLBACK_SOURCES.has(input.source))
        return undefined;
    if (Date.parse(input.mergedAt) < Date.parse(input.gateRequiredAt))
        return undefined;
    return 'post_gate_fallback_attribution';
}


/***/ }),

/***/ 493:
/***/ ((__unused_webpack_module, exports) => {


/**
 * Pure title-type -> release-notes-section categorization (D16-D19, table from
 * the signed design 53605-issue-proposals.html). No IO: the caller supplies the
 * already-resolved title and the labels already fetched from the API.
 */
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.BOT_CATEGORY_OVERRIDES = void 0;
exports.stripBackportPrefix = stripBackportPrefix;
exports.parseDependencyUpdate = parseDependencyUpdate;
exports.categorize = categorize;
/** D16: bots whose own title can't be trusted as the category source. */
exports.BOT_CATEGORY_OVERRIDES = {
    'backport-action': 'inherit-original',
    'monorepo-devops-automation[bot]': 'inherit-original',
    'renovate[bot]': 'deps',
    'dependabot[bot]': 'deps',
};
/** null = excluded from both outputs (release-merge PRs, D25). An unknown or
 *  unparseable type falls back to Uncategorized — never dropped (C10). */
const SECTION_BY_TYPE = {
    feat: 'Features',
    fix: 'Bug Fixes',
    perf: 'Performance',
    docs: 'Documentation',
    deps: 'Dependency updates',
    revert: 'Reverts',
    refactor: 'Maintenance',
    build: 'Maintenance',
    ci: 'Maintenance',
    test: 'Maintenance',
    style: 'Maintenance',
    merge: null,
};
/** The one section hidden from the customer-facing body — still in the full asset. */
const INTERNAL_SECTION = 'Maintenance';
// `type` + optional `(scope)` + optional `!` + `: ` + subject. The caller
// (pipeline/index.ts) already runs stripBackportPrefix on the title before
// this ever sees it, so no bracket tolerance is needed here — a leading
// bracket this regex still had to tolerate would only ever be a title that
// never should have passed the PR-gate's stricter lint in the first place.
const HEADER = /^(?<type>[^\s():!]+)(?:\([^)]*\))?!?:\s*(?<subject>.+)$/;
function parseType(title) {
    return HEADER.exec(title)?.groups?.type?.toLowerCase() ?? null;
}
const BACKPORT_TITLE_PREFIX = /^\[backport\b[^\]]*\]\s*/i;
/** Strips a leading `[Backport ...]` marker for display. Scoped to that one
 *  word so an unrelated bracketed prefix ("[CPT] ...") is left alone. */
function stripBackportPrefix(title) {
    return title.replace(BACKPORT_TITLE_PREFIX, '');
}
// dependabot's default title states both sides directly: "Bump X from A to B".
const DEPENDABOT_BUMP = /Bump (\S+) from (\S+) to (\S+)/i;
// A renovate body table row: "| [package](url) ... | `old` → `new` | ...".
// Anchored on the leading `[name]` and the backtick-quoted arrow pair only —
// the column count varies between renovate's table shapes.
const RENOVATE_TABLE_ROW = /^\|\s*\[([^\]]+)\].*?`([^`]+)`\s*→\s*`([^`]+)`.*\|\s*$/gm;
/**
 * For a `deps:` PR, the dependency name and its old/new version — the customer
 * wants "name: old → new", not the bot's verbose prose. Renovate only puts the
 * new version in its title, so its body table is read instead. null when
 * neither shape matches; the caller then keeps the plain title.
 */
function parseDependencyUpdate(input) {
    const bump = DEPENDABOT_BUMP.exec(input.title);
    if (bump) {
        const [, name, from, to] = bump;
        return `${name}: ${from} → ${to}`;
    }
    const rows = [...input.body.matchAll(RENOVATE_TABLE_ROW)].map((match) => `${match[1]}: ${match[2]} → ${match[3]}`);
    return rows.length > 0 ? rows.join('; ') : null;
}
function categorize(input) {
    const reasons = [];
    const override = input.authorLogin ? exports.BOT_CATEGORY_OVERRIDES[input.authorLogin] : undefined;
    const type = override === 'deps' ? 'deps' : parseType(input.title);
    if (type === null) {
        const author = input.authorLogin ? ` (author ${input.authorLogin})` : '';
        reasons.push(`Title does not parse as a conventional commit${author}: "${input.title}".`);
    }
    const mapped = type === null ? undefined : SECTION_BY_TYPE[type];
    const section = mapped === undefined ? 'Uncategorized' : mapped;
    const visibility = section === INTERNAL_SECTION ? 'internal' : 'customer';
    let component;
    if (input.componentLabels.length === 0) {
        component = null;
    }
    else if (input.componentLabels.length === 1) {
        component = input.componentLabels[0];
    }
    else {
        component = 'Multiple components';
        reasons.push(`Multiple components: ${input.componentLabels.join(', ')}.`);
    }
    return { section, visibility, breaking: input.breakingChangeLabel, component, reasons };
}


/***/ }),

/***/ 516:
/***/ (function(__unused_webpack_module, exports, __nccwpck_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
const node_fs_1 = __nccwpck_require__(24);
const core = __importStar(__nccwpck_require__(93));
const pipeline_1 = __nccwpck_require__(782);
const range_1 = __nccwpck_require__(53);
const walk_1 = __nccwpck_require__(600);
const render_1 = __nccwpck_require__(624);
const parser_1 = __nccwpck_require__(883);
const resolve_1 = __nccwpck_require__(940);
const warm_1 = __nccwpck_require__(579);
const resolver_1 = __nccwpck_require__(306);
/** `owner/repo` from the runner's own environment. Empty halves would reach
 *  GraphQL and come back as an opaque schema error, so they fail here instead. */
function readRepository() {
    const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '').split('/');
    if (!owner || !repo) {
        throw new Error(`GITHUB_REPOSITORY must be set to "owner/repo", got "${process.env.GITHUB_REPOSITORY ?? ''}".`);
    }
    return { owner, repo };
}
function readInputs() {
    const { owner, repo } = readRepository();
    const gateRequiredAt = core.getInput('gate-required-at').trim();
    if (gateRequiredAt.length > 0 && Number.isNaN(Date.parse(gateRequiredAt))) {
        throw new Error(`gate-required-at must be a parseable date, got "${gateRequiredAt}".`);
    }
    return {
        token: core.getInput('token', { required: true }),
        owner,
        repo,
        targetVersion: core.getInput('target-version', { required: true }),
        releaseBranch: core.getInput('release-branch', { required: true }),
        gateRequiredAt: gateRequiredAt.length > 0 ? gateRequiredAt : null,
        allowUnattributed: core.getBooleanInput('allow-unattributed'),
        unattributedReason: core.getInput('unattributed-reason').trim() || undefined,
        outputDir: core.getInput('output-dir') || '.',
    };
}
async function run() {
    const input = readInputs();
    const graphql = new resolve_1.GithubGraphqlResolver(input.token, input.owner, input.repo);
    const restResolver = new resolver_1.GithubResolver(input.token, input.owner, input.repo);
    const warmRefs = new Map();
    const pipelineResolver = (0, warm_1.buildPipelineResolver)(restResolver, warmRefs);
    const strategy = (0, range_1.resolveBaselineStrategy)(input.targetVersion);
    const baseline = (0, walk_1.resolveBaselineRef)(process.cwd(), strategy, input.targetVersion);
    const walked = (0, walk_1.walkFirstParent)(process.cwd(), baseline, input.targetVersion);
    core.info(`Range ${baseline}..${input.targetVersion}: ${walked.length} first-parent commits.`);
    const rangeShas = new Set(walked.map((commit) => commit.sha));
    // GitHub writes the pull request number into the subject of the commit it
    // squashes onto the branch, so for nearly every commit the mapping is already
    // in hand: 3662 of 8.9.0's 3694, and 5486 of 8.8.0's 5514. Asking
    // `associatedPullRequests` to rediscover it means walking branch history for
    // every commit — 148 requests for one minor, the slowest phase of the run.
    //
    // Derived, never trusted: the candidate is confirmed against the pull
    // request's own `mergeCommit`, which must BE this commit. That is a stronger
    // signal than `associatedPullRequests`, which reports every pull request
    // whose branch history contains the commit and is what once credited a
    // release to its own merge-back. Anything unconfirmed — no number in the
    // subject, unknown pull request, no merge commit, or a merge commit that is
    // some other commit — falls back to the original query, so a wrong guess
    // cannot become a wrong attribution.
    const candidateBySha = new Map();
    for (const commit of walked) {
        const match = /\(#(\d+)\)\s*$/.exec(commit.message);
        if (match)
            candidateBySha.set(commit.sha, Number(match[1]));
    }
    const metaByNumber = new Map();
    for (const meta of await graphql.fetchPrMetadata([...new Set(candidateBySha.values())])) {
        metaByNumber.set(meta.number, meta);
    }
    const confirmed = new Map();
    const unconfirmed = [];
    for (const commit of walked) {
        const candidate = candidateBySha.get(commit.sha);
        const meta = candidate === undefined ? undefined : metaByNumber.get(candidate);
        if (meta && meta.mergeCommitOid === commit.sha) {
            confirmed.set(commit.sha, {
                number: meta.number,
                baseRefName: meta.baseRefName,
                headRefName: meta.headRefName,
                mergeCommitOid: meta.mergeCommitOid,
            });
        }
        else {
            unconfirmed.push(commit.sha);
        }
    }
    core.info(`Mapped ${confirmed.size} commits from their own subject; ${unconfirmed.length} need the commit-to-PR query.`);
    const fallbackBySha = new Map((await graphql.mapCommitsToPrs(unconfirmed)).map((mapping) => [mapping.sha, mapping.associatedPrs]));
    const commitsForDedupe = walked.map((commit) => {
        const one = confirmed.get(commit.sha);
        return {
            sha: commit.sha,
            message: commit.message,
            associatedPrs: one ? [one] : (fallbackBySha.get(commit.sha) ?? []),
        };
    });
    const { prNumbers, reasons: rangeReasons } = (0, range_1.resolveCommitsToPrs)(commitsForDedupe, input.releaseBranch, rangeShas);
    for (const reason of rangeReasons)
        core.warning(reason);
    // Only the pull requests the fallback discovered are still unfetched.
    for (const meta of await graphql.fetchPrMetadata(prNumbers.filter((number) => !metaByNumber.has(number)))) {
        metaByNumber.set(meta.number, meta);
    }
    // Keyed off prNumbers, which is in walk order, so the output stays stable.
    const metadata = prNumbers.map((number) => metaByNumber.get(number)).filter((meta) => meta !== undefined);
    // Every reference the per-pull-request phase can ask about, learned in one
    // pass. The pipeline resolves the "Related issues" section and, when that
    // yields nothing, scans the whole body — so pre-warm the union of both,
    // each capped by the same policy the resolver applies per call.
    const wanted = new Set();
    for (const pr of metadata) {
        const section = (0, parser_1.extractSection)(pr.body);
        for (const refs of [section ? (0, parser_1.parseRefs)(section) : [], (0, parser_1.parseRefs)(pr.body)]) {
            for (const ref of (0, resolver_1.prioritizeAndCap)(refs)) {
                if (ref.repo === null)
                    wanted.add(ref.number);
            }
        }
    }
    for (const [number, classified] of await graphql.classifyRefs([...wanted])) {
        warmRefs.set(number, classified);
    }
    core.info(`Pre-classified ${warmRefs.size} distinct references in ${Math.ceil(wanted.size / 100)} requests.`);
    const processOne = async (pr) => {
        const warnings = (pr.truncatedFields ?? []).map((field) => `PR #${pr.number}: ${field} exceeded the 20-entry query cap — some entries were not read.`);
        const output = await (0, pipeline_1.processPr)(pipelineResolver, {
            number: pr.number,
            title: pr.title,
            body: pr.body,
            authorLogin: pr.authorLogin,
            mergedAt: pr.mergedAt,
            labels: pr.labels,
            closingIssuesReferences: pr.closingIssuesReferences,
        }, { gateRequiredAt: input.gateRequiredAt });
        if (output.anomaly)
            warnings.push(`PR #${output.number}: ${output.anomaly} (${output.attribution.source}).`);
        for (const reason of output.attribution.reasons)
            warnings.push(`PR #${output.number}: ${reason}`);
        for (const reason of output.categorization.reasons)
            warnings.push(`PR #${output.number}: ${reason}`);
        return {
            renderPr: {
                number: output.number,
                title: output.title,
                section: output.categorization.section,
                visibility: output.categorization.visibility,
                component: output.categorization.component,
                breaking: output.categorization.breaking,
                issueNumbers: output.attribution.issueNumbers,
                // A backport hop delivers via THIS PR's merge, but the backport bot never
                // writes a closing keyword — closingIssuesReferences is always empty for
                // it, so the general signal below would under-report every single one.
                closesIssueNumbers: output.attribution.deliveryPath === 'backportHop'
                    ? output.attribution.issueNumbers
                    : output.attribution.issueNumbers.filter((n) => pr.closingIssuesReferences.includes(n)),
                attributionSource: output.attribution.source,
            },
            // A `merge`-type PR (section: null) is excluded from every render() output
            // regardless of attribution, so it must never trip the unattributed guard.
            bucketed: output.categorization.section !== null &&
                (output.attribution.source === 'unattributed' || output.attribution.source === 'resolutionFailed'),
            warnings,
        };
    };
    // Each pull request's work is independent and almost entirely waiting on the
    // network, so a serial loop spends a minor release's runtime idle: 8.9.0 took
    // ~35 minutes here. Results land in index-keyed slots, never pushed, because
    // completion order is arbitrary while the release notes' order must not be.
    //
    // ponytail: 3 workers, not more. `resolve()` already runs up to CONCURRENCY
    // refs per pull request, so the two limits multiply. Six here — about 30
    // requests in flight — tripped GitHub's SECONDARY rate limit on 8.9.0, which
    // fires on concurrency rather than volume: the primary counter still read
    // 5000/5000 when it hit. The ceiling is burst width, not quota, so the fix is
    // fewer in flight rather than a bigger budget. Raising this wants one shared
    // limit across both levels, not a bigger number here.
    const WORKERS = 3;
    const processed = new Array(metadata.length);
    let cursor = 0;
    try {
        await Promise.all(Array.from({ length: Math.min(WORKERS, metadata.length) }, async () => {
            for (let index = cursor++; index < metadata.length; index = cursor++) {
                processed[index] = await processOne(metadata[index]);
            }
        }));
    }
    finally {
        // Deferring warnings to keep them in walk order must not mean losing them
        // when the run dies partway: a failed run's diagnostics are the ones most
        // worth reading.
        for (const entry of processed) {
            if (entry)
                for (const warning of entry.warnings)
                    core.warning(warning);
        }
    }
    const attributed = [];
    const unattributed = [];
    for (const entry of processed) {
        if (!entry)
            continue;
        (entry.bucketed ? unattributed : attributed).push(entry.renderPr);
    }
    const result = (0, render_1.render)(attributed, unattributed, {
        version: input.targetVersion,
        allowUnattributed: input.allowUnattributed,
        unattributedReason: input.unattributedReason,
    });
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/CHANGELOG-${input.targetVersion}.md`, result.fullAsset);
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/changelog.json`, JSON.stringify(result.changelogJson, null, 2));
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/labels.json`, JSON.stringify(result.labelsJson, null, 2));
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/audit.json`, JSON.stringify(result.auditJson, null, 2));
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/comments.json`, JSON.stringify(result.commentsJson, null, 2));
    core.setOutput('customer-body', result.customerBody);
    // Both bodies, so a reviewer can see exactly what the customer gets vs. the
    // full internal asset — same rendering guard as every other output: written
    // even when the unattributed guard trips, never skipped on failure.
    await core.summary
        .addHeading(`Release notes — ${input.targetVersion}`, 2)
        .addHeading('Customer-facing body', 3)
        .addRaw(result.customerBody)
        .addHeading('Full asset (includes internal-only sections)', 3)
        .addRaw(result.fullAsset)
        .write();
    // Every output above is written even when the unattributed guard trips —
    // audit.json's whole purpose is explaining which PRs and why — so the job
    // fails only AFTER the diagnostic outputs exist on disk.
    if (result.failureReason)
        throw new Error(result.failureReason);
    core.info(`Generated release notes for ${input.targetVersion}: ${attributed.length} attributed PR(s).`);
}
run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));


/***/ }),

/***/ 93:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.summary = exports.setFailed = exports.warning = exports.info = exports.setOutput = exports.getBooleanInput = exports.getInput = void 0;
const node_fs_1 = __nccwpck_require__(24);
/**
 * ponytail: the ~7 GitHub Actions toolkit calls we actually use, inlined.
 * @actions/core drags in @actions/exec + http-client + io (~400kB) for OIDC and
 * command features this action never touches. These are the documented Actions
 * command/file protocols — nothing clever.
 */
const escape = (msg) => msg.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
// The step summary is built as HTML, so escape anything interpolated into it —
// reasons carry user-controlled PR title/body fragments.
const escapeHtml = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const appendEnvFile = (envVar, content) => {
    const file = process.env[envVar];
    if (file)
        (0, node_fs_1.appendFileSync)(file, content);
};
const getInput = (name, opts = {}) => {
    const value = (process.env[`INPUT_${name.toUpperCase().replace(/ /g, '_')}`] ?? '').trim();
    if (opts.required && !value)
        throw new Error(`Input required and not supplied: ${name}`);
    return value;
};
exports.getInput = getInput;
const getBooleanInput = (name) => (0, exports.getInput)(name).toLowerCase() === 'true';
exports.getBooleanInput = getBooleanInput;
// GITHUB_OUTPUT file protocol with a heredoc delimiter (safe for multiline values).
const setOutput = (name, value) => appendEnvFile('GITHUB_OUTPUT', `${name}<<_GHA_EOF_\n${value}\n_GHA_EOF_\n`);
exports.setOutput = setOutput;
const info = (msg) => {
    process.stdout.write(`${msg}\n`);
};
exports.info = info;
const warning = (msg) => {
    process.stdout.write(`::warning::${escape(msg)}\n`);
};
exports.warning = warning;
const setFailed = (msg) => {
    process.stdout.write(`::error::${escape(msg)}\n`);
    process.exitCode = 1;
};
exports.setFailed = setFailed;
class Summary {
    buf = '';
    addHeading(text, level = 1) {
        this.buf += `<h${level}>${escapeHtml(text)}</h${level}>\n`;
        return this;
    }
    addList(items) {
        this.buf += `<ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>\n`;
        return this;
    }
    /** Appends already-formatted Markdown verbatim — GITHUB_STEP_SUMMARY renders
     *  as GitHub-flavored Markdown, so a pre-rendered document (e.g. the
     *  generated changelog) is written as-is rather than escaped as HTML. */
    addRaw(markdown) {
        this.buf += `${markdown}\n`;
        return this;
    }
    async write() {
        appendEnvFile('GITHUB_STEP_SUMMARY', this.buf);
        this.buf = '';
    }
}
exports.summary = new Summary();


/***/ }),

/***/ 631:
/***/ ((__unused_webpack_module, exports) => {


/**
 * Shared GitHub REST plumbing for the three fetch-based adapters (resolver,
 * comment, labels). One definition of the bot's auth / API-version / user-agent
 * headers and the per-repo base URL — previously copied verbatim into each
 * adapter. The adapters stay octokit-free (a handful of endpoints each); this is
 * just the common boilerplate, not a client.
 */
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GITHUB_API = void 0;
exports.fetchWithRetry = fetchWithRetry;
exports.fetchJsonWithRetry = fetchJsonWithRetry;
exports.githubHeaders = githubHeaders;
exports.repoApiUrl = repoApiUrl;
exports.GITHUB_API = 'https://api.github.com';
const USER_AGENT = 'camunda-release-notes-gate';
const GITHUB_API_VERSION = '2022-11-28';
/** A real secondary rate limit clears within minutes; past this, something else is wrong and must surface. */
const MAX_RETRIES = 5;
/** Longest `retry-after` this honours; beyond it the job should fail rather
 *  than hold a runner. GitHub's own secondary-limit hints stay well under. */
const MAX_RETRY_AFTER_MS = 60_000;
/** GitHub reports a throttled REST request as HTTP 429, or HTTP 403 carrying a
 *  `retry-after` (a 403 without one is a real permission failure and must not
 *  be retried). 5xx is a transient backend failure. Mirrors resolve/index.ts's
 *  GraphQL-side retryableStatus — same throttle shapes, REST transport. */
async function retryableStatus(res) {
    if (res.status === 429 || res.status >= 500)
        return true;
    if (res.status !== 403)
        return false;
    if (res.headers.get('retry-after') !== null)
        return true;
    if (res.headers.get('x-ratelimit-remaining') === '0')
        return true;
    // GitHub's SECONDARY rate limit — the one that fires on concurrency rather
    // than on volume — answers 403 and often names itself only in the body, with
    // the primary counter still reading full. Indistinguishable from a permission
    // failure by status alone, so read the body of a 403 (from a clone, leaving
    // the caller's stream intact) before deciding this job cannot proceed.
    try {
        return /rate limit/i.test(await res.clone().text());
    }
    catch {
        return false;
    }
}
/** The server's own wait, when it names one, else exponential backoff. `null`
 *  when the request never produced a response at all. */
function backoffMs(res, attempt) {
    const header = res?.headers.get('retry-after') ?? null;
    const seconds = header === null ? NaN : Number(header);
    if (Number.isFinite(seconds) && seconds >= 0)
        return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
    return 2 ** attempt * 1000;
}
/**
 * `fetch`, retrying a throttled or transiently failed REST request with
 * backoff instead of aborting the whole generation job on one bad response.
 * Never retries a non-throttle failure (e.g. a bare 403, a 404) — the caller
 * sees those immediately.
 */
async function fetchWithRetry(url, init, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
    for (let attempt = 0;; attempt++) {
        let res;
        try {
            res = await fetch(url, init);
        }
        catch (error) {
            // `fetch` REJECTS on a socket-level failure — connection reset, socket
            // hang-up, DNS blip — rather than returning a Response, so none of the
            // status handling below ever sees it. Left unguarded this aborts the
            // whole job on one blip, which over the thousands of calls a minor
            // release makes is close to certain.
            if (attempt >= MAX_RETRIES - 1) {
                const detail = error instanceof Error ? error.message : String(error);
                throw new Error(`GitHub API request never completed past ${MAX_RETRIES} attempts (${url}): ${detail}`);
            }
            await sleepImpl(backoffMs(null, attempt));
            continue;
        }
        if (res.ok || !(await retryableStatus(res)))
            return res;
        if (attempt >= MAX_RETRIES - 1) {
            throw new Error(`GitHub API kept returning HTTP ${res.status} past ${MAX_RETRIES} attempts (${url}).`);
        }
        await sleepImpl(backoffMs(res, attempt));
    }
}
/**
 * `fetchWithRetry` plus the body read, so a truncated or empty body is retried
 * like any other transient instead of throwing a SyntaxError past the retry
 * loop. GitHub answers that way under load exactly as readily as it answers
 * 502, and parsing outside the loop meant one such body killed the run.
 */
async function fetchJsonWithRetry(url, init, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
    for (let attempt = 0;; attempt++) {
        const res = await fetchWithRetry(url, init, sleepImpl);
        if (!res.ok)
            return { ok: false, status: res.status };
        try {
            return { ok: true, status: res.status, data: (await res.json()) };
        }
        catch {
            if (attempt >= MAX_RETRIES - 1) {
                throw new Error(`GitHub API returned an unparseable body past ${MAX_RETRIES} attempts (${url}).`);
            }
            await sleepImpl(backoffMs(null, attempt));
        }
    }
}
/** Auth + content-negotiation headers for the plain `GITHUB_TOKEN` every
 *  caller passes in. This action resolves from the PR head on `pull_request`
 *  (see the gate workflow's security-model header), so it must never be
 *  given a privileged token such as MONOREPO_RELEASE_APP. Pass `json: true`
 *  for write requests that send a JSON body. */
function githubHeaders(token, opts = {}) {
    const headers = {
        authorization: `Bearer ${token}`,
        accept: 'application/vnd.github+json',
        'x-github-api-version': GITHUB_API_VERSION,
        'user-agent': USER_AGENT,
    };
    if (opts.json)
        headers['content-type'] = 'application/json';
    return headers;
}
/** `https://api.github.com/repos/<owner>/<repo>` — the common request prefix. */
function repoApiUrl(owner, repo) {
    return `${exports.GITHUB_API}/repos/${owner}/${repo}`;
}


/***/ }),

/***/ 883:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.SECTION_HEADING = exports.OPT_OUT_PHRASE = void 0;
exports.stripHtmlComments = stripHtmlComments;
exports.stripCode = stripCode;
exports.parseRefs = parseRefs;
exports.extractSection = extractSection;
exports.isOptOutTicked = isOptOutTicked;
/**
 * Pure, section-scoped reference parser. Shared verbatim with the generator
 * (#57713) — no IO, no repo awareness. Cross-repo detection and issue-vs-PR
 * classification belong to the Resolver, not here.
 */
/** The template's opt-out phrase. Kept as an exported constant so the PR template
 *  and the parser cannot drift (enforced by the repo-constant grep in CI). */
exports.OPT_OUT_PHRASE = 'this pr does not need a linked issue';
/** The section whose refs the gate evaluates. */
exports.SECTION_HEADING = 'Related issues';
// GitHub's closing keywords + our custom "completes". Case-insensitive.
const CLOSING = /^(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|completes?)$/i;
const RELATES = /^relates?\s+to$/i;
const BACKPORT = /^backport\s+of$/i;
// Optional keyword prefix shared by both ref shapes.
const KW = String.raw `(?:\b(close[sd]?|fix(?:e[sd])?|resolve[sd]?|completes?|relates?\s+to|backport\s+of)\b[\s:]+)?`;
const OWNER_REPO = String.raw `([A-Za-z0-9][\w.-]*\/[A-Za-z0-9][\w.-]*)`;
// "closes #12", "camunda/other#7", bare "#12".
const SHORTHAND = new RegExp(KW + `(?:${OWNER_REPO})?#(\\d+)`, 'gi');
// Full GitHub URLs: ".../owner/repo/issues/12" or ".../pull/12".
const URL = new RegExp(KW + String.raw `https?:\/\/github\.com\/${OWNER_REPO}\/(?:issues|pull)\/(\d+)`, 'gi');
function kindOf(keyword) {
    if (keyword && BACKPORT.test(keyword))
        return 'backport';
    if (keyword && RELATES.test(keyword))
        return 'contributor';
    if (keyword && CLOSING.test(keyword))
        return 'closing';
    return 'contributor'; // bare "#N"
}
/**
 * Strip HTML comments before any parsing. The PR template's own instructional
 * `<!-- ... closes #1234 ... -->` block lives inside "## Related issues" and is
 * invisible in GitHub's rendered body, so a PR that leaves the boilerplate
 * untouched must NOT be attributed to whatever issue the comment names.
 */
function stripHtmlComments(text) {
    return text.replace(/<!--[\s\S]*?-->/g, '');
}
/**
 * Strip fenced and inline Markdown code before any parsing. A reviewer citing
 * an example — `` `closes #1234` `` in prose, or a fenced snippet quoting the
 * template — must not be mistaken for the author's own ref or opt-out tick.
 */
function stripCode(text) {
    return text.replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
}
/** Extract every reference from the given text (already scoped by the caller). */
function parseRefs(text) {
    text = stripCode(stripHtmlComments(text));
    const refs = [];
    const seen = new Set(); // dedupe by match offset
    const push = (match, repo, num) => {
        if (seen.has(match.index))
            return;
        seen.add(match.index);
        const keyword = match[1] ? match[1].toLowerCase().replace(/\s+/g, ' ') : null;
        refs.push({
            raw: match[0].trim(),
            number: Number(num),
            repo: repo ?? null,
            keyword,
            kind: kindOf(keyword),
            index: match.index,
        });
    };
    for (const match of text.matchAll(URL))
        push(match, match[2] ?? null, match[3]);
    for (const match of text.matchAll(SHORTHAND))
        push(match, match[2] ?? null, match[3]);
    return refs.sort((first, second) => first.index - second.index);
}
/**
 * Slice out a markdown section body: everything after the matching heading up
 * to the next heading of any level (or EOF). Returns null if absent.
 */
function extractSection(body, heading = exports.SECTION_HEADING) {
    const lines = stripHtmlComments(body).split(/\r?\n/);
    const headingRe = new RegExp(`^#{1,6}\\s+${escapeRe(heading)}\\s*$`, 'i');
    const start = lines.findIndex((line) => headingRe.test(line.trim()));
    if (start < 0)
        return null;
    const rest = lines.slice(start + 1);
    const end = rest.findIndex((line) => /^#{1,6}\s+\S/.test(line.trim()));
    return (end < 0 ? rest : rest.slice(0, end)).join('\n');
}
/** True when the opt-out checkbox is present and ticked. */
function isOptOutTicked(body) {
    const re = new RegExp(String.raw `^\s*[-*]\s*\[x\]\s*.*${escapeRe(exports.OPT_OUT_PHRASE)}`, 'im');
    return re.test(stripCode(stripHtmlComments(body)));
}
function escapeRe(literal) {
    return literal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}


/***/ }),

/***/ 782:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.processPr = processPr;
const attribution_1 = __nccwpck_require__(233);
const categorize_1 = __nccwpck_require__(493);
const parser_1 = __nccwpck_require__(883);
const title_1 = __nccwpck_require__(150);
/**
 * The legacy body-wide scan is the chain's last step, so its refs are only
 * resolved when the earlier steps cannot terminate — an opt-out, an eligible
 * section ref, or a native reference all decide the outcome without it. Every
 * ref costs an API call, and the section refs would otherwise be resolved a
 * second time as part of the body they live in.
 */
async function attributeDirectly(resolver, body, closingIssuesReferences) {
    const section = (0, parser_1.extractSection)(body);
    const optOut = section ? (0, parser_1.isOptOutTicked)(section) : false;
    const sectionRefs = section ? await resolver.resolveRefs((0, parser_1.parseRefs)(section)) : [];
    const needsLegacyScan = !optOut && !(0, attribution_1.hasEligibleRefs)(sectionRefs) && closingIssuesReferences.length === 0;
    const legacyRefs = needsLegacyScan ? await resolver.resolveRefs((0, parser_1.parseRefs)(body)) : [];
    return (0, attribution_1.decideAttribution)({ optOut, sectionRefs, closingIssuesReferences, legacyRefs });
}
/** Attribution outcomes that found nothing to attribute to — the trigger for
 *  the bot-link exemption. Mirrors the gate's own failing-link outcomes, not
 *  just "nothing found". */
const UNRESOLVED_SOURCES = new Set(['unattributed', 'resolutionFailed']);
/**
 * Direct scan, then the backport hop (inheriting the original's decision,
 * C7/V2), then the bot link exemption LAST — an exempt bot that did link a real
 * issue keeps that attribution rather than being overridden by the exemption.
 *
 * The hop fires whenever a backport marker is present, NOT only when the
 * backport's own body yielded nothing: C7 makes the original canonical, and a
 * backport body is a bot's paraphrase of it. `backport-action` copies the
 * original's refs into `relates to ${issue_refs}` without stripping HTML
 * comments, so the PR template's own `<!-- closes #1234 -->` examples arrive
 * here as visible, author-looking refs — four of them, plus the real one. A
 * body-wide scan of that *succeeds*, which is exactly why gating the hop on
 * failure let a 2018 issue title describe a 2026 fix. Deciding from the
 * original makes the outcome independent of whatever the bot wrote.
 *
 * An explicit opt-out tick on the backport is the one thing that outranks the
 * original: unlike a copied ref, it is a deliberate statement about this PR.
 */
async function attributePr(resolver, pr, original) {
    let decision = await attributeDirectly(resolver, pr.body, pr.closingIssuesReferences);
    let mergedAt = pr.mergedAt;
    if (decision.source !== 'optOut') {
        // Resolves to null when there is no backport marker, so this costs nothing
        // for an ordinary PR — and for a backport bot the original is fetched for
        // the inherited title anyway, memoized by the caller.
        const originalPull = await original();
        if (originalPull) {
            const originalDecision = await attributeDirectly(resolver, originalPull.body, []);
            decision = { ...originalDecision, deliveryPath: 'backportHop' };
            mergedAt = originalPull.mergedAt ?? pr.mergedAt;
        }
    }
    if (UNRESOLVED_SOURCES.has(decision.source) && (0, title_1.isLinkExemptAuthor)(pr.authorLogin)) {
        return {
            decision: {
                source: 'botExempt',
                issueNumbers: [],
                deliveryPath: 'direct',
                reasons: [`Author ${pr.authorLogin} is exempt from the PR-issue link requirement.`],
            },
            mergedAt,
        };
    }
    return { decision, mergedAt };
}
/**
 * The category-detection title and the display title come from the same lookup:
 * an inherit-original bot's own title is garbage for both purposes. The
 * `[Backport ...]` marker is stripped either way — noise for the customer.
 */
async function categorizePr(resolver, pr, original, override) {
    const inherited = override === 'inherit-original' ? (await original())?.title : undefined;
    const displayTitle = (0, categorize_1.stripBackportPrefix)(inherited ?? pr.title);
    const componentLabels = pr.labels.filter((label) => label.startsWith('component/'));
    const categorization = (0, categorize_1.categorize)({
        title: displayTitle,
        authorLogin: pr.authorLogin,
        componentLabels,
        breakingChangeLabel: pr.labels.includes('BREAKING CHANGE'),
    });
    return { displayTitle, categorization };
}
/**
 * The customer-facing title, in priority order: a `deps:` PR's parsed
 * "name: old → new"; else the FIRST linked issue's own title (written for a
 * release-notes reader, unlike the PR title); else the PR's own title.
 */
async function resolveDisplayTitle(resolver, pr, categorization, attribution, fallbackTitle) {
    if (categorization.section === 'Dependency updates') {
        const dependencyLine = (0, categorize_1.parseDependencyUpdate)({ title: pr.title, body: pr.body });
        if (dependencyLine)
            return dependencyLine;
    }
    const [primaryIssue] = attribution.issueNumbers;
    if (primaryIssue !== undefined) {
        const issueTitle = await resolver.fetchIssueTitle(primaryIssue);
        if (issueTitle)
            return issueTitle;
    }
    return fallbackTitle;
}
async function processPr(resolver, pr, options) {
    const backport = (0, parser_1.parseRefs)(pr.body).find((ref) => ref.kind === 'backport');
    const override = pr.authorLogin ? categorize_1.BOT_CATEGORY_OVERRIDES[pr.authorLogin] : undefined;
    // Both the attribution hop and the inherit-original title want the same
    // original PR — fetch it at most once per PR, and only if one of them asks.
    let pending;
    const original = () => (pending ??= backport ? resolver.fetchOriginalPull(backport.number, backport.repo) : Promise.resolve(null));
    const { decision: attribution, mergedAt } = await attributePr(resolver, pr, original);
    const { displayTitle, categorization } = await categorizePr(resolver, pr, original, override);
    const title = await resolveDisplayTitle(resolver, pr, categorization, attribution, displayTitle);
    const anomaly = (0, attribution_1.evaluatePostGateAnomaly)({
        mergedAt,
        gateRequiredAt: options.gateRequiredAt,
        source: attribution.source,
    });
    return { number: pr.number, title, attribution, categorization, anomaly };
}


/***/ }),

/***/ 53:
/***/ ((__unused_webpack_module, exports) => {


/**
 * The pure part of the range resolver (#50968): which previous point to diff
 * against, and how to turn git's answer into a deduped PR list. The git calls
 * themselves live in ./walk.
 */
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.resolveBaselineStrategy = resolveBaselineStrategy;
exports.resolveCommitsToPrs = resolveCommitsToPrs;
// Alphas are 1-based: an `-alpha0` would make the previous-alpha baseline
// `-alpha-1`, a ref that cannot exist, so it is rejected as unrecognized.
//
// Dotted alphas (`8.8.0-alpha4.1`) are deliberately NOT accepted, though the
// 8.8 line has a couple and zcl parses them. The release process no longer
// cuts them, so this is a closed shape, not an oversight — do not widen the
// regex on the strength of those tags alone.
const VERSION = /^(\d+)\.(\d+)\.(\d+)(?:-alpha([1-9]\d*))?$/;
// Release candidates are 1-based too, and appear at every level: `8.9.0-rc1`,
// `8.7.6-rc2`, `8.10.0-alpha1-rc3`. Only the suffix is matched here — what it
// is attached to still has to satisfy VERSION.
const RC_SUFFIX = /-rc[1-9]\d*$/;
function parseVersion(version, reportAs = version) {
    const match = VERSION.exec(version);
    if (!match)
        throw new Error(`Not a recognized release version: "${reportAs}"`);
    return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]), alpha: match[4] ? Number(match[4]) : null };
}
function format(v) {
    const base = `${v.major}.${v.minor}.${v.patch}`;
    // Explicit null check, not truthiness: an `alpha: 0` reaching here would
    // otherwise format as a stable tag and send the walk at the wrong baseline.
    return v.alpha == null ? base : `${base}-alpha${v.alpha}`;
}
/** `minor - 1`, guarded: at minor 0 the previous line belongs to the previous
 *  major, whose last minor no arithmetic on this version string can name. */
function previousMinor(v, target) {
    if (v.minor === 0) {
        throw new Error(`Unsupported release version "${target}": the baseline for the first minor of a major is the previous ` +
            `major's last minor, which cannot be derived from the version number alone.`);
    }
    return v.minor - 1;
}
/** The baseline to diff `target` against, from the version string alone — no
 *  tag list to consult, every case is arithmetic on the version number. */
function resolveBaselineStrategy(target) {
    // A candidate is a candidate *for* a version, so its notes cover that
    // version's whole range: drop `-rcN` and resolve the version it stands for.
    // Deliberately unlike zcl, which walks rcN back to rc(N-1) — that suits its
    // incremental issue labelling, but would reduce a candidate's changelog to
    // the delta since the last candidate rather than the release's contents.
    // Only the baseline is computed from the stripped string; callers keep
    // walking and labelling with the real `-rcN` tag.
    const v = parseVersion(target.replace(RC_SUFFIX, ''), target);
    // An alpha is a pre-release of a minor, so it only ever carries patch 0.
    // Without this, `X.Y.1-alpha1` falls through to the previous-alpha branch and
    // resolves to `X.Y.1` — the target's own base version, a tag never cut.
    if (v.alpha !== null && v.patch !== 0) {
        throw new Error(`Unsupported release version "${target}": an alpha is a pre-release of a minor, so it must carry patch 0.`);
    }
    // alpha1-of-cycle: no prior tag on this line exists yet, so always the fork
    // point off the previous minor's stable branch, never a tag lookup (V5).
    if (v.alpha === 1 && v.patch === 0) {
        return { kind: 'forkPoint', otherRef: `origin/stable/${v.major}.${previousMinor(v, target)}` };
    }
    if (v.alpha !== null) {
        return { kind: 'previousTag', ref: format({ ...v, alpha: v.alpha - 1 }) };
    }
    if (v.patch > 0) {
        return { kind: 'previousTag', ref: format({ ...v, patch: v.patch - 1 }) };
    }
    // Minor release: fork point between the previous minor's release tag and this target.
    const previousMinorTag = format({ major: v.major, minor: previousMinor(v, target), patch: 0 });
    return { kind: 'forkPoint', otherRef: previousMinorTag };
}
/** The only legitimate PR-less commits (C12); anything else without a PR on a
 *  protected branch is a ruleset-bypass anomaly.
 *
 *  The `Revert "..."` form is the orphaned-tag repair: when a release job has to
 *  redo its own version bumps it reverts them first, so those reverts are as
 *  much release automation as the commits they undo. Without them here, every
 *  repaired release reports its reverts as ruleset bypasses. */
const AUTOMATION_WHITELIST = /^(?:Revert ")?\[maven-release-plugin\]/;
/** A release branch is `release-<version>` — `release-8.9.19`,
 *  `release-8.10.0-alpha5`. Anchored on the version so it cannot swallow a
 *  feature branch that merely starts with the word. */
const RELEASE_BRANCH = /^release-\d+\.\d+\.\d+/;
/**
 * A release-branch merge-back delivers nothing of its own. It merges
 * `release-X.Y.Z` back into the line it was cut from, and everything it carries
 * was already published in that release's own notes. The branch shape is the
 * definition, not a heuristic: no other pull request goes from a release branch
 * into a stable line (or into `main`, for a pre-branch alpha).
 *
 * D25 asks for these to carry a `merge:` title at the source, which would also
 * exclude them via `categorize`. That fix lives in release automation outside
 * this repository and cannot reach pull requests that already merged, so the
 * topology is checked here as well. Relying on the title alone would put the
 * previous release's merge-back in every release's notes, and — because it
 * links no issue — in every release's unattributed bucket, failing the gate on
 * the same known-benign pull request every single time.
 */
function isReleaseMergeBack(pr) {
    // Version-shaped, not a bare `release-` prefix: a feature branch called
    // `release-notes-gate` targeting a stable line is ordinary work, not a
    // merge-back, and excluding it would drop real delivered work.
    return RELEASE_BRANCH.test(pr.headRefName) && (pr.baseRefName.startsWith('stable/') || pr.baseRefName === 'main');
}
/**
 * Dedupe a first-parent commit walk to one entry per PR. Ambiguity rule: prefer
 * the PR targeting the release branch; still tied -> audit, never guess.
 *
 * `rangeShas` is the walk's own commits. A pull request ships in this range only
 * if its merge landed among them: commits pushed straight onto a release branch
 * — the release plugin's version bumps, and the reverts that repair an orphaned
 * tag — have no pull request of their own, so GitHub credits them to whichever
 * one later swept that branch into stable. That is the *next* release's
 * merge-back, whose merge commit is not in this range at all. Without the
 * check, 8.9.19's notes listed #62049, merged three days after the tag was cut.
 */
function resolveCommitsToPrs(commits, releaseBranch, rangeShas) {
    const reasons = [];
    // Insertion-ordered, so this both dedupes and preserves walk order.
    const prNumbers = new Set();
    for (const commit of commits) {
        // Three outcomes, kept apart because they are three different facts about a
        // commit and collapsing them produces a wrong audit line: a merge-back is
        // excluded even though it merged here, while an out-of-range PR is excluded
        // precisely because it did not.
        const mergeBacks = commit.associatedPrs.filter(isReleaseMergeBack);
        const candidates = commit.associatedPrs.filter((pr) => !isReleaseMergeBack(pr));
        const shipped = candidates.filter((pr) => {
            if (pr.mergeCommitOid === null) {
                // Keep it: a MERGED pull request without a merge commit is a GitHub
                // data anomaly, and under-inclusion is the failure this epic exists to
                // fix. Say so, so the operator can check rather than wonder.
                reasons.push(`PR #${pr.number}: GitHub reported no merge commit — kept, but its range membership is unverified.`);
                return true;
            }
            return rangeShas.has(pr.mergeCommitOid);
        });
        if (shipped.length === 0) {
            // Credited only to a merge-back: either the merge-back commit itself, or
            // a commit pushed straight onto the release branch that one swept in.
            // Release plumbing either way — nothing delivered, nothing to report.
            if (mergeBacks.length > 0 && candidates.length === 0)
                continue;
            if (AUTOMATION_WHITELIST.test(commit.message))
                continue;
            const list = candidates.map((pr) => `#${pr.number}`).join(', ');
            reasons.push(candidates.length === 0
                ? `Ruleset-bypass anomaly: commit ${commit.sha} has no associated pull request and does not match the automation whitelist.`
                : `Commit ${commit.sha} is credited only to pull requests that did not merge inside this range (${list}), and its message does not match the automation whitelist — excluded.`);
            continue;
        }
        if (shipped.length === 1) {
            prNumbers.add(shipped[0].number);
            continue;
        }
        const matchingBranch = shipped.filter((pr) => pr.baseRefName === releaseBranch);
        if (matchingBranch.length === 1) {
            prNumbers.add(matchingBranch[0].number);
        }
        else {
            const list = shipped.map((pr) => `#${pr.number}`).join(', ');
            reasons.push(`Ambiguous commit ${commit.sha}: associated with multiple pull requests (${list}) and no unique match targeting ${releaseBranch} — never guessing.`);
        }
    }
    return { prNumbers: [...prNumbers], reasons };
}


/***/ }),

/***/ 600:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.resolveBaselineRef = resolveBaselineRef;
exports.walkFirstParent = walkFirstParent;
const node_child_process_1 = __nccwpck_require__(421);
// Unit separator: never appears in a commit subject, unlike ":" or "|".
const FIELD_SEP = '\x1f';
/** Turn a `BaselineStrategy` into a ref: previousTag already names one, forkPoint needs `merge-base`. */
function resolveBaselineRef(repoDir, strategy, target) {
    if (strategy.kind === 'previousTag')
        return strategy.ref;
    return (0, node_child_process_1.execFileSync)('git', ['merge-base', target, strategy.otherRef], { cwd: repoDir, encoding: 'utf8' }).trim();
}
function walkFirstParent(repoDir, baseline, target) {
    const output = (0, node_child_process_1.execFileSync)('git', ['log', `${baseline}..${target}`, '--first-parent', `--format=%H${FIELD_SEP}%s`], { cwd: repoDir, encoding: 'utf8' });
    return output
        .split('\n')
        .filter((line) => line.length > 0)
        .map((line) => {
        const [sha, message] = line.split(FIELD_SEP);
        return { sha: sha, message: message ?? '' };
    });
}


/***/ }),

/***/ 624:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.SCHEMA_VERSION = void 0;
exports.render = render;
/**
 * Turns the attributed-and-categorized PR list into the outputs downstream
 * reads. Pure: which issues a PR actually closed is supplied by the caller,
 * never derived here from a `closes` keyword — an accidental keyword on a
 * non-final PR must not stamp a premature "Released".
 */
/** V6: every JSON output carries this, so a format change has to bump it
 *  deliberately instead of consumers misreading a shape they weren't built for. */
exports.SCHEMA_VERSION = '1.0.0';
const SECTION_ORDER = [
    'Features',
    'Bug Fixes',
    'Performance',
    'Documentation',
    'Dependency updates',
    'Reverts',
    'Changes without a tracked issue',
    'Maintenance', // asset-only, so last — never reached in the customer body
    'Uncategorized',
];
/** D19: an opt-out PR is grouped under its own section, never its type's. */
function groupNameFor(pr) {
    if (pr.attributionSource === 'optOut')
        return 'Changes without a tracked issue';
    return pr.section ?? 'Uncategorized';
}
/** C1: the issue is the grouping key. A PR with no issue — opt-out, bot-exempt,
 *  unattributed — has nothing to group under and stays a single-PR entry. */
function entryKeyFor(pr) {
    return pr.issueNumbers.length > 0 ? `issue:${pr.issueNumbers[0]}` : `pr:${pr.number}`;
}
/** A section absent from SECTION_ORDER sorts after every known one, matching
 *  the output order below, which appends unknown names rather than dropping them. */
function sectionRank(name) {
    const index = SECTION_ORDER.indexOf(name);
    return index === -1 ? SECTION_ORDER.length : index;
}
/**
 * Where a group's PRs disagree on section — a `feat`, a `fix` and two
 * `refactor`s delivering one issue — the most customer-visible section wins,
 * and the entry appears there once rather than repeating under each.
 *
 * Deliberately not "the section of the PR that closed the issue": that needs
 * `closesIssueNumbers`, which is a proxy pending a real per-issue closer
 * lookup, and has no answer at all when nothing in the range closed the issue.
 * Ranking by visibility needs neither, so a wrong closer can never misplace an
 * entry, and it errs toward showing — the direction this epic exists to fix.
 */
function toEntries(prs) {
    const grouped = new Map();
    for (const pr of prs) {
        const key = entryKeyFor(pr);
        const list = grouped.get(key) ?? [];
        list.push(pr);
        grouped.set(key, list);
    }
    return [...grouped.values()].map((group) => {
        // Non-empty by construction, and ties keep the first PR in range order.
        const lead = group.reduce((best, pr) => sectionRank(groupNameFor(pr)) < sectionRank(groupNameFor(best)) ? pr : best);
        return {
            groupName: groupNameFor(lead),
            title: lead.title,
            issueNumbers: [...new Set(group.flatMap((pr) => pr.issueNumbers))],
            prNumbers: group.map((pr) => pr.number),
            breaking: group.some((pr) => pr.breaking),
        };
    });
}
function renderSectionedBody(prs) {
    const entries = toEntries(prs);
    const groups = new Map();
    for (const entry of entries) {
        const list = groups.get(entry.groupName) ?? [];
        list.push(entry);
        groups.set(entry.groupName, list);
    }
    const lines = [];
    const breaking = entries.filter((entry) => entry.breaking);
    if (breaking.length > 0) {
        lines.push('## Breaking changes', '', ...breaking.map((entry) => renderLine(entry)), '');
    }
    const orderedNames = [...SECTION_ORDER, ...[...groups.keys()].filter((name) => !SECTION_ORDER.includes(name))];
    for (const name of orderedNames) {
        const list = groups.get(name);
        if (!list?.length)
            continue;
        lines.push(`## ${name}`, '', ...list.map((entry) => renderLine(entry)), '');
    }
    return lines.join('\n').trim();
}
function renderLine(entry) {
    const prs = entry.prNumbers.map((n) => `#${n}`).join(', ');
    if (entry.issueNumbers.length === 0)
        return `- ${entry.title} (${prs})`;
    return `- ${entry.title} (${entry.issueNumbers.map((n) => `#${n}`).join(', ')}) — ${prs}`;
}
function commentFor(pr, issueNumber, version) {
    return pr.closesIssueNumbers.includes(issueNumber)
        ? { relationKind: 'closing', text: `Released in ${version} (#${pr.number}).` }
        : { relationKind: 'contributor', text: `Partially delivered in ${version} by #${pr.number}.` };
}
/**
 * The gate bucket holds two different failures — a PR that declared no issue at
 * all, and one whose every declared ref turned out to be dead. They need
 * opposite fixes (add a link vs. repair the target), so name them apart: a
 * release operator reading "unattributed" against a PR that visibly *has* a
 * `closes` line has no way to tell that the referenced issue is what is gone.
 */
function describeGuardFailure(bucket) {
    const list = (prs) => prs.map((pr) => `#${pr.number}`).join(', ');
    const noRefs = bucket.filter((pr) => pr.attributionSource === 'unattributed');
    const deadRefs = bucket.filter((pr) => pr.attributionSource === 'resolutionFailed');
    const parts = [`Release-notes attribution gate failed for ${bucket.length} pull request(s).`];
    if (noRefs.length > 0) {
        parts.push(`No issue reference found: ${list(noRefs)} — add a linked issue to the PR's "Related issues" section.`);
    }
    if (deadRefs.length > 0) {
        parts.push(`Every referenced issue was unresolvable: ${list(deadRefs)} — the reference exists but its target is deleted, ` +
            'transferred, or unreadable with this token; repair the reference rather than the PR body.');
    }
    parts.push('Set allow-unattributed=true with a non-empty unattributed-reason to override.');
    return parts.join(' ');
}
function render(prs, unattributed, options) {
    const guardFailed = unattributed.length > 0 && (!options.allowUnattributed || !options.unattributedReason);
    const failureReason = guardFailed ? describeGuardFailure(unattributed) : undefined;
    // A non-empty reason is proven whenever the guard passed with `unattributed` present.
    const unattributedReason = options.unattributedReason ?? '';
    const all = [...prs, ...unattributed];
    const customerPrs = prs.filter((pr) => pr.visibility === 'customer' && pr.section !== null);
    const assetPrs = all.filter((pr) => pr.section !== null);
    const customerBody = renderSectionedBody(customerPrs);
    const fullAsset = renderSectionedBody(assetPrs);
    const commentEntries = all.flatMap((pr) => pr.issueNumbers.map((issueNumber) => ({
        issueNumber,
        prNumber: pr.number,
        ...commentFor(pr, issueNumber, options.version),
        marker: `<!-- release-notes:${options.version}:issue-${issueNumber} -->`,
    })));
    const overrides = unattributed.map((pr) => ({ number: pr.number, reason: unattributedReason }));
    return {
        customerBody,
        fullAsset,
        changelogJson: { schemaVersion: exports.SCHEMA_VERSION, version: options.version, prs: all },
        labelsJson: {
            schemaVersion: exports.SCHEMA_VERSION,
            version: options.version,
            issues: [...new Set(all.flatMap((pr) => pr.issueNumbers))],
            pullRequests: all.map((pr) => pr.number),
        },
        auditJson: { schemaVersion: exports.SCHEMA_VERSION, version: options.version, overrides },
        commentsJson: { schemaVersion: exports.SCHEMA_VERSION, version: options.version, entries: commentEntries },
        failureReason,
    };
}


/***/ }),

/***/ 940:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubGraphqlResolver = exports.RetriesExhaustedError = exports.RATE_LIMITED_ERROR_TYPE = void 0;
const github_1 = __nccwpck_require__(631);
const GRAPHQL_URL = 'https://api.github.com/graphql';
/**
 * The two batch queries differ by an order of magnitude in server-side cost, so
 * they cannot share one size. `associatedPullRequests` makes GitHub walk branch
 * history per commit; `pullRequest(number:)` is a direct node lookup.
 *
 * Measured against camunda/camunda: 100 commit aliases return HTTP 502 after
 * ~11s (a server-side timeout, not a throttle — it fails identically on retry),
 * 50 take 7.2s, 25 take 3.3s. 25 is also marginally faster per commit overall,
 * so the margin costs nothing. A shared size of 100 meant every range longer
 * than 100 commits — every alpha and every minor — died on its first request.
 */
const COMMIT_BATCH_SIZE = 25;
/** 100 aliases return in 0.9s: a direct lookup, not a history walk. */
const PR_METADATA_BATCH_SIZE = 100;
/** A real secondary rate limit clears within minutes; past this, something else is wrong and must surface. */
const MAX_RETRIES = 5;
exports.RATE_LIMITED_ERROR_TYPE = 'RATE_LIMITED';
/** GitHub reports "no such node" as a field-level error, not a null field, and
 *  still returns the rest of the batch alongside it. */
const NOT_FOUND_ERROR_TYPE = 'NOT_FOUND';
/**
 * Thrown when a request failed every retry for a reason that a smaller request
 * might survive — a timeout, a 5xx, an unparseable body. Distinct from a
 * malformed-but-well-formed-HTTP response, which fails identically at any size:
 * bisecting one of those turns a single clear error into a storm of requests
 * and buries it.
 */
class RetriesExhaustedError extends Error {
}
exports.RetriesExhaustedError = RetriesExhaustedError;
/** Longest `retry-after` this honours; beyond it the job should fail rather
 *  than hold a runner. GitHub's own secondary-limit hints stay well under. */
const MAX_RETRY_AFTER_MS = 60_000;
/**
 * GitHub reports a throttled GraphQL request three different ways: a
 * `RATE_LIMITED` error type inside a 200, HTTP 429, or HTTP 403 carrying a
 * `retry-after` (a 403 without one is a real permission failure and must not
 * be retried). 5xx is separate — a transient GraphQL backend failure, routine
 * on the multi-alias batch queries this client sends.
 */
async function retryableStatus(res) {
    if (res.status === 429 || res.status >= 500)
        return true;
    if (res.status !== 403)
        return false;
    if (res.headers.get('retry-after') !== null)
        return true;
    if (res.headers.get('x-ratelimit-remaining') === '0')
        return true;
    // The secondary rate limit fires on concurrency, answers 403, and names
    // itself only in the body while the primary counter still reads full.
    try {
        return /rate limit/i.test(await res.clone().text());
    }
    catch {
        return false;
    }
}
/** The server's own wait, when it names one, else exponential backoff. */
function backoffMs(res, attempt) {
    const header = res?.headers.get('retry-after');
    const seconds = header === null || header === undefined ? NaN : Number(header);
    if (Number.isFinite(seconds) && seconds >= 0)
        return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
    return 2 ** attempt * 1000;
}
/** The one `associatedPullRequests` selection both query shapes share.
 *
 *  `headRefName` and `mergeCommit` are read here rather than in the metadata
 *  phase because both feed range membership, which is decided before any PR
 *  metadata is fetched — and they are free on a connection already selected. */
const prConnection = (afterArg = '') => `associatedPullRequests(first: 10${afterArg}) { nodes { number baseRefName headRefName state mergeCommit { oid } } pageInfo { hasNextPage endCursor } }`;
function assertField(value, description) {
    if (value === null || value === undefined)
        throw new Error(`Malformed GraphQL response: missing ${description}`);
    return value;
}
/** One commit's `associatedPullRequests` page, from whichever query shape produced it. */
function readPrPage(commit, sha) {
    const connection = assertField(commit.associatedPullRequests, `associatedPullRequests on commit ${sha}`);
    return {
        nodes: assertField(connection.nodes, `associatedPullRequests.nodes on commit ${sha}`),
        pageInfo: assertField(connection.pageInfo, `associatedPullRequests.pageInfo on commit ${sha}`),
    };
}
/**
 * GraphQL's `author.login` omits the `[bot]` suffix REST always includes for the
 * same actor (e.g. `monorepo-devops-automation`). Every bot-identity set in this
 * package is keyed on the REST convention, so normalize to it via the
 * `__typename: Bot` discriminator instead of leaving every map unmatched.
 */
function normalizeAuthorLogin(author) {
    if (!author?.login)
        return undefined;
    return author.__typename === 'Bot' && !author.login.endsWith('[bot]') ? `${author.login}[bot]` : author.login;
}
class GithubGraphqlResolver {
    token;
    owner;
    repo;
    fetchImpl;
    sleepImpl;
    constructor(token, owner, repo, fetchImpl = fetch, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.fetchImpl = fetchImpl;
        this.sleepImpl = sleepImpl;
    }
    async mapCommitsToPrs(shas) {
        const results = [];
        for (let i = 0; i < shas.length; i += COMMIT_BATCH_SIZE) {
            results.push(...(await this.mapCommitBatchBisecting(shas.slice(i, i + COMMIT_BATCH_SIZE))));
        }
        return results;
    }
    /**
     * ponytail: bisect on failure rather than tuning COMMIT_BATCH_SIZE harder.
     * The 502 this guards against is GitHub timing out on query cost, which
     * retrying an identical request can never clear — the request has to get
     * smaller. The constant is calibrated against today's repository; history
     * grows, and one commit tied to many pull requests costs more than its
     * neighbours, so treat the constant as the fast path and this as the ceiling.
     * Floors at a single commit, where a failure is real and must surface.
     */
    async mapCommitBatchBisecting(shas) {
        try {
            return await this.mapCommitBatch(shas);
        }
        catch (error) {
            // Only a retry-exhausted failure can plausibly be fixed by asking for
            // less. A malformed response fails the same at every size, so bisecting
            // it would replace one clear error with a storm of requests.
            if (!(error instanceof RetriesExhaustedError) || shas.length <= 1)
                throw error;
            const half = Math.ceil(shas.length / 2);
            return [
                ...(await this.mapCommitBatchBisecting(shas.slice(0, half))),
                ...(await this.mapCommitBatchBisecting(shas.slice(half))),
            ];
        }
    }
    /**
     * Classifies same-repo reference numbers in bulk. The REST classifier this
     * replaces costs one round trip per reference — 1738 of them for one minor,
     * a burst wide enough to trip GitHub's secondary rate limit, which fires on
     * concurrency rather than volume. `issueOrPullRequest` answers the same
     * question for 100 numbers in a single request.
     *
     * Cross-repo references are deliberately NOT handled here: the REST path
     * classifies those without an API call at all, so leaving them to it costs
     * nothing and avoids restating the same-repo rule in a second place.
     */
    async classifyRefs(numbers) {
        const out = new Map();
        for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
            const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
            const query = `query($owner: String!, $name: String!, ${batch.map((_, j) => `$n${j}: Int!`).join(', ')}) {
        repository(owner: $owner, name: $name) {
          ${batch
                .map((_, j) => `r${j}: issueOrPullRequest(number: $n${j}) { __typename ... on Issue { title } ... on PullRequest { title } }`)
                .join('\n')}
        }
      }`;
            const variables = { owner: this.owner, name: this.repo };
            batch.forEach((number, j) => (variables[`n${j}`] = number));
            const repository = await this.requestRepository(query, variables, true);
            batch.forEach((number, j) => {
                const node = repository[`r${j}`];
                // A number that resolves to neither is missing — deleted, transferred,
                // or never existed — exactly what a REST 404 means for the same number.
                if (!node) {
                    out.set(number, { target: 'missing', title: null });
                    return;
                }
                out.set(number, {
                    target: node.__typename === 'PullRequest' ? 'pullRequest' : 'issue',
                    title: node.title ?? null,
                });
            });
        }
        return out;
    }
    async fetchPrMetadata(numbers) {
        const results = [];
        for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
            results.push(...(await this.fetchMetadataBatch(numbers.slice(i, i + PR_METADATA_BATCH_SIZE))));
        }
        return results;
    }
    async mapCommitBatch(shas) {
        const query = `query($owner: String!, $name: String!, ${shas.map((_, i) => `$sha${i}: GitObjectID!`).join(', ')}) {
      repository(owner: $owner, name: $name) {
        ${shas.map((_, i) => `c${i}: object(oid: $sha${i}) { ... on Commit { ${prConnection()} } }`).join('\n')}
      }
    }`;
        const variables = { owner: this.owner, name: this.repo };
        shas.forEach((sha, i) => (variables[`sha${i}`] = sha));
        const repository = await this.requestRepository(query, variables);
        const mappings = [];
        for (const [i, sha] of shas.entries()) {
            const commit = assertField(repository[`c${i}`], `repository.c${i} (commit ${sha})`);
            mappings.push({ sha, associatedPrs: await this.drainAssociatedPrs(sha, commit) });
        }
        return mappings;
    }
    /**
     * Follows `pageInfo.hasNextPage` so a commit tied to many PRs is never
     * silently truncated at the first page.
     *
     * Filters to MERGED: the field has no `states` argument and returns every PR
     * whose branch history contains the commit — for a commit already on the base
     * branch that is every PR opened against it afterward, which makes nearly
     * every commit look ambiguous to the range resolver.
     */
    async drainAssociatedPrs(sha, firstPage) {
        let page = readPrPage(firstPage, sha);
        const all = [...page.nodes];
        while (page.pageInfo.hasNextPage) {
            const query = `query($owner: String!, $name: String!, $sha: GitObjectID!, $after: String) {
        repository(owner: $owner, name: $name) {
          c: object(oid: $sha) { ... on Commit { ${prConnection(', after: $after')} } }
        }
      }`;
            const repository = await this.requestRepository(query, { owner: this.owner, name: this.repo, sha, after: page.pageInfo.endCursor });
            const commit = assertField(repository.c, `repository.c (commit ${sha})`);
            page = readPrPage(commit, sha);
            all.push(...page.nodes);
        }
        return all
            .filter((node) => node.state === 'MERGED')
            .map((node) => ({
            number: node.number,
            baseRefName: node.baseRefName,
            headRefName: node.headRefName,
            mergeCommitOid: node.mergeCommit?.oid ?? null,
        }));
    }
    async fetchMetadataBatch(numbers) {
        const query = `query($owner: String!, $name: String!, ${numbers.map((_, i) => `$n${i}: Int!`).join(', ')}) {
      repository(owner: $owner, name: $name) {
        ${numbers
            .map((_, i) => `pr${i}: pullRequest(number: $n${i}) { number title body mergedAt baseRefName headRefName mergeCommit { oid } author { login __typename } labels(first: 20) { nodes { name } pageInfo { hasNextPage } } closingIssuesReferences(first: 20) { nodes { number } pageInfo { hasNextPage } } }`)
            .join('\n')}
      }
    }`;
        const variables = { owner: this.owner, name: this.repo };
        numbers.forEach((number, i) => (variables[`n${i}`] = number));
        const repository = await this.requestRepository(query, variables);
        return numbers.map((number, i) => {
            const pr = assertField(repository[`pr${i}`], `repository.pr${i} (PR #${number})`);
            const truncatedFields = [];
            if (pr.labels?.pageInfo?.hasNextPage)
                truncatedFields.push('labels');
            if (pr.closingIssuesReferences?.pageInfo?.hasNextPage)
                truncatedFields.push('closingIssuesReferences');
            return {
                number: assertField(pr.number, `number on PR #${number}`),
                title: assertField(pr.title, `title on PR #${number}`),
                baseRefName: assertField(pr.baseRefName, `baseRefName on PR #${number}`),
                headRefName: assertField(pr.headRefName, `headRefName on PR #${number}`),
                mergeCommitOid: pr.mergeCommit?.oid ?? null,
                body: pr.body ?? '',
                authorLogin: normalizeAuthorLogin(pr.author),
                mergedAt: assertField(pr.mergedAt, `mergedAt on PR #${number}`),
                labels: assertField(pr.labels?.nodes, `labels.nodes on PR #${number}`).map((label) => label.name),
                closingIssuesReferences: assertField(pr.closingIssuesReferences?.nodes, `closingIssuesReferences.nodes on PR #${number}`).map((issue) => issue.number),
                ...(truncatedFields.length > 0 ? { truncatedFields } : {}),
            };
        });
    }
    async requestRepository(query, variables, tolerateNotFound = false) {
        const data = await this.request(query, variables, tolerateNotFound);
        return assertField(data.repository, 'repository');
    }
    /** One GraphQL request, retrying a throttled or transiently failed one with
     *  backoff. Never logs the token, headers, or the raw response. */
    async request(query, variables, tolerateNotFound = false) {
        for (let attempt = 0;; attempt++) {
            // `fetch` rejects outright on a socket-level failure instead of
            // returning a Response, so every status check below is bypassed. Treated
            // as retry-exhausted rather than a plain Error so a batch that keeps
            // failing can still be bisected — an oversized query is one of the ways
            // a connection gets dropped.
            let res;
            try {
                res = await this.fetchImpl(GRAPHQL_URL, {
                    method: 'POST',
                    headers: (0, github_1.githubHeaders)(this.token, { json: true }),
                    body: JSON.stringify({ query, variables }),
                });
            }
            catch (error) {
                const detail = error instanceof Error ? error.message : String(error);
                await this.waitForRetry(null, attempt, `request never completed: ${detail}`);
                continue;
            }
            if (!res.ok) {
                if (!(await retryableStatus(res)))
                    throw new Error(`GitHub GraphQL API returned HTTP ${res.status}`);
                await this.waitForRetry(res, attempt, `HTTP ${res.status}`);
                continue;
            }
            // An overloaded GraphQL endpoint answers 200 with an empty or truncated
            // body as readily as it answers 502. That arrives here as a SyntaxError
            // from JSON.parse, which is exactly as transient as the status codes
            // above — and, left unguarded, escaped the retry loop and killed a run
            // three minutes in.
            let payload;
            try {
                payload = (await res.json());
            }
            catch {
                await this.waitForRetry(res, attempt, 'unparseable response body');
                continue;
            }
            if (payload.errors?.some((error) => error.type === exports.RATE_LIMITED_ERROR_TYPE)) {
                await this.waitForRetry(null, attempt, 'secondary rate limit');
                continue;
            }
            // A batch asking about many numbers will contain some that no longer
            // exist, and GitHub answers that with a NOT_FOUND error per alias while
            // still returning every alias that did resolve. Failing the whole batch
            // on one dead reference would make a single deleted issue fatal to the
            // release — 8.9.0's range carries 136 of them. Only the caller that
            // expects absences opts in; a missing commit SHA stays fatal.
            const fatal = tolerateNotFound
                ? (payload.errors ?? []).filter((error) => error.type !== NOT_FOUND_ERROR_TYPE)
                : (payload.errors ?? []);
            if (fatal.length) {
                throw new Error(`GitHub GraphQL error: ${fatal.map((error) => error.message).join('; ')}`);
            }
            return assertField(payload.data, 'data');
        }
    }
    /** Sleeps before the next attempt, or throws once the cap is reached — the
     *  one place that decides a retry loop is over. */
    async waitForRetry(res, attempt, cause) {
        if (attempt >= MAX_RETRIES - 1) {
            throw new RetriesExhaustedError(`GitHub GraphQL request kept failing (${cause}) past ${MAX_RETRIES} attempts.`);
        }
        await this.sleepImpl(backoffMs(res, attempt));
    }
}
exports.GithubGraphqlResolver = GithubGraphqlResolver;


/***/ }),

/***/ 579:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.buildPipelineResolver = buildPipelineResolver;
const resolver_1 = __nccwpck_require__(306);
/**
 * The per-pull-request phase's view of the API, served from classifications
 * learned in bulk beforehand so the phase itself makes almost no requests.
 * Anything not pre-warmed — a cross-repo ref, or one the bulk pass missed —
 * falls through to the REST resolver unchanged.
 */
function buildPipelineResolver(rest, warmRefs) {
    /** Only same-repo refs are pre-warmed: the REST path classifies a cross-repo
     *  one without an API call, so there is nothing to save and nothing to
     *  restate about what counts as same-repo. */
    const sameRepoNumber = (ref) => (ref.repo === null ? ref.number : null);
    return {
        async resolveRefs(refs) {
            // The SAME cap and priority the REST resolver applies — imported, not
            // restated, so the two can never disagree about which refs survive.
            const capped = (0, resolver_1.prioritizeAndCap)(refs);
            const cold = capped.filter((ref) => {
                const number = sameRepoNumber(ref);
                return number === null || !warmRefs.has(number);
            });
            const fresh = cold.length > 0 ? await rest.resolve(cold) : [];
            // Keyed by the ref's own position, not by its number: a body may cite the
            // same issue twice — #42118 cites #41769 at index 1 and again at 2680 —
            // and keying by number collapses the two, leaving one occurrence carrying
            // the other's index. Sorting by index then silently reorders the refs,
            // which changes which issue is "first" and so which issue the entry is
            // grouped and titled by.
            const freshByPosition = new Map(fresh.map((ref) => [ref.index, ref]));
            return capped
                .map((ref) => {
                const number = sameRepoNumber(ref);
                const warm = number === null ? undefined : warmRefs.get(number);
                if (warm)
                    return { ...ref, target: warm.target, crossRepo: false };
                // Never invent an answer: anything not pre-warmed came back from the
                // REST path above, and if even that has no verdict the ref is left to
                // the same 'missing' the resolver itself would report.
                return freshByPosition.get(ref.index) ?? { ...ref, target: 'missing', crossRepo: ref.repo !== null };
            })
                .sort((first, second) => first.index - second.index);
        },
        fetchOriginalPull: (number, repo) => rest.fetchOriginalPull(number, repo),
        fetchIssueTitle: async (number) => {
            const warm = warmRefs.get(number);
            // A pre-warmed classification already carries the title, so the separate
            // per-issue fetch this phase used to make is redundant for those.
            return warm ? warm.title : rest.fetchIssueTitle(number);
        },
    };
}


/***/ }),

/***/ 306:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubResolver = void 0;
exports.prioritizeAndCap = prioritizeAndCap;
const github_1 = __nccwpck_require__(631);
/** A PR body can carry at most this many refs to the API. A legitimate PR never
 *  needs more than a handful — this bounds the worst case (a body stuffed with
 *  hundreds of `#N` shorthands on `pull_request_target`) to a fixed cost. */
const MAX_REFS = 20;
/** How many classify calls run concurrently. Caps the fan-out against GitHub's
 *  API even after dedup + the cap above, so a burst of distinct numbers cannot
 *  open dozens of sockets at once. */
const CONCURRENCY = 5;
/** Lower sorts first. Closing/backport refs decide the gate's verdict, so they
 *  must survive the MAX_REFS cap ahead of merely-informational refs. */
function priorityOf(ref) {
    if (ref.kind === 'closing')
        return 0;
    if (ref.kind === 'backport')
        return 1;
    return 2;
}
/**
 * GitHub-API resolver: the only part of the pipeline that touches the network.
 * Classifies each ref as issue vs PR vs missing and flags cross-repo refs.
 *
 * GitHub's issues API returns PRs too (a PR is an issue with a `pull_request`
 * field), so one lookup per number classifies both. Cross-repo refs are not
 * queried — they never satisfy the gate, so their target stays "missing".
 *
 * ponytail: plain fetch (Node 24 global) over octokit — we hit exactly one
 * endpoint; octokit would inline the whole REST client into the bundle.
 * Throttled/transient responses are retried via fetchWithRetry (../github) —
 * the generator processes PRs serially, so one un-retried 5xx or secondary
 * rate limit anywhere in that chain would otherwise abort the whole job.
 */
/**
 * The refs a caller will actually classify: closing/backport refs sorted ahead
 * of merely-informational ones so that when the cap has to drop something, it
 * drops the least consequential first.
 *
 * Exported so a caller that pre-resolves in bulk applies the SAME policy. A
 * copied `MAX_REFS` would let the gate cap at one number and the generator at
 * another the moment either changed — the gate/generator divergence C4 exists
 * to prevent. One function, two callers, no constant to copy.
 */
function prioritizeAndCap(refs) {
    return [...refs].sort((first, second) => priorityOf(first) - priorityOf(second)).slice(0, MAX_REFS);
}
class GithubResolver {
    token;
    owner;
    repo;
    sleepImpl;
    repoUrl;
    headers;
    /** Titles seen while classifying refs, keyed by same-repo number (issues and
     *  PRs alike — `/issues/N` serves both). `classify` and `fetchIssueTitle` hit
     *  that same endpoint, and the generator asks for the title of a ref it has
     *  just classified, so the second call is served from here. */
    titlesByNumber = new Map();
    constructor(token, owner, repo, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.sleepImpl = sleepImpl;
        this.repoUrl = (0, github_1.repoApiUrl)(owner, repo);
        this.headers = (0, github_1.githubHeaders)(token);
    }
    /**
     * Resolve every ref, deduped (repeats of the same "#N" cost one API call),
     * capped at MAX_REFS (a legitimate PR never needs more), and bounded to
     * CONCURRENCY in flight — defense against a body engineered to fan out
     * unbounded concurrent requests through the gate's token.
     */
    async resolve(refs) {
        // Closing/backport refs decide the gate's verdict; bare/"relates to" refs
        // are informational. A stable sort keeps refs of equal priority in their
        // original order, so when the cap below has to drop something, it drops
        // the least consequential refs first instead of whichever came last in
        // the body.
        const capped = prioritizeAndCap(refs);
        const cache = new Map();
        const classifyCached = (ref) => {
            const key = `${ref.repo ?? ''}#${ref.number}`;
            let promise = cache.get(key);
            if (!promise) {
                promise = this.classify(ref);
                cache.set(key, promise);
            }
            return promise;
        };
        const results = [];
        for (let i = 0; i < capped.length; i += CONCURRENCY) {
            const batch = capped.slice(i, i + CONCURRENCY);
            const classified = await Promise.all(batch.map(classifyCached));
            batch.forEach((ref, index) => {
                const { target, crossRepo } = classified[index];
                results.push({ ...ref, target, crossRepo });
            });
        }
        // Restore body order for the policy's messages — the priority sort above
        // only controls what survives the cap, not how resolved refs get reported.
        return results.sort((first, second) => first.index - second.index);
    }
    /**
     * Fetch a same-repo pull request's body for backport-hop validation, or null
     * if it does not exist. Used to follow `Backport of #N` to the original PR and
     * validate that PR's attribution (the backport inherits it — C7).
     *
     * A cross-repo marker (`Backport of owner/other#N`) resolves to null: this
     * resolver is hardcoded to its own owner/repo, so #N there would name an
     * unrelated PR in THIS repo. We only inherit attribution from our own repo.
     */
    async fetchPullBody(number, repo) {
        if (this.isCrossRepo(repo))
            return null;
        const pull = await this.fetchPull(number);
        return pull?.body ?? null;
    }
    /**
     * Fetch a same-repo pull request's full fields for the generator's backport
     * hop (attribution + inherit-original title/mergedAt), or null if it does
     * not exist. A cross-repo marker (`Backport of owner/other#N`) resolves to
     * null for the same reason as {@link fetchPullBody}: #N there would name an
     * unrelated PR in THIS repo.
     */
    async fetchOriginalPull(number, repo) {
        if (this.isCrossRepo(repo))
            return null;
        return this.fetchPull(number);
    }
    /**
     * Fetch the fields the gate evaluates for one same-repo pull request, or null
     * if it does not exist.
     *
     * This is how the entrypoint obtains the PR under `workflow_run`, where the
     * event payload carries no `pull_request` object at all. Fetching also means
     * the body is read at evaluation time, so a stale or superseded trigger run
     * can never evaluate an out-of-date body.
     */
    async fetchPull(number) {
        const res = await (0, github_1.fetchJsonWithRetry)(`${this.repoUrl}/pulls/${number}`, { headers: this.headers }, this.sleepImpl);
        if (res.status === 404)
            return null;
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} fetching PR #${number}`);
        const { data } = res;
        return {
            body: data.body ?? '',
            title: data.title ?? '',
            authorLogin: data.user?.login,
            mergedAt: data.merged_at ?? undefined,
        };
    }
    /**
     * The live title of a same-repo issue, or null if it doesn't exist. Used by
     * the generator (#57713) to show the issue's own customer-facing wording
     * in release notes rather than the delivering PR's dev-facing title.
     */
    async fetchIssueTitle(number) {
        const cached = this.titlesByNumber.get(number);
        if (cached !== undefined)
            return cached;
        const res = await (0, github_1.fetchJsonWithRetry)(`${this.repoUrl}/issues/${number}`, { headers: this.headers }, this.sleepImpl);
        if (res.status === 404) {
            this.titlesByNumber.set(number, null);
            return null;
        }
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} fetching issue #${number}`);
        const title = res.data.title ?? null;
        this.titlesByNumber.set(number, title);
        return title;
    }
    /** A ref points at a different repo than the one being gated (case-insensitive). */
    isCrossRepo(repo) {
        return repo !== null && repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
    }
    /** Classify one (repo, number) pair — the part of a ref that actually needs
     *  an API call. Keyed independently of the ParsedRef's own fields (raw,
     *  keyword, kind, index) so `resolve()` can cache and reuse it across every
     *  ref that shares the same repo/number. */
    async classify(ref) {
        if (this.isCrossRepo(ref.repo))
            return { target: 'missing', crossRepo: true };
        const res = await (0, github_1.fetchJsonWithRetry)(`${this.repoUrl}/issues/${ref.number}`, { headers: this.headers }, this.sleepImpl);
        if (res.status === 404)
            return { target: 'missing', crossRepo: false };
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);
        this.titlesByNumber.set(ref.number, res.data.title ?? null);
        return { target: res.data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
    }
}
exports.GithubResolver = GithubResolver;


/***/ }),

/***/ 150:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.BOT_LINK_EXEMPT = exports.BOT_TITLE_EXEMPT = exports.HEADER_MAX = exports.TITLE_TYPES = void 0;
exports.lintTitle = lintTitle;
exports.isTitleExemptAuthor = isTitleExemptAuthor;
exports.isLinkExemptAuthor = isLinkExemptAuthor;
/**
 * PR-title lint — the active rules of `commitlint.config.cjs`, reimplemented as
 * a pure check so the action keeps zero runtime deps (pulling @commitlint +
 * config-conventional would vendor hundreds of kB into the committed bundle for
 * a handful of trivial rules). The config's other rules are disabled ([0,...]).
 *
 * DRIFT GUARD: TITLE_TYPES and HEADER_MAX are the single source of truth here,
 * and the action CI greps commitlint.config.cjs to assert they still match —
 * so a change to the repo's commit rules fails CI until this is updated.
 *
 * Active rules mirrored (see commitlint.config.cjs):
 *   type-empty:never · type-case:lower-case · type-enum · scope-empty:always ·
 *   header-max-length:120. Subject/body/footer rules are disabled there.
 */
/** commitlint.config.cjs `type-enum`. Keep in sync — CI enforces it. */
exports.TITLE_TYPES = [
    'build',
    'ci',
    'deps',
    'docs',
    'feat',
    'fix',
    'merge',
    'perf',
    'refactor',
    'revert',
    'style',
    'test',
];
/** commitlint.config.cjs `header-max-length`. Keep in sync — CI enforces it. */
exports.HEADER_MAX = 120;
// `type` + optional `(scope)` + optional `!` + `: ` + subject. Mirrors the
// conventional-commit header shape config-conventional parses.
const HEADER = /^(?<type>[^\s():!]+)(?<scope>\([^)]*\))?!?:[ ](?<subject>.+)$/;
/**
 * Wrap user-controlled title fragments before interpolating them into the
 * sticky comment / job summary. The gate posts the comment with a write token,
 * so a raw `@mention` in a malicious title would notify (spam) via the bot.
 * Inline code neutralises mentions; stripping backticks stops the value
 * breaking out of the span.
 */
function code(value) {
    return `\`${(value ?? '').replace(/`/g, '')}\``;
}
/** Lint a PR title. Pure — no IO, no bot logic (the caller decides bot skips). */
function lintTitle(title) {
    if (title.length > exports.HEADER_MAX) {
        return {
            outcome: 'fail',
            code: 'title-length',
            reasons: [`The title is ${title.length} characters; keep it within ${exports.HEADER_MAX}.`],
        };
    }
    const match = HEADER.exec(title);
    if (!match?.groups) {
        return {
            outcome: 'fail',
            code: 'title-format',
            reasons: [
                'The title must follow Conventional Commits: `type: summary` (e.g. "fix: correct retry backoff").',
                `Allowed types: ${exports.TITLE_TYPES.join(', ')}.`,
            ],
        };
    }
    const { type, scope } = match.groups;
    if (scope) {
        return {
            outcome: 'fail',
            code: 'title-scope',
            reasons: [`Scopes are not used in this repo — drop ${code(scope)} and write "${code(type)}: …".`],
        };
    }
    if (type !== type?.toLowerCase()) {
        return { outcome: 'fail', code: 'title-type', reasons: [`The type ${code(type)} must be lower-case.`] };
    }
    if (!exports.TITLE_TYPES.includes(type)) {
        return {
            outcome: 'fail',
            code: 'title-type',
            reasons: [`${code(type)} is not an allowed type. Use one of: ${exports.TITLE_TYPES.join(', ')}.`],
        };
    }
    return { outcome: 'pass', code: 'title-ok', reasons: [`Title type "${type}" is valid.`] };
}
/**
 * Bot authors whose titles are machine-generated and exempt from title lint
 * (D16). Their PR-issue link / backport marker is still validated — only the
 * title check is skipped.
 */
exports.BOT_TITLE_EXEMPT = new Set([
    'backport-action',
    'monorepo-devops-automation[bot]',
    'renovate[bot]',
    'dependabot[bot]',
]);
function isTitleExemptAuthor(login) {
    return login !== undefined && exports.BOT_TITLE_EXEMPT.has(login);
}
/**
 * Bot authors exempt from the PR-issue-LINK check, because they open PRs from
 * their own template and will never tick the opt-out checkbox. Dependency bumps
 * are not release-notes material, so an exemption is the agreed answer rather
 * than teaching each bot to write the section.
 *
 * DELIBERATELY SEPARATE from BOT_TITLE_EXEMPT, which must never be reused here:
 * that set contains `monorepo-devops-automation[bot]`, the author of every
 * backport PR. Exempting it from the link check would skip the backport hop, so
 * backports would stop inheriting the original PR's issue — silently dropping
 * them from the release notes, which is the failure this gate exists to prevent.
 */
exports.BOT_LINK_EXEMPT = new Set(['renovate[bot]']);
function isLinkExemptAuthor(login) {
    return login !== undefined && exports.BOT_LINK_EXEMPT.has(login);
}


/***/ }),

/***/ 421:
/***/ ((module) => {

module.exports = require("node:child_process");

/***/ }),

/***/ 24:
/***/ ((module) => {

module.exports = require("node:fs");

/***/ })

/******/ 	});
/************************************************************************/
/******/ 	// The module cache
/******/ 	var __webpack_module_cache__ = {};
/******/ 	
/******/ 	// The require function
/******/ 	function __nccwpck_require__(moduleId) {
/******/ 		// Check if module is in cache
/******/ 		var cachedModule = __webpack_module_cache__[moduleId];
/******/ 		if (cachedModule !== undefined) {
/******/ 			return cachedModule.exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = __webpack_module_cache__[moduleId] = {
/******/ 			// no module.id needed
/******/ 			// no module.loaded needed
/******/ 			exports: {}
/******/ 		};
/******/ 	
/******/ 		// Execute the module function
/******/ 		var threw = true;
/******/ 		try {
/******/ 			__webpack_modules__[moduleId].call(module.exports, module, module.exports, __nccwpck_require__);
/******/ 			threw = false;
/******/ 		} finally {
/******/ 			if(threw) delete __webpack_module_cache__[moduleId];
/******/ 		}
/******/ 	
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/ 	
/************************************************************************/
/******/ 	/* webpack/runtime/compat */
/******/ 	
/******/ 	if (typeof __nccwpck_require__ !== 'undefined') __nccwpck_require__.ab = __dirname + "/";
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	// This entry module is referenced by other modules so it can't be inlined
/******/ 	var __webpack_exports__ = __nccwpck_require__(516);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;