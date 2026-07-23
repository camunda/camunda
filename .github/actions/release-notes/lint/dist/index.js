/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ 573:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubCommentApi = exports.STICKY_MARKER = void 0;
exports.renderStickyComment = renderStickyComment;
exports.syncStickyComment = syncStickyComment;
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
/** Build the comment body (pure). Always carries the marker on the first line. */
function renderStickyComment(gate) {
    if (gate.outcome === 'pass') {
        return `${exports.STICKY_MARKER}\n### ✅ Release-notes checks passed\n\n_These checks now pass — no action needed._\n`;
    }
    // One block per failing check, each naming the reasons and the fix.
    const blocks = gate.checks
        .filter((check) => check.outcome === 'fail')
        .map((check) => `**${check.label}**\n${check.reasons.map((reason) => `- ${reason}`).join('\n')}`)
        .join('\n\n');
    const footer = '_Warn-only during rollout: this does not block merge yet. Addressing it keeps release notes accurate._';
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
 * paying. Reuses the injected MONOREPO_RELEASE_APP token (bot identity, so the
 * comment triggers downstream automations that GITHUB_TOKEN events would not).
 */
class GithubCommentApi {
    token;
    issueNumber;
    repoUrl;
    constructor(token, owner, repo, issueNumber) {
        this.token = token;
        this.issueNumber = issueNumber;
        this.repoUrl = `https://api.github.com/repos/${owner}/${repo}`;
    }
    headers() {
        return {
            authorization: `Bearer ${this.token}`,
            accept: 'application/vnd.github+json',
            'content-type': 'application/json',
            'x-github-api-version': '2022-11-28',
            'user-agent': 'camunda-release-notes-gate',
        };
    }
    async list() {
        // The comment is posted on the first gate run and stays put, so the first
        // page (100) always contains it — no pagination needed.
        const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments?per_page=100`, {
            headers: this.headers(),
        });
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} listing comments on #${this.issueNumber}`);
        return (await res.json());
    }
    async create(body) {
        const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments`, {
            method: 'POST',
            headers: this.headers(),
            body: JSON.stringify({ body }),
        });
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} creating comment on #${this.issueNumber}`);
    }
    async update(commentId, body) {
        const res = await fetch(`${this.repoUrl}/issues/comments/${commentId}`, {
            method: 'PATCH',
            headers: this.headers(),
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
    const optOut = (0, parser_1.isOptOutTicked)(body);
    const refs = section ? (0, parser_1.parseRefs)(section) : [];
    const resolved = await resolver.resolve(refs);
    return (0, policy_1.decide)(resolved, optOut);
}
async function evaluateGate(resolver, input) {
    // --- PR-issue link, with a backport-hop fallback (C7/V2) ---
    // A backport PR passes on its own section if it has one (manual template);
    // otherwise (bot backports carry only `Backport of #N`, no section) it passes
    // by inheriting the ORIGINAL PR's attribution.
    let deliveryPath = 'direct';
    let link = await evaluateLink(resolver, input.body);
    if (link.outcome === 'fail') {
        const backport = (0, parser_1.parseRefs)(input.body).find((ref) => ref.kind === 'backport');
        if (backport) {
            const originalBody = await resolver.fetchPullBody(backport.number);
            if (originalBody !== null) {
                const original = await evaluateLink(resolver, originalBody);
                deliveryPath = 'backportHop';
                link =
                    original.outcome === 'pass'
                        ? {
                            outcome: 'pass',
                            code: original.code,
                            reasons: [`Backport of #${backport.number} — inherits that PR's attribution (${original.code}).`],
                        }
                        : {
                            outcome: 'fail',
                            code: 'unlinked-undeclared',
                            reasons: [
                                `Backport of #${backport.number}, but that PR does not link a tracked issue either.`,
                                ...original.reasons,
                            ],
                        };
            }
        }
    }
    const checks = [{ label: 'PR-issue link', outcome: link.outcome, reasons: [...link.reasons] }];
    // --- Title lint (D16: skipped for bot authors; link/marker still checked) ---
    if (!(0, title_1.isTitleExemptAuthor)(input.authorLogin)) {
        const title = (0, title_1.lintTitle)(input.title);
        checks.push({ label: 'Title', outcome: title.outcome, reasons: [...title.reasons] });
    }
    const outcome = checks.every((check) => check.outcome === 'pass') ? 'pass' : 'fail';
    return { outcome, checks, deliveryPath };
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
        this.buf += `<h${level}>${text}</h${level}>\n`;
        return this;
    }
    addList(items) {
        this.buf += `<ul>${items.map((item) => `<li>${item}</li>`).join('')}</ul>\n`;
        return this;
    }
    async write() {
        appendEnvFile('GITHUB_STEP_SUMMARY', this.buf);
        this.buf = '';
    }
}
exports.summary = new Summary();


/***/ }),

