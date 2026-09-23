/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ 573:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubCommentApi = exports.GATE_DOCS_URL = exports.STICKY_MARKER = void 0;
exports.renderStickyComment = renderStickyComment;
exports.syncStickyComment = syncStickyComment;
const github_1 = __nccwpck_require__(631);
/**
 * The single sticky PR comment the gate maintains — one per PR, upserted by
 * a hidden marker so re-runs never stack duplicates. Split like the resolver:
 * render + upsert are pure/injectable; only GithubCommentApi touches the
 * network (plain fetch, no octokit).
 */
/** Never change — how every future run finds the comment it already posted. */
exports.STICKY_MARKER = '<!-- release-notes-pr-gate -->';
/** Where the comment sends authors for the full list of causes and fixes. */
exports.GATE_DOCS_URL = 'https://camunda.github.io/camunda/ci/#release-notes-pr-gate';
/** Deliberately terse: the reasons name the exact fix; everything else (why
 *  the rule exists, rollout state) lives behind GATE_DOCS_URL. */
function renderStickyComment(gate) {
    if (gate.outcome === 'pass') {
        return `${exports.STICKY_MARKER}\n### ✅ Release-notes checks passed\n`;
    }
    const blocks = gate.checks
        .filter((check) => check.outcome === 'fail')
        .map((check) => `**${check.label}**\n${check.reasons.map((reason) => `- ${reason}`).join('\n')}`)
        .join('\n\n');
    const footer = `[Causes and fixes](${exports.GATE_DOCS_URL}) · advisory, does not block merge`;
    return `${exports.STICKY_MARKER}\n### ❌ Release-notes checks\n\n${blocks}\n\n${footer}\n`;
}
/** fail: update or create. pass: update to the resolved body if a comment
 *  already exists (the PR failed earlier), else do nothing — a PR that never
 *  failed stays comment-free. */
async function syncStickyComment(api, gate) {
    const existing = (await api.list()).find((comment) => comment.body.includes(exports.STICKY_MARKER));
    const body = renderStickyComment(gate);
    if (gate.outcome === 'fail') {
        if (existing) {
            await api.update(existing.id, body);
            return 'updated';
        }
        await api.create(body);
        return 'created';
    }
    if (existing) {
        await api.update(existing.id, body);
        return 'resolved';
    }
    return 'noop';
}
/** issue-comments API over plain fetch. Uses GITHUB_TOKEN with
 *  `pull-requests: write` — nothing reacts to this comment as an event, so
 *  no App identity or Vault secrets are needed to post it. */
class GithubCommentApi {
    issueNumber;
    repoUrl;
    headers;
    constructor(token, owner, repo, issueNumber) {
        this.issueNumber = issueNumber;
        this.repoUrl = (0, github_1.repoApiUrl)(owner, repo);
        this.headers = (0, github_1.githubHeaders)(token, { json: true });
    }
    /** Fetch most-recently-updated first, stopping as soon as a page contains
     *  the sticky marker — every run touches it, keeping it near the top. */
    async list() {
        const perPage = 100;
        const all = [];
        for (let page = 1;; page++) {
            const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments?per_page=${perPage}&page=${page}&sort=updated&direction=desc`, { headers: this.headers });
            if (!res.ok)
                throw new Error(`GitHub API ${res.status} listing comments on #${this.issueNumber}`);
            const batch = (await res.json());
            all.push(...batch);
            if (batch.some((comment) => comment.body.includes(exports.STICKY_MARKER)))
                break;
            if (batch.length < perPage)
                break;
        }
        return all;
    }
    async create(body) {
        const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments`, {
            method: 'POST',
            headers: this.headers,
            body: JSON.stringify({ body }),
        });
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} creating comment on #${this.issueNumber}`);
    }
    async update(commentId, body) {
        const res = await fetch(`${this.repoUrl}/issues/comments/${commentId}`, {
            method: 'PATCH',
            headers: this.headers,
            body: JSON.stringify({ body }),
        });
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} updating comment ${commentId}`);
    }
}
exports.GithubCommentApi = GithubCommentApi;


/***/ }),

/***/ 155:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.evaluateGate = evaluateGate;
const parser_1 = __nccwpck_require__(883);
const policy_1 = __nccwpck_require__(86);
const title_1 = __nccwpck_require__(150);
/** Evaluate the PR-issue link for one PR body: section refs + opt-out. */
async function evaluateLink(resolver, body) {
    const section = (0, parser_1.extractSection)(body);
    // Scoped to the section, not the whole body — a stray ticked box outside
    // "Related issues" must not pass a PR whose actual section is empty.
    const optOut = section ? (0, parser_1.isOptOutTicked)(section) : false;
    const refs = section ? (0, parser_1.parseRefs)(section) : [];
    const resolved = await resolver.resolve(refs);
    return (0, policy_1.decide)(resolved, optOut);
}
/** Why a `Backport of #N` marker couldn't be followed, so the author sees
 *  the actual problem rather than a generic "no linked issue". */
