/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ 801:
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
const run_1 = __nccwpck_require__(913);
const reader_1 = __nccwpck_require__(473);
const resolve_1 = __nccwpck_require__(940);
/**
 * The `apply` entrypoint, dry-run only: compares the generator's plan with
 * what zcl labelled and writes the report to the job summary. Read-only
 * token; there is no code path here that writes to GitHub.
 */
async function run() {
    const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '').split('/');
    if (!owner || !repo)
        throw new Error(`GITHUB_REPOSITORY must be set to "owner/repo", got "${process.env.GITHUB_REPOSITORY ?? ''}".`);
    const token = core.getInput('token', { required: true });
    const inputDir = core.getInput('input-dir') || '.';
    const graphql = new resolve_1.GithubGraphqlResolver(token, owner, repo);
    const result = await (0, run_1.runApply)({
        inputDir,
        dryRun: core.getInput('dry-run') !== 'false',
        generateOutcome: core.getInput('generate-outcome'),
    }, {
        labelled: (label) => (0, reader_1.fetchLabelledNumbers)(token, owner, repo, label),
        issueFacts: (numbers) => graphql.fetchIssueFacts(numbers),
    });
    for (const warning of result.warnings)
        core.warning(warning);
    await core.summary.addRaw(result.markdown).write();
    if (result.divergence)
        (0, node_fs_1.writeFileSync)(`${inputDir}/apply-divergence.json`, JSON.stringify(result.divergence, null, 2));
}
run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));


/***/ }),

/***/ 596:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.DIVERGENCE_LIST_CAP = exports.SUPPORTED_SCHEMA_MAJOR = void 0;
exports.parseGeneratorFiles = parseGeneratorFiles;
exports.planApply = planApply;
exports.renderReport = renderReport;
exports.renderDegradedReport = renderDegradedReport;
const gha_1 = __nccwpck_require__(93);
/**
 * Dry-run `apply`: turns the generator's labels.json + comments.json and what
 * GitHub already shows into the list of writes a real apply would make, and a
 * report of how that list differs from what zcl did. Pure — no fetch, no
 * writes — so the same planner can later drive a real executor unchanged.
 */
exports.SUPPORTED_SCHEMA_MAJOR = '2';
/** Per divergence bucket; the rest go to apply-divergence.json. */
exports.DIVERGENCE_LIST_CAP = 20;
function parseGeneratorFiles(raw) {
    const labels = parseJson('labels.json', raw.labels);
    const comments = parseJson('comments.json', raw.comments);
    for (const [name, file] of [['labels.json', labels], ['comments.json', comments]]) {
        if (String(file.schemaVersion).split('.')[0] !== exports.SUPPORTED_SCHEMA_MAJOR) {
            throw new Error(`${name} has schemaVersion ${file.schemaVersion}, but this action supports ${exports.SUPPORTED_SCHEMA_MAJOR}.x only.`);
        }
    }
    if (labels.version !== comments.version) {
        throw new Error(`labels.json and comments.json are for different versions (${labels.version} and ${comments.version}).`);
    }
    const changelog = raw.changelog === undefined
        ? []
        : parseJson('changelog.json', raw.changelog).prs.map((pr) => ({
            number: pr.number,
            attributionSource: pr.attributionSource,
            deliveryPath: pr.deliveryPath,
        }));
    return { labels, comments, changelog };
}
function parseJson(name, text) {
    try {
        return JSON.parse(text);
    }
    catch {
        throw new Error(`${name} is not valid JSON.`);
    }
}
const sorted = (numbers) => [...numbers].sort((a, b) => a - b);
function planApply(files, observed) {
    const { labels, comments, changelog } = files;
    const generatorIssues = new Set(labels.issues);
    const generatorPullRequests = new Set(labels.pullRequests);
    const issues = {
        both: sorted([...generatorIssues].filter((number) => observed.zclIssues.has(number))),
        generatorOnly: sorted([...generatorIssues].filter((number) => !observed.zclIssues.has(number))),
        zclOnly: sorted([...observed.zclIssues].filter((number) => !generatorIssues.has(number))),
    };
    const bothPullRequests = [...generatorPullRequests].filter((number) => observed.zclPullRequests.has(number)).length;
    const pullRequests = {
        generator: generatorPullRequests.size,
        zcl: observed.zclPullRequests.size,
        both: bothPullRequests,
        generatorOnly: generatorPullRequests.size - bothPullRequests,
        zclOnly: observed.zclPullRequests.size - bothPullRequests,
    };
    const operations = [];
    for (const number of sorted(generatorIssues)) {
        operations.push(observed.zclIssues.has(number)
            ? { type: 'skip', what: 'label', number, reason: 'already labelled' }
            : { type: 'add-label', target: 'issue', number });
    }
    for (const number of sorted(generatorPullRequests)) {
        operations.push(observed.zclPullRequests.has(number)
            ? { type: 'skip', what: 'label', number, reason: 'already labelled' }
            : { type: 'add-label', target: 'pull_request', number });
    }
    for (const entry of comments.entries) {
        const skipReason = commentSkipReason(observed.issueFacts.get(entry.issueNumber));
        operations.push(skipReason
            ? { type: 'skip', what: 'comment', number: entry.issueNumber, reason: skipReason }
            : {
                type: 'create-comment',
                issueNumber: entry.issueNumber,
                relationKind: entry.relationKind,
                text: entry.text,
                marker: entry.marker,
            });
    }
    const entryByIssue = new Map(comments.entries.map((entry) => [entry.issueNumber, entry]));
    const prFacts = new Map(changelog.map((pr) => [pr.number, pr]));
    const hints = {};
    for (const number of [...issues.generatorOnly, ...issues.zclOnly]) {
        const entry = entryByIssue.get(number);
        hints[number] = [
            entry ? deliveringPrs(entry, prFacts) : "no PR in the generator's range is attributed to it",
            describeState(observed.issueFacts.get(number)),
        ].join(' · ');
    }
    return {
        version: labels.version,
        baselineEmpty: observed.zclIssues.size === 0 && observed.zclPullRequests.size === 0,
        issues,
        pullRequests,
        operations,
        hints,
    };
}
/** The legacy weekly script's rule: closed issues only, never abandoned ones. */
function commentSkipReason(state) {
    if (!state)
        return 'issue state unknown';
    if (!state.closed)
        return 'issue still open';
    if (state.stateReason === 'NOT_PLANNED')
        return 'closed as not planned';
    if (state.stateReason === 'DUPLICATE')
        return 'closed as duplicate';
    return undefined;
}
function deliveringPrs(entry, prFacts) {
    const first = prFacts.get(entry.prNumbers[0]);
    const prs = entry.prNumbers.map((number) => `#${number}`).join(', ');
    const detail = [entry.relationKind, ...(first ? [`attribution ${first.attributionSource}`, `delivery ${first.deliveryPath}`] : [])];
    return `via PR ${prs} (${detail.join('; ')})`;
}
function describeState(state) {
    if (!state)
        return 'issue state unknown';
    if (!state.closed)
        return 'issue open';
    return state.stateReason === 'NOT_PLANNED' ? 'closed as not planned' : state.stateReason === 'DUPLICATE' ? 'closed as duplicate' : 'closed';
}
const plural = (count, noun) => `${count} ${noun}${count === 1 ? '' : 's'}`;
/** Markdown for the job summary. Everything interpolated is escaped: the
 *  version and the comment text come from files, not from this code. */