/***/ 855:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubLabelApi = exports.NO_ISSUE_LABEL_DESCRIPTION = exports.NO_ISSUE_LABEL_COLOR = exports.NO_ISSUE_LABEL = void 0;
exports.decideLabelAction = decideLabelAction;
exports.syncNoIssueLabel = syncNoIssueLabel;
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
/** Created on demand (see GithubLabelApi.ensureLabelExists) if the repo does
 *  not already have this label — keeps rollout self-contained. */
exports.NO_ISSUE_LABEL_COLOR = 'e4e669';
exports.NO_ISSUE_LABEL_DESCRIPTION = 'Release-notes gate: this PR does not link a tracked issue (warn-only).';
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
 * Reads the check by label rather than gate.outcome so a title-only failure
 * never adds a label whose name specifically means "no linked issue".
 */
async function syncNoIssueLabel(api, gate) {
    const link = gate.checks.find((check) => check.label === 'PR-issue link');
    if (!link)
        return 'noop'; // defensive — the link check is always present today
    const current = await api.list();
    const action = decideLabelAction(current, link.outcome);
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
    token;
    issueNumber;
    repoUrl;
    constructor(token, owner, repo, issueNumber) {
        this.token = token;
        this.issueNumber = issueNumber;
        this.repoUrl = `https://api.github.com/repos/${owner}/${repo}`;
    }
    headers() {
        return {
            authorization: `Bearer ${this.token}`,
            accept: 'application/vnd.github+json',
            'content-type': 'application/json',
            'x-github-api-version': '2022-11-28',
            'user-agent': 'camunda-release-notes-gate',
        };
    }
    async list() {
        const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels?per_page=100`, {
            headers: this.headers(),
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
            headers: this.headers(),
        });
        // 404 means the label is already gone (e.g. a concurrent run removed it) — not an error.
        if (!res.ok && res.status !== 404) {
            throw new Error(`GitHub API ${res.status} removing label "${label}" from #${this.issueNumber}`);
        }
    }
    postLabel(label) {
        return fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels`, {
            method: 'POST',
            headers: this.headers(),
            body: JSON.stringify({ labels: [label] }),
        });
    }
    async ensureLabelExists(label) {
        const res = await fetch(`${this.repoUrl}/labels`, {
            method: 'POST',
            headers: this.headers(),
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
const node_fs_1 = __nccwpck_require__(24);
const comment_1 = __nccwpck_require__(573);
const gate_1 = __nccwpck_require__(155);
const core = __importStar(__nccwpck_require__(93));
const labels_1 = __nccwpck_require__(855);
const resolver_1 = __nccwpck_require__(306);
/**
 * PR-gate lint entrypoint (warn-only rollout).
 *
 * Security: runs on pull_request_target, metadata-only. The PR body/title are
 * read from the event payload — never by checking out the PR head. The
 * privileged token comes in as an input (reused MONOREPO_RELEASE_APP).
 *
 * ponytail: warn-only for now — reports the combined gate outcome (PR-issue
 * link + title lint, with a backport hop) to the job summary, the outputs, a
 * single sticky PR comment (created only on failure, flipped to resolved once
 * fixed), and the display-only `no-issue` label. Both the comment and the
 * label sync regardless of `enforce` — they're informational, not the
 * enforcement mechanism. Enforce mode ships in a follow-up PR. `enforce=true`
 * flips a fail into a non-zero exit.
 */
async function run() {
    const token = core.getInput('token', { required: true });
    const enforce = core.getBooleanInput('enforce');
    const eventPath = process.env.GITHUB_EVENT_PATH;
    const event = eventPath
        ? JSON.parse((0, node_fs_1.readFileSync)(eventPath, 'utf8'))
        : {};
    const pr = event.pull_request;
    if (!pr) {
        core.info('No pull_request in payload; nothing to lint.');
        return;
    }
    const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
    const resolver = new resolver_1.GithubResolver(token, owner ?? '', repo ?? '');
    const gate = await (0, gate_1.evaluateGate)(resolver, {
        body: pr.body ?? '',
        title: pr.title ?? '',
        authorLogin: pr.user?.login,
    });
    const failed = gate.checks.filter((check) => check.outcome === 'fail');
    const reasons = failed.flatMap((check) => check.reasons.map((reason) => `${check.label}: ${reason}`));
    core.setOutput('outcome', gate.outcome);
    core.setOutput('delivery-path', gate.deliveryPath);
    core.setOutput('failed-checks', failed.map((check) => check.label).join(','));
    const heading = gate.outcome === 'pass' ? '✅ Release-notes checks passed' : '❌ Release-notes checks failed';
    const summaryLines = gate.checks.map((check) => `${check.outcome === 'pass' ? '✅' : '❌'} ${check.label}: ${check.reasons.join(' ')}`);
    await core.summary.addHeading(heading, 3).addList(summaryLines).write();
    // Sticky PR comment (D24: comments from day one). A comment sync failure must
    // never fail the gate — warn or not, the outcome above stands.
    if (pr.number) {
        try {
            const comments = new comment_1.GithubCommentApi(token, owner ?? '', repo ?? '', pr.number);
            const action = await (0, comment_1.syncStickyComment)(comments, gate);
            core.info(`Sticky comment: ${action}.`);
        }
        catch (err) {
            core.warning(`Sticky comment sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
        }
        // Display-only `no-issue` label, mirroring the PR-issue-link check. Runs
        // during warn-only rollout too, so the label is already trustworthy by the
        // time enforce mode lands — a sync failure never fails the gate.
        try {
            const labels = new labels_1.GithubLabelApi(token, owner ?? '', repo ?? '', pr.number);
            const action = await (0, labels_1.syncNoIssueLabel)(labels, gate);
            core.setOutput('label-action', action);
            core.info(`no-issue label: ${action}.`);
        }
        catch (err) {
            core.warning(`Label sync failed (non-fatal): ${err instanceof Error ? err.message : String(err)}`);
        }
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
/** Extract every reference from the given text (already scoped by the caller). */
function parseRefs(text) {
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
    const lines = body.split(/\r?\n/);
    const headingRe = new RegExp(`^#{1,6}\\s+${escapeRe(heading)}\\s*$`, 'i');
    const start = lines.findIndex((line) => headingRe.test(line.trim()));
    if (start < 0)
        return null;
    const rest = lines.slice(start + 1);
    const end = rest.findIndex((line) => /^#{1,6}\s+\S/.test(line));
    return (end < 0 ? rest : rest.slice(0, end)).join('\n');
}
/** True when the opt-out checkbox is present and ticked. */
function isOptOutTicked(body) {
    const re = new RegExp(String.raw `^\s*[-*]\s*\[x\]\s*.*${escapeRe(exports.OPT_OUT_PHRASE)}`, 'im');
    return re.test(body);
}
function escapeRe(literal) {
    return literal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}


/***/ }),