function unresolvableBackportReason(backport, resolved) {
    if (resolved?.crossRepo) {
        return `Backport of ${backport.repo}#${backport.number} points to another repository — attribution can only be inherited from a pull request in this repo.`;
    }
    if (resolved?.target === 'issue') {
        return `Backport of #${backport.number} points to an issue, not a pull request — a backport marker must reference the original PR.`;
    }
    return `Backport of #${backport.number} does not resolve to a pull request in this repo — attribution cannot be inherited.`;
}
async function evaluateGate(resolver, input) {
    // A backport PR passes on its own section if it has one; otherwise (bot
    // backports carry only `Backport of #N`) it inherits the ORIGINAL PR's link.
    let deliveryPath = 'direct';
    let link = await evaluateLink(resolver, input.body);
    // Hop only for a genuinely undeclared link — a pr-ref-in-section failure
    // (the section itself links a PR) is a hard error the backport marker must
    // not silently override.
    if (link.outcome === 'fail' && link.code === 'unlinked-undeclared') {
        const backport = (0, parser_1.parseRefs)(input.body).find((ref) => ref.kind === 'backport');
        if (backport) {
            deliveryPath = 'backportHop';
            // Only a same-repo pull request needs its body fetched.
            const [resolved] = await resolver.resolve([backport]);
            const originalBody = resolved?.target === 'pullRequest' && !resolved.crossRepo
                ? await resolver.fetchPullBody(backport.number, backport.repo)
                : null;
            if (originalBody === null) {
                // Speak to the marker itself — the generic section advice is noise here.
                link = {
                    outcome: 'fail',
                    code: 'unlinked-undeclared',
                    reasons: [unresolvableBackportReason(backport, resolved)],
                };
            }
            else {
                const original = await evaluateLink(resolver, originalBody);
                link =
                    original.outcome === 'pass'
                        ? {
                            outcome: 'pass',
                            code: original.code,
                            reasons: [`Backport of #${backport.number} — inherits that PR's attribution (${original.code}).`],
                        }
                        : {
                            outcome: 'fail',
                            code: original.code, // original's actual code, not one fixed code — the two failures need different fixes
                            reasons: [
                                original.code === 'pr-ref-in-section'
                                    ? `Backport of #${backport.number}, but that PR's section links a pull request, not an issue.`
                                    : `Backport of #${backport.number}, but that PR does not link a tracked issue either.`,
                                ...original.reasons,
                            ],
                        };
            }
        }
    }
    // Bot link exemption (Renovate). After the hop, only on a still-failing
    // link — a fallback, never a bypass: an explicit link always wins.
    if (link.outcome === 'fail' && (0, title_1.isLinkExemptAuthor)(input.authorLogin)) {
        link = {
            outcome: 'pass',
            code: 'bot-exempt',
            reasons: [`Author ${input.authorLogin} is exempt from the PR-issue link check.`],
        };
    }
    const checks = [{ label: 'PR-issue link', outcome: link.outcome, reasons: [...link.reasons] }];
    // --- Title lint (skipped for bot authors; link/marker still checked) ---
    if (!(0, title_1.isTitleExemptAuthor)(input.authorLogin)) {
        const title = (0, title_1.lintTitle)(input.title);
        checks.push({ label: 'Title', outcome: title.outcome, reasons: [...title.reasons] });
    }
    const outcome = checks.every((check) => check.outcome === 'pass') ? 'pass' : 'fail';
    return { outcome, checks, deliveryPath, link };
}


/***/ }),

/***/ 93:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.summary = exports.setFailed = exports.warning = exports.info = exports.setOutput = exports.getBooleanInput = exports.getInput = void 0;
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

