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
 * The single sticky PR comment the gate maintains. One marked comment per PR,
 * upserted by a hidden marker so re-runs never stack duplicates.
 *
 * Split like the resolver (types.ts): the body render + upsert logic are pure /
 * injectable (unit-tested for idempotency), and only GithubCommentApi touches
 * the network — plain fetch, no octokit, same rationale as GithubResolver.
 */
/** Hidden HTML marker identifying our comment. Never change it — it is how
 * every future run finds the comment it already posted. */
exports.STICKY_MARKER = '<!-- release-notes-pr-gate -->';
/** Where the comment sends authors for the full list of causes and fixes. */
exports.GATE_DOCS_URL = 'https://camunda.github.io/camunda/ci/#release-notes-pr-gate';
/**
 * Build the comment body (pure). Always carries the marker on the first line.
 *
 * Deliberately terse: the reasons name the exact fix, and everything else —
 * why the rule exists, the full cause list, the rollout state — lives in the
 * docs behind GATE_DOCS_URL rather than being restated on every failing PR.
 */
function renderStickyComment(gate) {
    if (gate.outcome === 'pass') {
        return `${exports.STICKY_MARKER}\n### ✅ Release-notes checks passed\n`;
    }
    // One block per failing check, each naming the reasons and the fix.
    const blocks = gate.checks
        .filter((check) => check.outcome === 'fail')
        .map((check) => `**${check.label}**\n${check.reasons.map((reason) => `- ${reason}`).join('\n')}`)
        .join('\n\n');
    const footer = `[Causes and fixes](${exports.GATE_DOCS_URL}) · advisory, does not block merge`;
    return `${exports.STICKY_MARKER}\n### ❌ Release-notes checks\n\n${blocks}\n\n${footer}\n`;
}
/**
 * Idempotently reconcile the PR's single sticky comment against the outcome.
 *
 *  - fail: update the existing comment, or create one if none exists.
 *  - pass: if a comment exists (the PR failed earlier), update it to the
 *          resolved body; if none exists, do nothing — a PR that never failed
 *          stays comment-free, so the gate adds no noise across ~800 PRs.
 */
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
/**
 * issue-comments API over plain fetch (Node global). Same reasoning as
 * GithubResolver: a handful of endpoints, so octokit's bundle cost is not worth
 * paying. Uses GITHUB_TOKEN with `pull-requests: write` — nothing reacts to this
 * comment as an event, so the gate needs no App identity (and therefore no Vault
 * secrets) to post it.
 */