function renderReport(plan) {
    const version = (0, gha_1.escapeHtml)(plan.version);
    const label = `\`version:${version}\``;
    const labelOps = plan.operations.filter((operation) => operation.type === 'add-label');
    const labelledIssues = labelOps.filter((operation) => operation.target === 'issue').length;
    const labelledPullRequests = labelOps.length - labelledIssues;
    const lines = [`### Release-notes apply (dry run): ${version}`, ''];
    if (plan.baselineEmpty) {
        lines.push(`⚠ No issues carry ${label}, so there is nothing to compare with (the zcl label step may have failed). ` +
            `Showing the generator's plan only: would label ${plural(labelledIssues, 'issue')} and ${labelledPullRequests} PRs.`, '');
    }
    else {
        lines.push(`Compared with the ${label} labels zcl applied. Nothing was written to GitHub.`, '', '| Issues | Count |', '|---|---|', `| Both agree | ${plan.issues.both.length} |`, `| Generator only (zcl missed) | ${plan.issues.generatorOnly.length} |`, `| zcl only (generator missed) | ${plan.issues.zclOnly.length} |`, '', `Pull requests: the generator plans ${plan.pullRequests.generator}, zcl labelled ${plan.pullRequests.zcl} ` +
            `(${plan.pullRequests.both} in both). Counts only, because zcl labels few PRs.`, '');
        const diverges = plan.issues.generatorOnly.length + plan.issues.zclOnly.length > 0;
        if (!diverges)
            lines.push('No divergence.', '');
        lines.push(...bucket('Generator only', plan.issues.generatorOnly, plan.hints));
        lines.push(...bucket('zcl only', plan.issues.zclOnly, plan.hints));
    }
    lines.push('#### Would do (nothing written)', '');
    lines.push(`- Label ${plural(labelledIssues, 'issue')} and ${plural(labelledPullRequests, 'pull request')} with ${label}` +
        ` (${countSkips(plan, 'label')} already carry it).`);
    lines.push(`- ${describeComments(plan)}`, '');
    const example = plan.operations.find((operation) => operation.type === 'create-comment');
    if (example) {
        lines.push('<details><summary>Show one comment as it would be posted</summary>', '', fence(`${example.text}\n${example.marker}`), '', '</details>', '');
    }
    return lines.join('\n');
}
function renderDegradedReport(reason) {
    return `### Release-notes apply (dry run)\n\n⚠ Skipped: ${(0, gha_1.escapeHtml)(reason)}\n`;
}
function bucket(title, numbers, hints) {
    if (numbers.length === 0)
        return [];
    const shown = numbers.slice(0, exports.DIVERGENCE_LIST_CAP);
    const lines = [`#### ${title} (${numbers.length})`, '', ...shown.map((number) => `- #${number} · ${(0, gha_1.escapeHtml)(hints[number] ?? '')}`)];
    if (numbers.length > shown.length) {
        lines.push(`- …${numbers.length - shown.length} more: see apply-divergence.json in the run artifacts`);
    }
    return [...lines, ''];
}
function countSkips(plan, what, reason) {
    return plan.operations.filter((operation) => operation.type === 'skip' && operation.what === what && (!reason || operation.reason === reason)).length;
}
function describeComments(plan) {
    const comments = plan.operations.filter((operation) => operation.type === 'create-comment');
    const closing = comments.filter((operation) => operation.relationKind === 'closing').length;
    const skipped = ['issue still open', 'closed as not planned', 'closed as duplicate', 'issue state unknown']
        .map((reason) => [countSkips(plan, 'comment', reason), reason])
        .filter(([count]) => count > 0)
        .map(([count, reason]) => `${count} ${reason.replace(/^issue /, '')}`);
    return (`Comment on ${plural(comments.length, 'issue')}: ${closing} "Released in" and ${comments.length - closing} "Partially delivered".` +
        (skipped.length > 0 ? ` Skipped: ${skipped.join(', ')}.` : ''));
}
/** A fence longer than any backtick run inside, so the content cannot close it. */
function fence(content) {
    const longest = Math.max(0, ...(content.match(/`+/g) ?? []).map((run) => run.length));
    const ticks = '`'.repeat(Math.max(3, longest + 1));
    return `${ticks}text\n${content}\n${ticks}`;
}


/***/ }),

/***/ 473:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.fetchLabelledNumbers = fetchLabelledNumbers;
const github_1 = __nccwpck_require__(631);
const PAGE_SIZE = 100;
/** Everything carrying `label`, closed or open, in one paginated listing
 *  rather than a call per issue. The issues endpoint returns PRs too; the
 *  `pull_request` key tells them apart. A failed page throws: an empty set
 *  from a permission error would read as "zcl labelled nothing". */
async function fetchLabelledNumbers(token, owner, repo, label, sleepImpl) {
    const issues = new Set();
    const pullRequests = new Set();
    const headers = (0, github_1.githubHeaders)(token);
    for (let page = 1;; page++) {
        const query = new URLSearchParams({ labels: label, state: 'all', per_page: String(PAGE_SIZE), page: String(page) });
        const result = await (0, github_1.fetchJsonWithRetry)(`${(0, github_1.repoApiUrl)(owner, repo)}/issues?${query}`, { headers }, sleepImpl);
        if (!result.ok)
            throw new Error(`GitHub API HTTP ${result.status} listing items labelled ${label}.`);
        for (const item of result.data)
            (item.pull_request ? pullRequests : issues).add(item.number);
        if (result.data.length < PAGE_SIZE)
            return { issues, pullRequests };
    }
}


/***/ }),

