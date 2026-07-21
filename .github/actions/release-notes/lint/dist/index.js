/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

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
const escape = (s) => s.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
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
        this.buf += `<ul>${items.map((i) => `<li>${i}</li>`).join('')}</ul>\n`;
        return this;
    }
    async write() {
        appendEnvFile('GITHUB_STEP_SUMMARY', this.buf);
        this.buf = '';
    }
}
exports.summary = new Summary();


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
const core = __importStar(__nccwpck_require__(93));
const parser_1 = __nccwpck_require__(883);
const policy_1 = __nccwpck_require__(86);
const resolver_1 = __nccwpck_require__(306);
/**
 * PR-gate lint entrypoint (spike / warn-only).
 *
 * Security: runs on pull_request_target, metadata-only. The PR body is read
 * from the event payload — never by checking out the PR head. The privileged
 * token comes in as an input (reused MONOREPO_RELEASE_APP).
 *
 * ponytail: warn-only for now — reports the decision to the job summary and
 * outputs; the sticky comment, label sync, and enforce mode ship in follow-up
 * PRs. `enforce=true` flips a fail into a non-zero exit.
 */
async function run() {
    const token = core.getInput('token', { required: true });
    const enforce = core.getBooleanInput('enforce');
    const eventPath = process.env.GITHUB_EVENT_PATH;
    const event = eventPath ? JSON.parse((0, node_fs_1.readFileSync)(eventPath, 'utf8')) : {};
    const pr = event.pull_request;
    if (!pr) {
        core.info('No pull_request in payload; nothing to lint.');
        return;
    }
    const body = pr.body ?? '';
    const section = (0, parser_1.extractSection)(body);
    const optOut = (0, parser_1.isOptOutTicked)(body);
    const refs = section ? (0, parser_1.parseRefs)(section) : [];
    const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '/').split('/');
    const resolver = new resolver_1.GithubResolver(token, owner ?? '', repo ?? '');
    const resolved = await resolver.resolve(refs);
    const decision = (0, policy_1.decide)(resolved, optOut);
    core.setOutput('outcome', decision.outcome);
    core.setOutput('code', decision.code);
    const heading = decision.outcome === 'pass' ? '✅ PR-issue link check passed' : '❌ PR-issue link check failed';
    await core.summary
        .addHeading(heading, 3)
        .addList(decision.reasons.slice())
        .write();
    if (decision.outcome === 'fail') {
        const msg = decision.reasons.join(' ');
        if (enforce)
            core.setFailed(msg);
        else
            core.warning(`[warn-only] ${msg}`);
    }
    else {
        core.info(decision.reasons.join(' '));
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
    const push = (m, repo, num) => {
        if (seen.has(m.index))
            return;
        seen.add(m.index);
        const keyword = m[1] ? m[1].toLowerCase().replace(/\s+/g, ' ') : null;
        refs.push({
            raw: m[0].trim(),
            number: Number(num),
            repo: repo ?? null,
            keyword,
            kind: kindOf(keyword),
            index: m.index,
        });
    };
    for (const m of text.matchAll(URL))
        push(m, m[2] ?? null, m[3]);
    for (const m of text.matchAll(SHORTHAND))
        push(m, m[2] ?? null, m[3]);
    return refs.sort((a, b) => a.index - b.index);
}
/**
 * Slice out a markdown section body: everything after the matching heading up
 * to the next heading of any level (or EOF). Returns null if absent.
 */
function extractSection(body, heading = exports.SECTION_HEADING) {
    const lines = body.split(/\r?\n/);
    const headingRe = new RegExp(`^#{1,6}\\s+${escapeRe(heading)}\\s*$`, 'i');
    const start = lines.findIndex((l) => headingRe.test(l.trim()));
    if (start < 0)
        return null;
    const rest = lines.slice(start + 1);
    const end = rest.findIndex((l) => /^#{1,6}\s+\S/.test(l));
    return (end < 0 ? rest : rest.slice(0, end)).join('\n');
}
/** True when the opt-out checkbox is present and ticked. */
function isOptOutTicked(body) {
    const re = new RegExp(String.raw `^\s*[-*]\s*\[x\]\s*.*${escapeRe(exports.OPT_OUT_PHRASE)}`, 'im');
    return re.test(body);
}
function escapeRe(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
    const sameRepo = refs.filter((r) => !r.crossRepo && r.kind !== 'backport');
    const prRefs = sameRepo.filter((r) => r.target === 'pullRequest');
    if (prRefs.length > 0) {
        const list = prRefs.map((r) => `#${r.number}`).join(', ');
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
    const liveIssues = sameRepo.filter((r) => r.target === 'issue');
    if (liveIssues.length > 0) {
        const closing = liveIssues.some((r) => r.kind === 'closing');
        const list = liveIssues.map((r) => `#${r.number}`).join(', ');
        return {
            outcome: 'pass',
            code: closing ? 'section-closing' : 'section-contributor',
            reasons: [`Linked to issue ${list} in the "Related issues" section.`],
        };
    }
    const dead = sameRepo.filter((r) => r.target === 'missing').map((r) => `#${r.number}`);
    const crossRepo = refs.filter((r) => r.crossRepo).map((r) => r.raw);
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
    async resolveOne(ref) {
        const crossRepo = ref.repo !== null && ref.repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
        if (crossRepo)
            return { ...ref, target: 'missing', crossRepo: true };
        const res = await fetch(`https://api.github.com/repos/${this.owner}/${this.repo}/issues/${ref.number}`, {
            headers: {
                authorization: `Bearer ${this.token}`,
                accept: 'application/vnd.github+json',
                'x-github-api-version': '2022-11-28',
                'user-agent': 'camunda-release-notes-gate',
            },
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