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
const index_1 = __nccwpck_require__(596);
const run_1 = __nccwpck_require__(913);
const reader_1 = __nccwpck_require__(473);
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
    const result = await (0, run_1.runApply)({
        inputDir,
        dryRun: (0, index_1.parseDryRun)(core.getInput('dry-run')),
        generateOutcome: core.getInput('generate-outcome'),
    }, {
        labelled: (label) => (0, reader_1.fetchLabelledNumbers)(token, owner, repo, label),
        issueStates: (numbers) => (0, reader_1.fetchIssueStates)(token, owner, repo, numbers),
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
exports.parseDryRun = parseDryRun;
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
    for (const [name, file] of [['labels.json', labels], ['comments.json', comments]]) {
        if (typeof file.version !== 'string' || !VERSION_SHAPE.test(file.version)) {
            throw new Error(`${name} has no usable "version" (letters, digits, dots, dashes, plus signs and underscores only).`);
        }
    }
    if (labels.version !== comments.version) {
        throw new Error(`labels.json and comments.json are for different versions (${labels.version} and ${comments.version}).`);
    }
    if (!isNumbers(labels.issues) || !isNumbers(labels.pullRequests)) {
        throw new Error('labels.json must hold an "issues" and a "pullRequests" array of positive whole numbers.');
    }
    if (!Array.isArray(comments.entries) || !comments.entries.every(isCommentEntry)) {
        throw new Error('comments.json must hold an "entries" array of { issueNumber, prNumbers, relationKind, text, marker }.');
    }
    return { labels, comments, changelog: parseChangelogHints(raw.changelog) };
}
const VERSION_SHAPE = /^[0-9A-Za-z][0-9A-Za-z._+-]*$/;
const isNumber = (value) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const isNumbers = (value) => Array.isArray(value) && value.every(isNumber);
function isCommentEntry(entry) {
    const candidate = entry;
    return (isNumber(candidate?.issueNumber) &&
        isNumbers(candidate?.prNumbers) &&
        (candidate?.relationKind === 'closing' || candidate?.relationKind === 'contributor') &&
        typeof candidate.text === 'string' &&
        typeof candidate.marker === 'string' &&
        candidate.marker !== '');
}
/** changelog.json only adds facts to hints, so one that is missing, unreadable
 *  or the wrong shape costs the hints, not the comparison. */
function parseChangelogHints(text) {
    if (text === undefined)
        return [];
    try {
        const prs = JSON.parse(text).prs;
        if (!Array.isArray(prs))
            return [];
        return prs.map((pr) => ({ number: pr.number, attributionSource: pr.attributionSource, deliveryPath: pr.deliveryPath }));
    }
    catch {
        return [];
    }
}
/** `dry-run` is the one switch between rehearsing and writing, so anything but
 *  an exact "true" or "false" is an error rather than a guess. */
function parseDryRun(raw) {
    const value = raw.trim();
    if (value === '' || value === 'true')
        return true;
    if (value === 'false')
        return false;
    throw new Error(`dry-run must be "true" or "false", got "${raw}".`);
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
    // The marker is keyed on the issue, so a repeated issue publishes only its last entry.
    const entryByIssue = new Map(comments.entries.map((entry) => [entry.issueNumber, entry]));
    const legacyText = `This has been released in version [${labels.version}]`;
    const hasComment = (number, needle) => observed.issueFacts.get(number)?.comments.some((body) => body.includes(needle)) ?? false;
    const eligible = new Set();
    for (const entry of entryByIssue.values()) {
        const skipReason = commentSkipReason(observed.issueFacts.get(entry.issueNumber));
        if (!skipReason)
            eligible.add(entry.issueNumber);
        const alreadyCommented = !eligible.has(entry.issueNumber)
            ? skipReason
            : hasComment(entry.issueNumber, legacyText)
                ? 'already commented by the legacy script'
                : hasComment(entry.issueNumber, entry.marker)
                    ? 'already commented by the generator'
                    : undefined;
        operations.push(alreadyCommented
            ? { type: 'skip', what: 'comment', number: entry.issueNumber, reason: alreadyCommented }
            : {
                type: 'create-comment',
                issueNumber: entry.issueNumber,
                relationKind: entry.relationKind,
                text: entry.text,
                marker: entry.marker,
            });
    }
    const legacyCommented = sorted([...observed.issueFacts.keys()].filter((number) => hasComment(number, legacyText)));
    const commentComparison = {
        both: legacyCommented.filter((number) => eligible.has(number)),
        generatorOnly: sorted([...eligible].filter((number) => !hasComment(number, legacyText))),
        legacyOnly: legacyCommented.filter((number) => !eligible.has(number)),
    };
    const prFacts = new Map(changelog.map((pr) => [pr.number, pr]));
    const hints = {};
    const hintFor = (number, withoutEntry) => [entryByIssue.has(number) ? deliveringPrs(entryByIssue.get(number), prFacts) : withoutEntry, describeState(observed.issueFacts.get(number))].join(' · ');
    for (const number of issues.generatorOnly)
        hints[number] = hintFor(number, 'the generator has no comment entry for it');
    for (const number of issues.zclOnly)
        hints[number] = hintFor(number, "the generator's labels.json does not list it");
    for (const number of commentComparison.legacyOnly)
        hints[number] ??= hintFor(number, 'the generator plans no comment for it');
    return {
        version: labels.version,
        baselineEmpty: observed.zclIssues.size === 0,
        issues,
        pullRequests,
        operations,
        comments: commentComparison,
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
/** Every delivering PR with its own attribution and delivery path, so no fact is
 *  quoted for a PR it does not belong to. */
function deliveringPrs(entry, prFacts) {
    const prs = entry.prNumbers.map((number) => {
        const facts = prFacts.get(number);
        return facts ? `#${number} (attribution ${facts.attributionSource}; delivery ${facts.deliveryPath})` : `#${number}`;
    });
    return `${entry.relationKind}, via PR ${prs.join(', ')}`;
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
    const label = inlineCode(`version:${plan.version}`);
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
            lines.push('No issue divergence.', '');
        lines.push(...bucket('Generator only', plan.issues.generatorOnly, plan.hints));
        lines.push(...bucket('zcl only', plan.issues.zclOnly, plan.hints));
    }
    lines.push(...renderCommentComparison(plan));
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
function renderCommentComparison(plan) {
    const { both, generatorOnly, legacyOnly } = plan.comments;
    const lines = [
        '| Comments (issues whose state was read) | Count |',
        '|---|---|',
        `| Legacy script commented, generator agrees | ${both.length} |`,
        `| Generator would comment, legacy has not | ${generatorOnly.length} |`,
        `| Legacy commented, generator would not | ${legacyOnly.length} |`,
        '',
    ];
    const unreadable = countSkips(plan, 'comment', 'issue state unknown');
    if (unreadable > 0) {
        lines.push(`⚠ The state of ${plural(unreadable, 'issue')} could not be read, so this comparison is incomplete.`, '');
    }
    else if (both.length + legacyOnly.length === 0) {
        lines.push('The legacy script runs weekly, so at release time it has usually not commented yet; compare again after its next run.', '');
    }
    return [...lines, ...bucket('Legacy commented, generator would not', legacyOnly, plan.hints)];
}
/** A code span shows its content literally, so it is neither HTML-escaped nor
 *  allowed to hold a backtick run that would close it. */
function inlineCode(text) {
    const longest = Math.max(0, ...(text.match(/`+/g) ?? []).map((run) => run.length));
    const ticks = '`'.repeat(longest + 1);
    const pad = text.startsWith('`') || text.endsWith('`') ? ' ' : '';
    return `${ticks}${pad}${text}${pad}${ticks}`;
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
const COMMENT_SKIP_REASONS = [
    'issue still open',
    'closed as not planned',
    'closed as duplicate',
    'issue state unknown',
    'already commented by the legacy script',
    'already commented by the generator',
];
function describeComments(plan) {
    const comments = plan.operations.filter((operation) => operation.type === 'create-comment');
    const closing = comments.filter((operation) => operation.relationKind === 'closing').length;
    const skipped = COMMENT_SKIP_REASONS.map((reason) => [countSkips(plan, 'comment', reason), reason])
        .filter(([count]) => count > 0)
        .map(([count, reason]) => `${count} ${reason.replace(/^issue /, '')}`);
    return (`Comment on ${plural(comments.length, 'issue')}: ${closing} "Released in" and ${comments.length - closing} "Partially delivered".` +
        (skipped.length > 0 ? ` Skipped issues: ${skipped.join(', ')}.` : ''));
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
exports.COMMENTS_READ = void 0;
exports.fetchLabelledNumbers = fetchLabelledNumbers;
exports.fetchIssueStates = fetchIssueStates;
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
/** Comment bodies make a response heavy, so this batch is smaller than the 100 the
 *  resolver uses for scalar fields. */
const STATE_BATCH_SIZE = 25;
/** The latest comments read per issue. The legacy script reads every page, so an
 *  issue with more comments than this is flagged `commentsTruncated`. */
exports.COMMENTS_READ = 100;
const RATE_LIMITED = 'RATE_LIMITED';
const NOT_FOUND = 'NOT_FOUND';
/** Retries ran out for a reason a smaller request might survive. */
class RetriesExhausted extends Error {
}
/** What the plan needs about each issue, in batched queries: whether it is closed
 *  and why, and its latest comments. An issue GitHub does not know is left out,
 *  which the planner reads as "state unknown". Any other error throws, and so does
 *  a response with no repository, which would otherwise read as "no issue known". */
async function fetchIssueStates(token, owner, repo, numbers, sleepImpl) {
    const states = new Map();
    for (let start = 0; start < numbers.length; start += STATE_BATCH_SIZE) {
        for (const [number, state] of await readStates(token, owner, repo, numbers.slice(start, start + STATE_BATCH_SIZE), sleepImpl)) {
            states.set(number, state);
        }
    }
    return states;
}
/** A batch whose retries are exhausted is halved, down to one issue, where the failure is real. */
async function readStates(token, owner, repo, batch, sleepImpl) {
    try {
        return await readBatch(token, owner, repo, batch, sleepImpl);
    }
    catch (error) {
        if (!(error instanceof RetriesExhausted) || batch.length <= 1)
            throw error;
        const half = Math.ceil(batch.length / 2);
        const merged = await readStates(token, owner, repo, batch.slice(0, half), sleepImpl);
        for (const [number, state] of await readStates(token, owner, repo, batch.slice(half), sleepImpl))
            merged.set(number, state);
        return merged;
    }
}
async function readBatch(token, owner, repo, batch, sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
    // Issue numbers travel as variables, never in the query text.
    const declarations = batch.map((_, i) => `$n${i}: Int!`).join(', ');
    const fields = batch.map((_, i) => `i${i}: issue(number: $n${i}) { closed stateReason comments(last: ${exports.COMMENTS_READ}) { totalCount nodes { body } } }`);
    const variables = { owner, name: repo };
    batch.forEach((number, i) => (variables[`n${i}`] = number));
    const body = JSON.stringify({
        query: `query($owner: String!, $name: String!, ${declarations}) { repository(owner: $owner, name: $name) { ${fields.join(' ')} } }`,
        variables,
    });
    for (let attempt = 0;; attempt++) {
        let result;
        try {
            result = await (0, github_1.fetchJsonWithRetry)(`${github_1.GITHUB_API}/graphql`, { method: 'POST', headers: (0, github_1.githubHeaders)(token, { json: true }), body }, sleepImpl);
        }
        catch (error) {
            throw new RetriesExhausted(error instanceof Error ? error.message : String(error));
        }
        if (!result.ok)
            throw new Error(`GitHub GraphQL HTTP ${result.status} reading issue states.`);
        const { data, errors = [] } = result.data;
        if (errors.some((error) => error.type === RATE_LIMITED)) {
            if (attempt >= github_1.MAX_RETRIES - 1)
                throw new RetriesExhausted(`GitHub GraphQL rate limit persisted past ${github_1.MAX_RETRIES} attempts.`);
            await sleepImpl((0, github_1.backoffMs)(null, attempt));
            continue;
        }
        // An absent issue is a NOT_FOUND on its own alias; anything else, or a missing repository, is a failure.
        const fatal = errors.find((error) => !(error.type === NOT_FOUND && error.path?.[0] === 'repository' && /^i\d+$/.test(String(error.path[1]))));
        if (fatal)
            throw new Error(`GitHub GraphQL error reading issue states: ${fatal.message ?? fatal.type}`);
        const repository = data?.repository;
        if (!repository)
            throw new Error('GitHub GraphQL returned no repository reading issue states.');
        const states = new Map();
        batch.forEach((number, i) => {
            const node = repository[`i${i}`];
            if (!node)
                return;
            const comments = (node.comments?.nodes ?? []).map((comment) => comment.body ?? '');
            states.set(number, {
                closed: node.closed ?? false,
                stateReason: node.stateReason ?? null,
                comments,
                commentsTruncated: (node.comments?.totalCount ?? comments.length) > comments.length,
            });
        });
        return states;
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
    // State and comments are read for the generator's issues (comment rule) and for
    // zcl-only ones (hint), once each, in batches.
    const wanted = [...new Set([...files.labels.issues, ...labelled.issues])];
    const issueFacts = await deps.issueStates(wanted);
    const plan = (0, index_1.planApply)(files, { zclIssues: labelled.issues, zclPullRequests: labelled.pullRequests, issueFacts });
    const warnings = plan.baselineEmpty ? [`No issues carry version:${plan.version}, so there was nothing to compare with.`] : [];
    const unread = wanted.filter((number) => !issueFacts.has(number));
    if (unread.length > 0) {
        warnings.push(`The state of ${unread.length} of ${wanted.length} issues could not be read (${unread.slice(0, 5).map((number) => `#${number}`).join(', ')}${unread.length > 5 ? ', …' : ''}), so the comment comparison is incomplete.`);
    }
    const truncated = [...issueFacts].filter(([, state]) => state.commentsTruncated).map(([number]) => number);
    if (truncated.length > 0) {
        warnings.push(`${truncated.length} issues have more comments than were read (${truncated.slice(0, 5).map((number) => `#${number}`).join(', ')}${truncated.length > 5 ? ', …' : ''}), so an existing comment may be missed.`);
    }
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