/***/ 913:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.runApply = runApply;
const node_fs_1 = __nccwpck_require__(24);
const node_path_1 = __nccwpck_require__(760);
const index_1 = __nccwpck_require__(596);
async function runApply(inputs, deps) {
    if (!inputs.dryRun)
        throw new Error('Real apply is not implemented yet; run with dry-run=true.');
    if (inputs.generateOutcome !== '' && inputs.generateOutcome !== 'success') {
        return skipped(`the generate step outcome was ${inputs.generateOutcome}, so there are no files to compare.`);
    }
    let files;
    try {
        files = (0, index_1.parseGeneratorFiles)({
            labels: readInput(inputs.inputDir, 'labels.json'),
            comments: readInput(inputs.inputDir, 'comments.json'),
            changelog: readOptionalInput(inputs.inputDir, 'changelog.json'),
        });
    }
    catch (error) {
        return skipped(error instanceof Error ? error.message : String(error));
    }
    const labelled = await deps.labelled(`version:${files.labels.version}`);
    // State is read for the generator's issues (comment rule) and for zcl-only
    // ones (hint), once each, in batches.
    const wanted = [...new Set([...files.labels.issues, ...labelled.issues])];
    const issueFacts = await deps.issueFacts(wanted);
    const plan = (0, index_1.planApply)(files, { zclIssues: labelled.issues, zclPullRequests: labelled.pullRequests, issueFacts });
    const warnings = plan.baselineEmpty ? [`No issues carry version:${plan.version}, so there was nothing to compare with.`] : [];
    return { markdown: (0, index_1.renderReport)(plan), divergence: plan, warnings };
}
function skipped(reason) {
    return { markdown: (0, index_1.renderDegradedReport)(reason), warnings: [`Apply dry run skipped: ${reason}`] };
}
function readInput(directory, name) {
    try {
        return (0, node_fs_1.readFileSync)((0, node_path_1.join)(directory, name), 'utf8');
    }
    catch {
        throw new Error(`${name} not found in ${directory}.`);
    }
}
/** changelog.json only adds facts to hints, so its absence is not a reason to skip. */
function readOptionalInput(directory, name) {
    try {
        return (0, node_fs_1.readFileSync)((0, node_path_1.join)(directory, name), 'utf8');
    }
    catch {
        return undefined;
    }
}