class GithubCommentApi {
    issueNumber;
    repoUrl;
    headers;
    constructor(token, owner, repo, issueNumber) {
        this.issueNumber = issueNumber;
        this.repoUrl = (0, github_1.repoApiUrl)(owner, repo);
        this.headers = (0, github_1.githubHeaders)(token, { json: true });
    }
    /**
     * Fetch comments most-recently-updated first, stopping as soon as a page
     * contains the sticky marker. `syncStickyComment` touches the sticky
     * comment via `create`/`update` on every run, which keeps its `updated_at`
     * near the top — so on a busy PR this typically returns after one page
     * instead of walking every comment. A PR whose sticky comment doesn't exist
     * yet still pages through everything, but that happens at most once per PR.
     */
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
    // Scoped to the same section as refs: the opt-out checkbox lives in the PR
    // template's "Related issues" block, not just anywhere in the body — a
    // missing/renamed heading must not let a stray ticked box elsewhere pass
    // a PR whose actual section was never filled in.
    const optOut = section ? (0, parser_1.isOptOutTicked)(section) : false;
    const refs = section ? (0, parser_1.parseRefs)(section) : [];
    const resolved = await resolver.resolve(refs);
    return (0, policy_1.decide)(resolved, optOut);
}
/**
 * Explain why a `Backport of #N` marker could not be followed to an original
 * PR, so the author sees the actual problem rather than a generic "no linked
 * issue". Takes the caller's classification of the ref rather than resolving
 * it again — the caller already has it, to decide whether fetching the body
 * is worth doing at all.
 */
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
    // --- PR-issue link, with a backport-hop fallback (C7/V2) ---
    // A backport PR passes on its own section if it has one (manual template);
    // otherwise (bot backports carry only `Backport of #N`, no section) it passes
    // by inheriting the ORIGINAL PR's attribution.
    let deliveryPath = 'direct';
    let link = await evaluateLink(resolver, input.body);
    // Only hop for a genuinely undeclared link. A `pr-ref-in-section` failure is a
    // hard error (the section itself links a PR) — an unrelated `Backport of #N`
    // marker must not silently discard it and flip the gate to pass.
    if (link.outcome === 'fail' && link.code === 'unlinked-undeclared') {
        const backport = (0, parser_1.parseRefs)(input.body).find((ref) => ref.kind === 'backport');
        if (backport) {
            deliveryPath = 'backportHop';
            // Only a same-repo pull request needs its body fetched — a cross-repo,
            // missing, or issue-not-PR target already has everything the failure
            // message needs from the classification alone.
            const [resolved] = await resolver.resolve([backport]);
            const originalBody = resolved?.target === 'pullRequest' && !resolved.crossRepo
                ? await resolver.fetchPullBody(backport.number, backport.repo)
                : null;
            if (originalBody === null) {
                // The marker is the PR's stated attribution path, so speak to the marker
                // only — the generic "add a closing keyword / tick opt-out" section advice
                // is irrelevant for a backport PR and would just be noise.
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
                            // A pr-ref-in-section failure and an unlinked-undeclared one
                            // point the author at different fixes, so the hop reports the
                            // original PR's actual code rather than one fixed code for
                            // every failure reason.
                            code: original.code,
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
    // Bot link exemption (Renovate). Applied AFTER the hop and only to a still
    // failing link, so it is a fallback and never a bypass: a bot PR that does
    // link an issue keeps its real code, and the hop above still runs for the
    // backport bot. An explicit link therefore always wins over the exemption.
    if (link.outcome === 'fail' && (0, title_1.isLinkExemptAuthor)(input.authorLogin)) {
        link = {
            outcome: 'pass',
            code: 'bot-exempt',
            reasons: [`Author ${input.authorLogin} is exempt from the PR-issue link check.`],
        };
    }
    const checks = [{ label: 'PR-issue link', outcome: link.outcome, reasons: [...link.reasons] }];
    // --- Title lint (D16: skipped for bot authors; link/marker still checked) ---
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
exports.githubHeaders = githubHeaders;
exports.repoApiUrl = repoApiUrl;
exports.GITHUB_API = 'https://api.github.com';
const USER_AGENT = 'camunda-release-notes-gate';
const GITHUB_API_VERSION = '2022-11-28';
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

/***/ 855:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubLabelApi = exports.NO_ISSUE_LABEL_DESCRIPTION = exports.NO_ISSUE_LABEL_COLOR = exports.NO_ISSUE_LABEL = void 0;
exports.decideLabelAction = decideLabelAction;
exports.syncNoIssueLabel = syncNoIssueLabel;
const github_1 = __nccwpck_require__(631);
/**
 * Syncs the display-only `no-issue` label to mirror the PR-issue-link check
 * only (not the title check — the label answers one question: "does this PR
 * link a tracked issue?"). Best-effort like the sticky comment: a sync
 * failure never fails the gate, and it runs regardless of `enforce` — the
 * label is informational, not a blocking mechanism.
 */
/** The label the gate syncs. Single source of truth — do not rename without
 *  updating any saved searches/dashboards that filter on it. */
exports.NO_ISSUE_LABEL = 'no-issue';
/**
 * Used only by GithubLabelApi.ensureLabelExists, which recreates the label if
 * someone deletes it, so a missing label degrades to a self-heal instead of a
 * failed sync.
 *
 * These MUST match the label as it exists in the repo today (colour `ededed`,
 * no description beyond this one) — otherwise a delete-then-heal cycle would
 * silently reskin a label that predates this gate. The wording deliberately
 * avoids "warn-only": that becomes wrong at the required-flip, and nobody would
 * think to update a label description then.
 */
exports.NO_ISSUE_LABEL_COLOR = 'ededed';
exports.NO_ISSUE_LABEL_DESCRIPTION = 'Release-notes gate: this PR does not link a tracked issue.';
/** Pure decision: given the PR's current labels and the link check's
 * outcome, decide whether to add/remove the no-issue label. */
function decideLabelAction(currentLabels, linkOutcome) {
    const has = currentLabels.includes(exports.NO_ISSUE_LABEL);
    if (linkOutcome === 'fail')
        return has ? 'noop' : 'added';
    return has ? 'removed' : 'noop';
}
/**
 * Reconcile the no-issue label against the gate's PR-issue-link check.
 * Reads the typed `gate.link` decision (not gate.outcome) so a title-only
 * failure never adds a label whose name specifically means "no linked issue".
 */
async function syncNoIssueLabel(api, gate) {
    const current = await api.list();
    const action = decideLabelAction(current, gate.link.outcome);
    if (action === 'added')
        await api.add(exports.NO_ISSUE_LABEL);
    if (action === 'removed')
        await api.remove(exports.NO_ISSUE_LABEL);
    return action;
}
/**
 * issue-labels API over plain fetch. Same rationale as GithubCommentApi /
 * GithubResolver: a handful of endpoints, so octokit's bundle cost isn't
 * worth paying.
 */
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
 * Security: runs on `pull_request`, so the workflow and this action resolve from
 * the PR head — the same trust model as every other lint in ci.yml, and the
 * reason there is no privileged token anywhere here. A fork PR gets a read-only
 * GITHUB_TOKEN and no secrets, which is exactly why the writes below are guarded
 * by `can-write` instead of failing.
 *
 * The PR number comes from the event payload; the body and title are then
 * fetched from the API, so they are current at evaluation time rather than a
 * snapshot from whenever the event fired (a PR edited twice in quick succession
 * must not be judged on the older body).
 *
 * ponytail: warn-only for now — reports the combined gate outcome (PR-issue link
 * + title lint, with a backport hop) to the job summary, the outputs, a single
 * sticky PR comment, and the display-only `no-issue` label. The check itself is
 * the job's own conclusion; GitHub renders it on the PR without us publishing
 * anything. Both syncs run regardless of `enforce` — they are informational, not
 * the enforcement mechanism. `enforce=true` flips a fail into a non-zero exit;
 * enforce mode ships in a follow-up PR.
 */
async function run() {
    const token = core.getInput('token', { required: true });
    const enforce = core.getBooleanInput('enforce');
    // False on fork PRs: GitHub issues a read-only token and withholds secrets
    // there, whatever the workflow's `permissions:` block asks for. Everything the
    // gate READS still works, so it evaluates and reports normally — only the two
    // writes are skipped, and the log says so rather than surfacing a 403.
    const canWrite = core.getBooleanInput('can-write');
    const prNumberInput = core.getInput('pr-number').trim();
    const prNumber = Number(prNumberInput);
    if (!Number.isInteger(prNumber) || prNumber <= 0) {
        core.setFailed(`pr-number must be a positive integer, got "${prNumberInput}".`);
        return;
    }
    const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
    const resolver = new resolver_1.GithubResolver(token, owner ?? '', repo ?? '');
    // A transient API error (403/500) must respect `enforce`: warn-only means the
    // gate never hard-fails, so a blip cannot turn a green check red.
    let gate;
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
    // The job summary is the gate's primary report: it is the one channel that
    // works everywhere, fork PRs included, and needs no token at all.
    const heading = gate.outcome === 'pass' ? '✅ Release-notes checks passed' : '❌ Release-notes checks failed';
    const summaryLines = gate.checks.map((check) => `${check.outcome === 'pass' ? '✅' : '❌'} ${check.label}: ${check.reasons.join(' ')}`);
    await core.summary.addHeading(heading, 3).addList(summaryLines).write();
    if (!canWrite) {
        // Not a failure: a fork PR is still fully evaluated above, and the verdict is
        // in the summary and this log. Stated explicitly so the absence of the usual
        // comment reads as designed rather than broken.
        core.info('Fork pull request: no write token available, so the sticky comment and no-issue label are skipped.');
    }
    else {
        // The sticky comment and the display-only `no-issue` label. Independent of
        // each other, so run them concurrently. Each is best-effort: a sync failure
        // is logged and must never fail the gate — warn or not, the outcome above
        // stands.
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
 */
class GithubResolver {
    token;
    owner;
    repo;
    repoUrl;
    headers;
    constructor(token, owner, repo) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
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
        const prioritized = [...refs].sort((first, second) => priorityOf(first) - priorityOf(second));
        const capped = prioritized.slice(0, MAX_REFS);
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
     * Fetch the fields the gate evaluates for one same-repo pull request, or null
     * if it does not exist.
     *
     * This is how the entrypoint obtains the PR under `workflow_run`, where the
     * event payload carries no `pull_request` object at all. Fetching also means
     * the body is read at evaluation time, so a stale or superseded trigger run
     * can never evaluate an out-of-date body.
     */
    async fetchPull(number) {
        const res = await fetch(`${this.repoUrl}/pulls/${number}`, {
            headers: this.headers,
        });
        if (res.status === 404)
            return null;
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} fetching PR #${number}`);
        const data = (await res.json());
        return { body: data.body ?? '', title: data.title ?? '', authorLogin: data.user?.login };
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
        const res = await fetch(`${this.repoUrl}/issues/${ref.number}`, {
            headers: this.headers,
        });
        if (res.status === 404)
            return { target: 'missing', crossRepo: false };
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);
        const data = (await res.json());
        return { target: data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
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
/******/ 	var __webpack_exports__ = __nccwpck_require__(554);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;