/***/ 855:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubLabelApi = exports.NO_ISSUE_LABEL_DESCRIPTION = exports.NO_ISSUE_LABEL_COLOR = exports.NO_ISSUE_LABEL = void 0;
exports.decideLabelAction = decideLabelAction;
exports.syncNoIssueLabel = syncNoIssueLabel;
const github_1 = __nccwpck_require__(631);
/**
 * Syncs the display-only `no-issue` label to the PR-issue-link check only
 * (not title). Best-effort like the sticky comment: a sync failure never
 * fails the gate, and it runs regardless of `enforce` — informational, not
 * a blocking mechanism.
 */
/** Do not rename without updating any dashboards/saved searches on it. */
exports.NO_ISSUE_LABEL = 'no-issue';
/** Must match the label as it exists in the repo today — used only to
 *  recreate it if someone deletes it, never to reskin an existing one. */
exports.NO_ISSUE_LABEL_COLOR = 'ededed';
exports.NO_ISSUE_LABEL_DESCRIPTION = 'Release-notes gate: this PR does not link a tracked issue.';
function decideLabelAction(currentLabels, linkOutcome) {
    const has = currentLabels.includes(exports.NO_ISSUE_LABEL);
    if (linkOutcome === 'fail')
        return has ? 'noop' : 'added';
    return has ? 'removed' : 'noop';
}
/** Reads the typed `gate.link` decision, not `gate.outcome`, so a title-only
 *  failure never adds a label that specifically means "no linked issue". */