/***/ }),

/***/ 93:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.summary = exports.setFailed = exports.warning = exports.info = exports.setOutput = exports.getBooleanInput = exports.getInput = exports.escapeHtml = void 0;
const node_fs_1 = __nccwpck_require__(24);
const node_crypto_1 = __nccwpck_require__(598);
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
exports.escapeHtml = escapeHtml;
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
// GITHUB_OUTPUT file protocol with a heredoc delimiter. Random per call, like
// @actions/core, so a value that happens to contain the literal delimiter
// line (a contributor-authored PR title, passed straight into an output)
// can't truncate the value and inject arbitrary following output lines.
const setOutput = (name, value) => {
    const delimiter = `ghadelimiter_${(0, node_crypto_1.randomUUID)()}`;
    if (value.includes(delimiter))
        throw new Error(`Unexpected input: value matches delimiter "${delimiter}"`);
    appendEnvFile('GITHUB_OUTPUT', `${name}<<${delimiter}\n${value}\n${delimiter}\n`);
};
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
        this.buf += `<h${level}>${(0, exports.escapeHtml)(text)}</h${level}>\n`;
        return this;
    }
    addList(items) {
        this.buf += `<ul>${items.map((item) => `<li>${(0, exports.escapeHtml)(item)}</li>`).join('')}</ul>\n`;
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
 * comment, labels): one definition of auth/headers/retry, previously copied
 * into each. Stays octokit-free — a handful of endpoints, not a client.
 *
 * `retryableStatus`/`backoffMs`/`MAX_RETRIES` are also reused by resolve/index.ts
 * for its GraphQL transport — same throttle shapes, different transport, so the
 * classification logic is exported rather than duplicated there.
 */
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.MAX_RETRIES = exports.GITHUB_API = void 0;
exports.retryableStatus = retryableStatus;
exports.backoffMs = backoffMs;
exports.fetchWithRetry = fetchWithRetry;
exports.fetchJsonWithRetry = fetchJsonWithRetry;
exports.githubHeaders = githubHeaders;
exports.repoApiUrl = repoApiUrl;
exports.GITHUB_API = 'https://api.github.com';
const USER_AGENT = 'camunda-release-notes-gate';
const GITHUB_API_VERSION = '2022-11-28';
exports.MAX_RETRIES = 5;
const MAX_RETRY_AFTER_MS = 60_000; // beyond this the job should fail rather than hold a runner
/** 429, or 403 with a `retry-after` (a bare 403 is a real permission failure).
 *  5xx is transient. */
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
    const header = res?.headers.get('retry-after') ?? null;
    const seconds = header === null ? NaN : Number(header);
    if (Number.isFinite(seconds) && seconds >= 0)
        return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
    return 2 ** attempt * 1000;
}
/** `fetch` with backoff on a throttled or transient failure. Never retries a
 *  non-throttle failure (bare 403, 404) — the caller sees those immediately. */
async function fetchWithRetry(url, init, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
    for (let attempt = 0;; attempt++) {
        let res;
        try {
            res = await fetch(url, init);
        }
        catch (error) {
            // fetch REJECTS on a socket-level failure (reset, DNS blip) instead of
            // returning a Response, so this must be handled separately from status.
            if (attempt >= exports.MAX_RETRIES - 1) {
                const detail = error instanceof Error ? error.message : String(error);
                throw new Error(`GitHub API request never completed past ${exports.MAX_RETRIES} attempts (${url}): ${detail}`);
            }
            await sleepImpl(backoffMs(null, attempt));
            continue;
        }
        if (res.ok || !(await retryableStatus(res)))
            return res;
        if (attempt >= exports.MAX_RETRIES - 1) {
            throw new Error(`GitHub API kept returning HTTP ${res.status} past ${exports.MAX_RETRIES} attempts (${url}).`);
        }
        await sleepImpl(backoffMs(res, attempt));
    }
}
/** `fetchWithRetry` plus the body read — a truncated/empty body is retried
 *  like any other transient (GitHub answers that way under load too) instead
 *  of throwing a SyntaxError past the retry loop. */
async function fetchJsonWithRetry(url, init, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
    for (let attempt = 0;; attempt++) {
        const res = await fetchWithRetry(url, init, sleepImpl);
        if (!res.ok)
            return { ok: false, status: res.status };
        try {
            return { ok: true, status: res.status, data: (await res.json()) };
        }
        catch {
            if (attempt >= exports.MAX_RETRIES - 1) {
                throw new Error(`GitHub API returned an unparseable body past ${exports.MAX_RETRIES} attempts (${url}).`);
            }
            await sleepImpl(backoffMs(null, attempt));
        }
    }
}
/** Auth + content-negotiation headers for the plain `GITHUB_TOKEN` every
 *  caller passes in — never a privileged token (this action resolves from
 *  the PR head on `pull_request`). Pass `json: true` for a JSON body. */
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
 * Pure, section-scoped reference parser, shared verbatim with the generator
 * — no IO, no repo awareness. Cross-repo detection and issue-vs-PR
 * classification belong to the Resolver, not here.
 */