/***/ 86:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.decide = decide;
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
        const list = prRefs.map((ref) => `#${ref.number}`).join(', ');
        return {
            outcome: 'fail',
            code: 'pr-ref-in-section',
            reasons: [
                `The "Related issues" section links a pull request (${list}), not an issue.`,
                'Link the tracked issue this PR resolves (e.g. "closes #1234"), or tick the opt-out checkbox.',
            ],
        };
    }
    if (optOut) {
        return { outcome: 'pass', code: 'opt-out', reasons: ['Opt-out checkbox ticked: no linked issue required.'] };
    }
    const liveIssues = sameRepo.filter((ref) => ref.target === 'issue');
    if (liveIssues.length > 0) {
        const closing = liveIssues.some((ref) => ref.kind === 'closing');
        const list = liveIssues.map((ref) => `#${ref.number}`).join(', ');
        return {
            outcome: 'pass',
            code: closing ? 'section-closing' : 'section-contributor',
            reasons: [`Linked to issue ${list} in the "Related issues" section.`],
        };
    }
    const dead = sameRepo.filter((ref) => ref.target === 'missing').map((ref) => `#${ref.number}`);
    const crossRepo = refs.filter((ref) => ref.crossRepo).map((ref) => ref.raw);
    const reasons = [
        'No linked issue found in the "Related issues" section, and the opt-out checkbox is not ticked.',
        'Add a closing keyword with the tracked issue (e.g. "closes #1234"), or tick the opt-out checkbox.',
    ];
    if (dead.length)
        reasons.push(`These refs do not resolve to an existing issue: ${dead.join(', ')}.`);
    if (crossRepo.length)
        reasons.push(`Cross-repo refs do not count toward this repo's release notes: ${crossRepo.join(', ')}.`);
    return { outcome: 'fail', code: 'unlinked-undeclared', reasons };
}