async function syncNoIssueLabel(api, gate) {
    const current = await api.list();
    const action = decideLabelAction(current, gate.link.outcome);
    if (action === 'added')
        await api.add(exports.NO_ISSUE_LABEL);
    if (action === 'removed')
        await api.remove(exports.NO_ISSUE_LABEL);
    return action;
}
/** issue-labels API over plain fetch — same rationale as GithubCommentApi. */
class GithubLabelApi {
    issueNumber;
    repoUrl;
    headers;
    constructor(token, owner, repo, issueNumber) {
        this.issueNumber = issueNumber;
        this.repoUrl = (0, github_1.repoApiUrl)(owner, repo);
        this.headers = (0, github_1.githubHeaders)(token, { json: true });
    }
    async list() {
        // No pagination (unlike GithubCommentApi): GitHub caps an issue/PR at 100
        // labels, so a single per_page=100 page is always the complete set.
        const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels?per_page=100`, {
            headers: this.headers,
        });
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} listing labels on #${this.issueNumber}`);
        const data = (await res.json());
        return data.map((label) => label.name);
    }
    async add(label) {
        const res = await this.postLabel(label);
        if (res.status === 404) {
            // Repo doesn't have this label defined yet — create it once, then retry.
            await this.ensureLabelExists(label);
            const retry = await this.postLabel(label);
            if (!retry.ok) {
                throw new Error(`GitHub API ${retry.status} adding label "${label}" to #${this.issueNumber} after creating it`);
            }
            return;
        }
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} adding label "${label}" to #${this.issueNumber}`);
    }
    async remove(label) {
        const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels/${encodeURIComponent(label)}`, {
            method: 'DELETE',
            headers: this.headers,
        });
        // 404 means the label is already gone (e.g. a concurrent run removed it) — not an error.
        if (!res.ok && res.status !== 404) {
            throw new Error(`GitHub API ${res.status} removing label "${label}" from #${this.issueNumber}`);
        }
    }
    postLabel(label) {
        return fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels`, {
            method: 'POST',
            headers: this.headers,
            body: JSON.stringify({ labels: [label] }),
        });
    }
    async ensureLabelExists(label) {
        const res = await fetch(`${this.repoUrl}/labels`, {
            method: 'POST',
            headers: this.headers,
            body: JSON.stringify({ name: label, color: exports.NO_ISSUE_LABEL_COLOR, description: exports.NO_ISSUE_LABEL_DESCRIPTION }),
        });
        // 422 means another concurrent run already created it — not an error.
        if (!res.ok && res.status !== 422) {
            throw new Error(`GitHub API ${res.status} creating label "${label}"`);
        }
    }
}
exports.GithubLabelApi = GithubLabelApi;


/***/ }),

/***/ 554:
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
const comment_1 = __nccwpck_require__(573);
const gate_1 = __nccwpck_require__(155);
const core = __importStar(__nccwpck_require__(93));
const labels_1 = __nccwpck_require__(855);
const resolver_1 = __nccwpck_require__(306);
/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on `pull_request`, resolving from the PR head — no
 * privileged token anywhere here. A fork PR gets a read-only GITHUB_TOKEN
 * and no secrets, so the writes below are guarded by `can-write`, not left
 * to fail.
 *
 * Body/title are fetched fresh from the API rather than the event payload,
 * so a PR edited twice in quick succession is judged on the current body.
 *
 * ponytail: warn-only for now. `enforce=true` flips a fail into a non-zero
 * exit; enforce mode ships in a follow-up PR.
 */
async function run() {
    const token = core.getInput('token', { required: true });
    const enforce = core.getBooleanInput('enforce');
    const canWrite = core.getBooleanInput('can-write'); // false on fork PRs — reads still work, only the two writes below are skipped
    const prNumberInput = core.getInput('pr-number').trim();
    const prNumber = Number(prNumberInput);
    if (!Number.isInteger(prNumber) || prNumber <= 0) {
        core.setFailed(`pr-number must be a positive integer, got "${prNumberInput}".`);
        return;
    }
    const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
    const resolver = new resolver_1.GithubResolver(token, owner ?? '', repo ?? '');
    let gate; // a transient API error respects `enforce` too — warn-only means a blip can't turn a green check red
    try {
        const pull = await resolver.fetchPull(prNumber);
        if (!pull) {
            core.info(`PR #${prNumber} could not be fetched; nothing to lint.`);
            return;
        }
        gate = await (0, gate_1.evaluateGate)(resolver, {
            body: pull.body,
            title: pull.title,
            authorLogin: pull.authorLogin,
        });
    }
    catch (err) {
        const msg = `Release-notes gate could not be evaluated: ${err instanceof Error ? err.message : String(err)}`;
        if (enforce)
            core.setFailed(msg);
        else
            core.warning(`[warn-only] ${msg}`);
        return;
    }
    const failed = gate.checks.filter((check) => check.outcome === 'fail');
    const reasons = failed.flatMap((check) => check.reasons.map((reason) => `${check.label}: ${reason}`));
    core.setOutput('outcome', gate.outcome);
    core.setOutput('delivery-path', gate.deliveryPath);
    core.setOutput('failed-checks', failed.map((check) => check.label).join(','));
    // Job summary is the primary report — works everywhere, fork PRs included, no token needed.
    const heading = gate.outcome === 'pass' ? '✅ Release-notes checks passed' : '❌ Release-notes checks failed';
    const summaryLines = gate.checks.map((check) => `${check.outcome === 'pass' ? '✅' : '❌'} ${check.label}: ${check.reasons.join(' ')}`);
    await core.summary.addHeading(heading, 3).addList(summaryLines).write();
    if (!canWrite) {
        core.info('Fork pull request: no write token available, so the sticky comment and no-issue label are skipped.');
    }
    else {
        // Independent, so run concurrently. Each is best-effort — a sync failure
        // is logged and never fails the gate.
        await Promise.allSettled([
            (async () => {
                try {
                    const comments = new comment_1.GithubCommentApi(token, owner ?? '', repo ?? '', prNumber);
                    const action = await (0, comment_1.syncStickyComment)(comments, gate);
                    core.info(`Sticky comment: ${action}.`);
                }
                catch (err) {
                    core.warning(`Sticky comment sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
                }
            })(),
            (async () => {
                try {
                    const labels = new labels_1.GithubLabelApi(token, owner ?? '', repo ?? '', prNumber);
                    const action = await (0, labels_1.syncNoIssueLabel)(labels, gate);
                    core.setOutput('label-action', action);
                    core.info(`no-issue label: ${action}.`);
                }
                catch (err) {
                    core.warning(`Label sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
                }
            })(),
        ]);
    }
    if (gate.outcome === 'fail') {
        const msg = reasons.join(' ');
        if (enforce)
            core.setFailed(msg);
        else
            core.warning(`[warn-only] ${msg}`);
    }
    else {
        core.info('All release-notes checks passed.');
    }
}
run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));


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

/***/ 86:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.decide = decide;
const parser_1 = __nccwpck_require__(883);
/** Distinct issue numbers, in first-seen order — the same issue can appear
 *  twice in a body (`closes #12` and its full URL) and must be reported once. */
function uniqueNumbers(refs) {
    return [...new Set(refs.map((ref) => ref.number))];
}
/**
 * Pure PR-gate decision. Given the resolved refs found inside the section and
 * whether the opt-out checkbox is ticked, decide PASS/FAIL with reasons that
 * name the offending ref and the exact fix.
 *
 * Precedence (a PR ref is always an error, even alongside a valid issue ref):
 *   1. any same-repo ref resolves to a PR      -> FAIL pr-ref-in-section
 *   2. opt-out ticked                          -> PASS opt-out
 *   3. a same-repo ref resolves to a live issue -> PASS section-(closing|contributor)
 *   4. otherwise                               -> FAIL unlinked-undeclared
 *
 * Cross-repo refs and backport markers never satisfy the requirement on their own.
 */