/** The template's opt-out phrase. Kept as an exported constant so the PR template
 *  and the parser cannot drift (enforced by the repo-constant grep in CI). */
exports.OPT_OUT_PHRASE = 'this pr does not need a linked issue';
/** The section whose refs the gate evaluates. */
exports.SECTION_HEADING = 'Related issues';
// GitHub's closing keywords + our custom "completes". Case-insensitive.
const CLOSING = /^(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|completes?)$/i;
const RELATES = /^relate[sd]?\s+to$/i;
const BACKPORT = /^backport\s+of$/i;
// Optional keyword prefix shared by both ref shapes.
const KW = String.raw `(?:\b(close[sd]?|fix(?:e[sd])?|resolve[sd]?|completes?|relate[sd]?\s+to|backport\s+of)\b[\s:]+)?`;
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
/** Strips before any parsing — the PR template's own instructional
 *  `<!-- closes #1234 -->` block is invisible in the rendered body, and a
 *  PR that leaves the boilerplate untouched must not be attributed to it. */
function stripHtmlComments(text) {
    return text.replace(/<!--[\s\S]*?-->/g, '');
}
/** A reviewer citing an example (`` `closes #1234` `` in prose, or a fenced
 *  snippet) must not be mistaken for the author's own ref or opt-out tick. */
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
/** Everything after the matching heading up to the next heading (or EOF), or null if absent. */
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

/***/ 940:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubGraphqlResolver = exports.RetriesExhaustedError = exports.RATE_LIMITED_ERROR_TYPE = void 0;
const github_1 = __nccwpck_require__(631);
const warm_1 = __nccwpck_require__(579);
const GRAPHQL_URL = 'https://api.github.com/graphql';
/** `associatedPullRequests` walks branch history per commit; `pullRequest(number:)`
 *  is a direct lookup — an order of magnitude cheaper, so the two can't share
 *  a batch size. 100 commit aliases 502s at ~11s server-side timeout; 25 is
 *  the size that doesn't. */
const COMMIT_BATCH_SIZE = 25;
/** A direct lookup — 100 aliases return in under a second. */
const PR_METADATA_BATCH_SIZE = 100;
exports.RATE_LIMITED_ERROR_TYPE = 'RATE_LIMITED';
/** A field-level error, not a null field — the rest of the batch still comes back. */
const NOT_FOUND_ERROR_TYPE = 'NOT_FOUND';
/** Thrown when every retry failed for a reason a SMALLER request might
 *  survive. A malformed response fails identically at any size — bisecting
 *  that would replace one clear error with a storm of requests. */