/***/ }),

/***/ 306:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.GithubResolver = void 0;
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
    constructor(token, owner, repo) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
    }
    async resolve(refs) {
        return Promise.all(refs.map((ref) => this.resolveOne(ref)));
    }
    /**
     * Fetch a same-repo pull request's body for backport-hop validation, or null
     * if it does not exist. Used to follow `Backport of #N` to the original PR and
     * validate that PR's attribution (the backport inherits it — C7).
     */
    async fetchPullBody(number) {
        const res = await fetch(`https://api.github.com/repos/${this.owner}/${this.repo}/pulls/${number}`, {
            headers: this.headers(),
        });
        if (res.status === 404)
            return null;
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} fetching PR #${number}`);
        const data = (await res.json());
        return data.body ?? '';
    }
    headers() {
        return {
            authorization: `Bearer ${this.token}`,
            accept: 'application/vnd.github+json',
            'x-github-api-version': '2022-11-28',
            'user-agent': 'camunda-release-notes-gate',
        };
    }
    async resolveOne(ref) {
        const crossRepo = ref.repo !== null && ref.repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
        if (crossRepo)
            return { ...ref, target: 'missing', crossRepo: true };
        const res = await fetch(`https://api.github.com/repos/${this.owner}/${this.repo}/issues/${ref.number}`, {
            headers: this.headers(),
        });
        if (res.status === 404)
            return { ...ref, target: 'missing', crossRepo: false };
        if (!res.ok)
            throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);
        const data = (await res.json());
        return { ...ref, target: data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
    }
}
exports.GithubResolver = GithubResolver;


/***/ }),

/***/ 150:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.BOT_TITLE_EXEMPT = exports.HEADER_MAX = exports.TITLE_TYPES = void 0;
exports.lintTitle = lintTitle;
exports.isTitleExemptAuthor = isTitleExemptAuthor;
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
            reasons: [`Scopes are not used in this repo — drop "${scope}" and write "${type}: …".`],
        };
    }
    if (type !== type?.toLowerCase()) {
        return { outcome: 'fail', code: 'title-type', reasons: [`The type "${type}" must be lower-case.`] };
    }
    if (!exports.TITLE_TYPES.includes(type)) {
        return {
            outcome: 'fail',
            code: 'title-type',
            reasons: [`"${type}" is not an allowed type. Use one of: ${exports.TITLE_TYPES.join(', ')}.`],
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