function decide(refs, optOut) {
    const sameRepo = refs.filter((ref) => !ref.crossRepo && ref.kind !== 'backport');
    const prRefs = sameRepo.filter((ref) => ref.target === 'pullRequest');
    if (prRefs.length > 0) {
        const list = uniqueNumbers(prRefs).map((number) => `#${number}`).join(', ');
        return {
            outcome: 'fail',
            code: 'pr-ref-in-section',
            reasons: [
                `The "${parser_1.SECTION_HEADING}" section links a pull request (${list}), not an issue.`,
                'Link the tracked issue this PR resolves (e.g. `closes #1234`), or tick the opt-out checkbox.',
            ],
        };
    }
    if (optOut) {
        return { outcome: 'pass', code: 'opt-out', reasons: ['Opt-out checkbox ticked: no linked issue required.'] };
    }
    const liveIssues = sameRepo.filter((ref) => ref.target === 'issue');
    if (liveIssues.length > 0) {
        const closing = liveIssues.some((ref) => ref.kind === 'closing');
        const list = uniqueNumbers(liveIssues).map((number) => `#${number}`).join(', ');
        return {
            outcome: 'pass',
            code: closing ? 'section-closing' : 'section-contributor',
            reasons: [`Linked to issue ${list} in the "${parser_1.SECTION_HEADING}" section.`],
        };
    }
    const dead = uniqueNumbers(sameRepo.filter((ref) => ref.target === 'missing')).map((number) => `#${number}`);
    const crossRepo = refs.filter((ref) => ref.crossRepo).map((ref) => ref.raw);
    const reasons = [
        `No linked issue found in the "${parser_1.SECTION_HEADING}" section, and the opt-out checkbox is not ticked.`,
        'Add a closing keyword with the tracked issue (e.g. `closes #1234`), or tick the opt-out checkbox.',
    ];
    if (dead.length)
        reasons.push(`These refs do not resolve to an existing issue: ${dead.join(', ')}.`);
    if (crossRepo.length)
        reasons.push(`Cross-repo refs do not count toward this repo's release notes: ${crossRepo.join(', ')}.`);
    return { outcome: 'fail', code: 'unlinked-undeclared', reasons };
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

/***/ 150:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.BOT_LINK_EXEMPT = exports.BOT_TITLE_EXEMPT = exports.HEADER_MAX = exports.TITLE_TYPES = void 0;
exports.lintTitle = lintTitle;
exports.isTitleExemptAuthor = isTitleExemptAuthor;
exports.isLinkExemptAuthor = isLinkExemptAuthor;
/**
 * PR-title lint — `commitlint.config.cjs`'s active rules (type-empty,
 * type-case, type-enum, scope-empty, header-max-length), reimplemented pure
 * to keep the action's runtime deps at zero. CI greps that config to assert
 * TITLE_TYPES/HEADER_MAX still match, so drift fails CI, not a release.
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
/** Wraps a title fragment before it goes into the sticky comment — the gate
 *  posts with a write token, so a raw `@mention` would notify via the bot. */
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
 * Bot authors whose titles are machine-generated and exempt from title lint.
 * Their PR-issue link / backport marker is still validated — only the
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
 * Bot authors exempt from the PR-issue-LINK check — they open PRs from their
 * own template and never tick the opt-out box.
 *
 * MUST STAY SEPARATE from BOT_TITLE_EXEMPT: that set includes
 * `monorepo-devops-automation[bot]`, the backport-PR author. Exempting it
 * here would skip the backport hop, silently dropping backports from notes.
 */
exports.BOT_LINK_EXEMPT = new Set(['renovate[bot]']);
function isLinkExemptAuthor(login) {
    return login !== undefined && exports.BOT_LINK_EXEMPT.has(login);
}


/***/ }),

/***/ 598:
/***/ ((module) => {

module.exports = require("node:crypto");

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
/******/ 	/* webpack/runtime/asset-relocator-loader */
/******/ 	if (typeof __nccwpck_require__ !== 'undefined') __nccwpck_require__.ab = __dirname + "/";
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	// This entry module is referenced by other modules so it can't be inlined
/******/ 	var __webpack_exports__ = __nccwpck_require__(554);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;