class RetriesExhaustedError extends Error {
}
exports.RetriesExhaustedError = RetriesExhaustedError;
/** The one `associatedPullRequests` selection both query shapes share.
 *  `headRefName`/`mergeCommit` feed range membership, decided before any PR
 *  metadata fetch, and are free on a connection already selected. */
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
/** GraphQL's `author.login` omits the `[bot]` suffix REST always includes;
 *  every bot-identity set in this package is keyed on the REST convention. */
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
    /** The one "batch N values into a multi-alias `repository()` query" shape
     *  `mapCommitBatch`, `classifyRefs`, `fetchIssueFacts`, and `fetchMetadataBatch`
     *  each built separately — only the variable type, alias prefix, and
     *  per-item field selection actually differ between them. */
    buildBatchQuery(items, opts) {
        const varDecls = items.map((_, i) => `$v${i}: ${opts.varType}`).join(', ');
        const body = items.map((_, i) => `${opts.aliasPrefix}${i}: ${opts.field(`$v${i}`)}`).join('\n');
        const query = `query($owner: String!, $name: String!, ${varDecls}) {
      repository(owner: $owner, name: $name) {
        ${body}
      }
    }`;
        const variables = { owner: this.owner, name: this.repo };
        items.forEach((item, i) => (variables[`v${i}`] = item));
        return { query, variables };
    }
    async mapCommitsToPrs(shas) {
        const results = [];
        for (let i = 0; i < shas.length; i += COMMIT_BATCH_SIZE) {
            results.push(...(await this.mapCommitBatchBisecting(shas.slice(i, i + COMMIT_BATCH_SIZE))));
        }
        return results;
    }
    /** ponytail: bisect on failure rather than tuning COMMIT_BATCH_SIZE harder
     *  — COMMIT_BATCH_SIZE is the fast path, this is the ceiling. Floors at one
     *  commit, where a failure is real. */
    async mapCommitBatchBisecting(shas) {
        try {
            return await this.mapCommitBatch(shas);
        }
        catch (error) {
            // Only retry-exhausted can plausibly be fixed by asking for less.
            if (!(error instanceof RetriesExhaustedError) || shas.length <= 1)
                throw error;
            const half = Math.ceil(shas.length / 2);
            return [
                ...(await this.mapCommitBatchBisecting(shas.slice(0, half))),
                ...(await this.mapCommitBatchBisecting(shas.slice(half))),
            ];
        }
    }
    /** Same-repo numbers in bulk via `issueOrPullRequest` — the REST classifier
     *  this replaces costs one round trip per reference, enough to trip the
     *  secondary rate limit on a large minor. Cross-repo refs stay with REST,
     *  which classifies those without an API call at all. */
    async classifyRefs(numbers) {
        const out = new Map();
        for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
            const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
            const { query, variables } = this.buildBatchQuery(batch, {
                varType: 'Int!',
                aliasPrefix: 'r',
                field: (ref) => `issueOrPullRequest(number: ${ref}) { __typename ... on Issue { title } ... on PullRequest { title } }`,
            });
            const repository = await this.requestRepository(query, variables, true);
            batch.forEach((number, j) => {
                const node = repository[`r${j}`];
                if (!node) { // missing: deleted, transferred, or never existed — a REST 404 for the same number
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
    /** What only the ISSUE knows: labels, and its own close event. Same
     *  batching/NOT_FOUND tolerance as `classifyRefs`. `timelineItems(last: 1)`
     *  is the LAST close — the one that matters for reopened-then-reclosed. */
    async fetchIssueFacts(numbers) {
        const out = new Map();
        for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
            const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
            const { query, variables } = this.buildBatchQuery(batch, {
                varType: 'Int!',
                aliasPrefix: 'i',
                field: (ref) => `issue(number: ${ref}) { closed stateReason labels(first: 20) { nodes { name } pageInfo { hasNextPage } } timelineItems(last: 1, itemTypes: CLOSED_EVENT) { nodes { ... on ClosedEvent { closer { __typename ... on PullRequest { number repository { nameWithOwner } } } } } } }`,
            });
            const repository = await this.requestRepository(query, variables, true);
            batch.forEach((number, j) => {
                const node = repository[`i${j}`];
                if (!node)
                    return;
                const closer = node.timelineItems?.nodes?.[0]?.closer;
                // A PR in another repo can close an issue here (camunda/camunda-docs#4852
                // closes camunda/camunda#26937) — its number means nothing in our numbering.
                const sameRepo = closer?.repository?.nameWithOwner === `${this.owner}/${this.repo}`;
                out.set(number, {
                    closed: node.closed ?? false,
                    stateReason: node.stateReason ?? null,
                    closerPrNumber: closer?.__typename === 'PullRequest' && sameRepo ? (closer.number ?? null) : null,
                    labels: (node.labels?.nodes ?? []).map((label) => label.name),
                    labelsTruncated: node.labels?.pageInfo?.hasNextPage ?? false,
                });
            });
        }
        return out;
    }
    async fetchPrMetadata(numbers, speculative = false) {
        const results = [];
        for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
            results.push(...(await this.fetchMetadataBatch(numbers.slice(i, i + PR_METADATA_BATCH_SIZE), speculative)));
        }
        return results;
    }
    async mapCommitBatch(shas) {
        const { query, variables } = this.buildBatchQuery(shas, {
            varType: 'GitObjectID!',
            aliasPrefix: 'c',
            field: (ref) => `object(oid: ${ref}) { ... on Commit { ${prConnection()} } }`,
        });
        const repository = await this.requestRepository(query, variables);
        const mappings = [];
        for (const [i, sha] of shas.entries()) {
            const commit = assertField(repository[`c${i}`], `repository.c${i} (commit ${sha})`);
            mappings.push({ sha, associatedPrs: await this.drainAssociatedPrs(sha, commit) });
        }
        return mappings;
    }
    /** Follows `pageInfo.hasNextPage` so a many-PR commit is never truncated.
     *  Filters to MERGED: the field has no `states` arg and otherwise returns
     *  every PR whose branch history contains the commit — every PR opened
     *  against the base branch afterward, for a commit already on it. */
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
    /** `speculative`: a number scraped from a merge subject is only a guess,
     *  and `pullRequest(number:)` NOT_FOUNDs for it — absent here (rather than
     *  a thrown error) is what sends the commit to the `associatedPullRequests`
     *  fallback instead of aborting the release. */
    async fetchMetadataBatch(numbers, speculative = false) {
        const { query, variables } = this.buildBatchQuery(numbers, {
            varType: 'Int!',
            aliasPrefix: 'pr',
            field: (ref) => `pullRequest(number: ${ref}) { number title body mergedAt baseRefName headRefName mergeCommit { oid } author { login __typename } labels(first: 20) { nodes { name } pageInfo { hasNextPage } } closingIssuesReferences(first: 20) { nodes { number repository { nameWithOwner } } pageInfo { hasNextPage } } }`,
        });
        const repository = await this.requestRepository(query, variables, speculative);
        return numbers.flatMap((number, i) => {
            const node = repository[`pr${i}`];
            if (speculative && (node === null || node === undefined))
                return [];
            const pr = assertField(node, `repository.pr${i} (PR #${number})`);
            // An OPEN pull request is a non-null node but an unconfirmed guess all
            // the same — as absent as NOT_FOUND above, not a reason to abort.
            if (speculative && pr.mergedAt == null)
                return [];
            const truncatedFields = [];
            if (pr.labels?.pageInfo?.hasNextPage)
                truncatedFields.push('labels');
            if (pr.closingIssuesReferences?.pageInfo?.hasNextPage)
                truncatedFields.push('closingIssuesReferences');
            return [{
                    number: assertField(pr.number, `number on PR #${number}`),
                    title: assertField(pr.title, `title on PR #${number}`),
                    baseRefName: assertField(pr.baseRefName, `baseRefName on PR #${number}`),
                    headRefName: assertField(pr.headRefName, `headRefName on PR #${number}`),
                    mergeCommitOid: pr.mergeCommit?.oid ?? null,
                    body: pr.body ?? '',
                    authorLogin: normalizeAuthorLogin(pr.author),
                    mergedAt: assertField(pr.mergedAt, `mergedAt on PR #${number}`),
                    labels: assertField(pr.labels?.nodes, `labels.nodes on PR #${number}`).map((label) => label.name),
                    closingIssuesReferences: assertField(pr.closingIssuesReferences?.nodes, `closingIssuesReferences.nodes on PR #${number}`)
                        .filter((issue) => (0, warm_1.isOwnRepo)(assertField(issue.repository?.nameWithOwner, `repository.nameWithOwner on closing issue #${issue.number} of PR #${number}`), `${this.owner}/${this.repo}`))
                        .map((issue) => issue.number),
                    ...(truncatedFields.length > 0 ? { truncatedFields } : {}),
                }];
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
            // fetch rejects on a socket-level failure instead of returning a
            // Response — routed through waitForRetry so it stays bisectable.
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
                if (!(await (0, github_1.retryableStatus)(res)))
                    throw new Error(`GitHub GraphQL API returned HTTP ${res.status}`);
                await this.waitForRetry(res, attempt, `HTTP ${res.status}`);
                continue;
            }
            // A 200 with an empty/truncated body throws SyntaxError from JSON.parse
            // — as transient as the status codes above, so retried the same way.
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
            // A batch containing deleted numbers gets a NOT_FOUND per alias plus every
            // alias that DID resolve — only the caller expecting absences opts in;
            // a missing commit SHA stays fatal.
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
        if (attempt >= github_1.MAX_RETRIES - 1) {
            throw new RetriesExhaustedError(`GitHub GraphQL request kept failing (${cause}) past ${github_1.MAX_RETRIES} attempts.`);
        }
        await this.sleepImpl((0, github_1.backoffMs)(res, attempt));
    }
}
exports.GithubGraphqlResolver = GithubGraphqlResolver;


/***/ }),

/***/ 579:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.isOwnRepo = isOwnRepo;
exports.prewarmNumbers = prewarmNumbers;
exports.backportTargets = backportTargets;
exports.buildPipelineResolver = buildPipelineResolver;
const parser_1 = __nccwpck_require__(883);
const resolver_1 = __nccwpck_require__(306);
/** A full-URL ref to this repo parses with `repo` set, so `null` alone misses it. */
function isOwnRepo(repo, ownRepo) {
    return repo === null || repo.toLowerCase() === ownRepo.toLowerCase();
}
/** Every own-repo number the pipeline might resolve for these bodies — section
 *  refs plus full-body refs, each capped by the policy the resolver applies. */
function prewarmNumbers(bodies, ownRepo) {
    const wanted = new Set();
    for (const body of bodies) {
        const section = (0, parser_1.extractSection)(body);
        for (const refs of [section ? (0, parser_1.parseRefs)(section) : [], (0, parser_1.parseRefs)(body)]) {
            for (const ref of (0, resolver_1.prioritizeAndCap)(refs)) {
                if (isOwnRepo(ref.repo, ownRepo))
                    wanted.add(ref.number);
            }
        }
    }
    return wanted;
}
/** The own-repo PRs these bodies are backports of, deduped — the same marker
 *  the pipeline's hop follows. A cross-repo marker never resolves here. */
function backportTargets(bodies, ownRepo) {
    const targets = new Set();
    for (const body of bodies) {
        const marker = (0, parser_1.parseRefs)(body).find((ref) => ref.kind === 'backport');
        if (marker && isOwnRepo(marker.repo, ownRepo))
            targets.add(marker.number);
    }
    return [...targets];
}
/** `originals` holds the backport originals fetched in bulk; anything absent
 *  (not prefetched, or not a merged PR) still goes to REST. */
function buildPipelineResolver(rest, warmRefs, ownRepo, originals = new Map()) {
    // Cross-repo refs are never pre-warmed — the REST path classifies those without an API call.
    const sameRepoNumber = (ref) => (isOwnRepo(ref.repo, ownRepo) ? ref.number : null);
    return {
        async resolveRefs(refs) {
            const capped = (0, resolver_1.prioritizeAndCap)(refs); // same cap/priority as the REST resolver — imported, not restated
            const cold = capped.filter((ref) => {
                const number = sameRepoNumber(ref);
                return number === null || !warmRefs.has(number);
            });
            const fresh = cold.length > 0 ? await rest.resolve(cold) : [];
            // Keyed by position, not number: a body can cite the same issue twice,
            // and keying by number would collapse the two occurrences into one.
            const freshByPosition = new Map(fresh.map((ref) => [ref.index, ref]));
            return capped
                .map((ref) => {
                const number = sameRepoNumber(ref);
                const warm = number === null ? undefined : warmRefs.get(number);
                if (warm)
                    return { ...ref, target: warm.target, crossRepo: false };
                return freshByPosition.get(ref.index) ?? { ...ref, target: 'missing', crossRepo: !isOwnRepo(ref.repo, ownRepo) };
            })
                .sort((first, second) => first.index - second.index);
        },
        fetchOriginalPull: async (number, repo) => (isOwnRepo(repo, ownRepo) ? originals.get(number) : undefined) ?? rest.fetchOriginalPull(number, repo),
        fetchIssueTitle: async (number) => warmRefs.get(number)?.title ?? rest.fetchIssueTitle(number),
    };
}


/***/ }),

/***/ 306:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubResolver = void 0;
exports.prioritizeAndCap = prioritizeAndCap;
const github_1 = __nccwpck_require__(631);
/** Bounds the worst case — a body stuffed with hundreds of `#N` shorthands
 *  on `pull_request_target` — to a fixed cost. */
const MAX_REFS = 20;
/** Caps fan-out even after dedup + the cap above. */
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
 * The refs a caller will actually classify, capped and priority-sorted so a
 * dropped ref is always the least consequential one. Exported so the gate
 * and the generator apply the SAME cap — a copied `MAX_REFS` would let them
 * drift apart the moment either changed.
 */
function prioritizeAndCap(refs) {
    return [...refs].sort((first, second) => priorityOf(first) - priorityOf(second)).slice(0, MAX_REFS);
}
/**
 * GitHub-API resolver: the only part of the pipeline that touches the network.
 * Classifies each ref as issue vs PR vs missing and flags cross-repo refs —
 * GitHub's issues API returns PRs too (a PR is an issue with a `pull_request`
 * field), so one lookup per number classifies both.
 *
 * ponytail: plain fetch (Node 24 global) over octokit for this one endpoint.
 * Transient responses retry via `fetchWithRetry` (../github) since PRs are
 * processed serially — one un-retried 5xx would abort the whole job.
 */
class GithubResolver {
    token;
    owner;
    repo;
    sleepImpl;
    repoUrl;
    headers;
    /** `classify` and `fetchIssueTitle` hit the same `/issues/N` endpoint, so a
     *  title seen while classifying serves the later fetchIssueTitle call. */
    titlesByNumber = new Map();
    constructor(token, owner, repo, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.sleepImpl = sleepImpl;
        this.repoUrl = (0, github_1.repoApiUrl)(owner, repo);
        this.headers = (0, github_1.githubHeaders)(token);
    }
    /** Resolve every ref: deduped, capped at MAX_REFS, bounded to CONCURRENCY
     *  in flight — defense against a body engineered to fan out unbounded
     *  concurrent requests through the gate's token. */
    async resolve(refs) {
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
        return results.sort((first, second) => first.index - second.index); // body order for messages — priority sort only controlled the cap
    }
    /** A same-repo pull request's body, for backport-hop validation, or null if
     *  it doesn't exist. Cross-repo (`Backport of owner/other#N`) resolves to
     *  null: #N there would name an unrelated PR in THIS repo. */
    async fetchPullBody(number, repo) {
        if (this.isCrossRepo(repo))
            return null;
        const pull = await this.fetchPull(number);
        return pull?.body ?? null;
    }
    /** Same as {@link fetchPullBody} but the full fields, for the generator's
     *  backport hop (attribution + inherit-original title/mergedAt). */
    async fetchOriginalPull(number, repo) {
        if (this.isCrossRepo(repo))
            return null;
        return this.fetchPull(number);
    }
    /** The fields the gate evaluates for one same-repo pull request, or null if
     *  it doesn't exist. Fetched fresh rather than trusted from the webhook
     *  payload, since `workflow_run` carries no `pull_request` object at all
     *  and a stale trigger run must not evaluate an out-of-date body. */
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
    /** The live title of a same-repo issue, or null if it doesn't exist — the
     *  generator shows this customer-facing wording, not the PR's dev title. */
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

/***/ 598:
/***/ ((module) => {

module.exports = require("node:crypto");

/***/ }),

/***/ 24:
/***/ ((module) => {

module.exports = require("node:fs");

/***/ }),

/***/ 760:
/***/ ((module) => {

module.exports = require("node:path");

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
/******/ 	/* webpack/runtime/asset-relocator-loader */
/******/ 	if (typeof __nccwpck_require__ !== 'undefined') __nccwpck_require__.ab = __dirname + "/";
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	// This entry module is referenced by other modules so it can't be inlined
/******/ 	var __webpack_exports__ = __nccwpck_require__(801);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;