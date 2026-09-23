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
 * The unconditional attribution chain: section refs, then GitHub's native
 * closing field, then a legacy body-wide scan. Pure — decides from one PR's
 * own already-resolved facts; the backport hop is the caller's composition.
 *
 * A dead section ref (`missing`) still fails the chain outright rather than
 * falling through — a bogus number should be fixed in the section, not
 * silently covered by a fallback source. A section ref that resolves to a
 * pull request is different: GitHub's native closing field can still name
 * the issue correctly, so that case falls through instead of failing —
 * otherwise a `Related issues` line pointing at a PR would block attribution
 * even though `closingIssuesReferences` already has the answer.
 */
function decideAttribution(input) {
    if (input.optOut) {
        return { source: 'optOut', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
    }
    const sectionEligible = eligible(input.sectionRefs);
    const sectionLive = sectionEligible.filter((ref) => ref.target === 'issue');
    const sectionDead = sectionEligible.filter((ref) => ref.target === 'missing');
    const deadReasons = sectionDead.length
        ? [`These section refs do not resolve to a live issue in this repo: ${uniqueNumbers(sectionDead).map((n) => `#${n}`).join(', ')}.`]
        : [];
    if (sectionLive.length > 0) {
        return { source: 'section', issueNumbers: uniqueNumbers(sectionLive), deliveryPath: 'direct', reasons: deadReasons };
    }
    if (sectionDead.length > 0) {
        return { source: 'resolutionFailed', issueNumbers: [], deliveryPath: 'direct', reasons: deadReasons };
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
 * A PR merged after its branch's gate watermark terminates at the section
 * step by construction, so any fallback source past that point means the
 * section contract wasn't observed. `mergedAt` must be the PR the decision
 * came FROM — for a backport hop, the original's. See GENERATOR.md for the
 * full attribution-chain rationale.
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


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.BOT_CATEGORY_OVERRIDES = void 0;
exports.internalIssueKind = internalIssueKind;
exports.hiddenFromCustomerBody = hiddenFromCustomerBody;
exports.stripBackportPrefix = stripBackportPrefix;
exports.parseDependencyUpdate = parseDependencyUpdate;
exports.formatDependencyUpdates = formatDependencyUpdates;
exports.categorize = categorize;
/**
 * Pure title-type -> release-notes-section categorization. No IO: the caller
 * supplies the already-resolved title and the labels already fetched from
 * the API. See GENERATOR.md for the full section table and rationale.
 */
/** Bots whose own title can't be trusted as the category source. */
exports.BOT_CATEGORY_OVERRIDES = {
    'backport-action': 'inherit-original',
    'monorepo-devops-automation[bot]': 'inherit-original',
    'renovate[bot]': 'deps',
    'dependabot[bot]': 'deps',
};
/** null = excluded from both outputs (release-merge PRs). An unparseable
 *  title falls back to Uncategorized — never dropped. Keyed on the full
 *  `TITLE_TYPES` tuple so a new commitlint type is a compile error here
 *  until it is routed to a section. */
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
/** A customer must never see these, whatever the delivering PR's title says
 *  — the conventional-commit type describes the CHANGE, not the audience. */
const INTERNAL_ISSUE_KINDS = new Set(['kind/task', 'kind/epic']);
/** The `kind/*` label that marks one issue internal, or null. Returns the
 *  label rather than a boolean so the audit line can name which one did it. */
function internalIssueKind(issueLabels) {
    return issueLabels.find((label) => INTERNAL_ISSUE_KINDS.has(label)) ?? null;
}
/** Hidden only when EVERY linked issue is internal — one PR routinely closes
 *  a customer bug and a QA task together, and hiding on any internal label
 *  would suppress the real fix too. No issue at all is never hidden. */
function hiddenFromCustomerBody(issueLabelSets) {
    if (issueLabelSets.length === 0)
        return null;
    const kinds = issueLabelSets.map(internalIssueKind);
    return kinds.every((kind) => kind !== null) ? kinds[0] : null;
}
// type + optional (scope) + optional ! + ": " + subject. Caller already
// strips a `[Backport ...]` prefix, so no bracket tolerance needed here.
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
// A renovate body table row. Anchored on the leading `[name]` and the
// backtick-quoted arrow pair only — the column count varies between shapes.
const RENOVATE_TABLE_ROW = /^\|\s*\[([^\]]+)\].*?`([^`]+)`\s*→\s*`([^`]+)`.*\|\s*$/gm;
/** Each dependency a `deps:` PR moves and its versions — "name: old → new",
 *  not the bot's prose. Structured, not pre-formatted, so the renderer can
 *  collapse repeated updates across a release into one line. */
function parseDependencyUpdate(input) {
    const bump = DEPENDABOT_BUMP.exec(input.title);
    if (bump) {
        const [, name, from, to] = bump;
        return [{ name: name, from: from, to: to }];
    }
    return [...input.body.matchAll(RENOVATE_TABLE_ROW)].map((match) => ({
        name: match[1],
        from: match[2],
        to: match[3],
    }));
}
/** The one-line form used as an entry title. */
function formatDependencyUpdates(updates) {
    return updates.map((update) => `${update.name}: ${update.from} → ${update.to}`).join('; ');
}
function categorize(input) {
    const reasons = [];
    const override = input.authorLogin ? exports.BOT_CATEGORY_OVERRIDES[input.authorLogin] : undefined;
    const type = override === 'deps' ? 'deps' : parseType(input.title);
    if (type === null) {
        const author = input.authorLogin ? ` (author ${input.authorLogin})` : '';
        reasons.push(`Title does not parse as a conventional commit${author}: "${input.title}".`);
    }
    const mapped = type !== null && type in SECTION_BY_TYPE ? SECTION_BY_TYPE[type] : undefined;
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

/***/ 388:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.closesIssueNumbers = closesIssueNumbers;
/** Closed without being delivered; no pull request may claim these, backport hop included. */
const ABANDONED_REASONS = new Set(['NOT_PLANNED', 'DUPLICATE']);
function abandoned(closure) {
    return closure !== undefined && closure.stateReason !== null && ABANDONED_REASONS.has(closure.stateReason);
}
/** The subset of `issueNumbers` this pull request actually closed. */
function closesIssueNumbers(input, closures) {
    return input.issueNumbers.filter((issueNumber) => {
        const closure = closures.get(issueNumber);
        if (abandoned(closure))
            return false;
        if (input.deliveryPath === 'backportHop')
            return true;
        if (closure?.closerPrNumber != null)
            return closure.closerPrNumber === input.prNumber;
        return input.declaredCloses.includes(issueNumber);
    });
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
const categorize_1 = __nccwpck_require__(493);
const delivery_1 = __nccwpck_require__(388);
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
    // GitHub writes the PR number into the merge-commit subject, so most commits'
    // mapping is already in hand without walking branch history — but a guessed
    // number is only confirmed against the PR's own `mergeCommit`; anything
    // unconfirmed falls back to the original `associatedPullRequests` query.
    const candidateBySha = new Map();
    for (const commit of walked) {
        const match = /\(#(\d+)\)\s*$/.exec(commit.message);
        if (match)
            candidateBySha.set(commit.sha, Number(match[1]));
    }
    const metaByNumber = new Map();
    for (const meta of await graphql.fetchPrMetadata([...new Set(candidateBySha.values())], true)) {
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
    for (const meta of await graphql.fetchPrMetadata(prNumbers.filter((number) => !metaByNumber.has(number)))) {
        metaByNumber.set(meta.number, meta);
    }
    const metadata = prNumbers.map((number) => metaByNumber.get(number)).filter((meta) => meta !== undefined); // walk order, kept stable
    // Pre-warm the union of section refs + full-body refs the pipeline might ask
    // about, each capped by the same policy the resolver applies per call.
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
                attributionSource: output.attribution.source,
                dependencies: output.dependencies,
            },
            delivery: {
                prNumber: output.number,
                deliveryPath: output.attribution.deliveryPath,
                declaredCloses: pr.closingIssuesReferences,
            },
            // A merge-type PR (section: null) is excluded from every output, so it must never trip the unattributed guard.
            bucketed: output.categorization.section !== null &&
                (output.attribution.source === 'unattributed' || output.attribution.source === 'resolutionFailed'),
            warnings,
        };
    };
    // Each PR's work is almost entirely waiting on the network, so it's worker-
    // pooled rather than serial. Results land in index-keyed slots (never
    // pushed) since completion order is arbitrary but output order must not be.
    //
    // ponytail: 3 workers, not more — `resolve()` already runs CONCURRENCY refs
    // per PR, so the two multiply; 6 here tripped GitHub's secondary rate limit
    // on burst width, not quota. Raising this wants one shared limit, not a
    // bigger number here.
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
        // Flushed even on a partial failure — those diagnostics are the most worth reading.
        for (const entry of processed) {
            if (entry)
                for (const warning of entry.warnings)
                    core.warning(warning);
        }
    }
    // After attribution, since the issue set is what attribution produces —
    // includes backport-settled issues too, since kind/* visibility needs all of them.
    const wantedIssues = new Set();
    for (const entry of processed) {
        if (!entry)
            continue;
        for (const issueNumber of entry.renderPr.issueNumbers)
            wantedIssues.add(issueNumber);
    }
    const issueFacts = await graphql.fetchIssueFacts([...wantedIssues]);
    core.info(`Read labels and the close event of ${issueFacts.size} of ${wantedIssues.size} referenced issue(s).`);
    const attributed = [];
    const unattributed = [];
    const issueFactsWarnings = [];
    for (const entry of processed) {
        if (!entry)
            continue;
        const internalKind = (0, categorize_1.hiddenFromCustomerBody)(entry.renderPr.issueNumbers.map((issueNumber) => issueFacts.get(issueNumber)?.labels ?? []));
        if (internalKind) {
            issueFactsWarnings.push(`PR #${entry.renderPr.number}: linked issue is ${internalKind} — kept in the full asset, hidden from the customer body.`);
        }
        for (const issueNumber of entry.renderPr.issueNumbers) {
            if (issueFacts.get(issueNumber)?.labelsTruncated) {
                issueFactsWarnings.push(`Issue #${issueNumber} has more than 20 labels — kind/* visibility could not be verified against the full label set.`);
            }
        }
        const renderPr = {
            ...entry.renderPr,
            visibility: internalKind ? 'internal' : entry.renderPr.visibility,
            closesIssueNumbers: (0, delivery_1.closesIssueNumbers)({ ...entry.delivery, issueNumbers: entry.renderPr.issueNumbers }, issueFacts),
            // positively open only — an issue absent from the lookup is not evidence of anything
            openIssueNumbers: entry.renderPr.issueNumbers.filter((issueNumber) => issueFacts.get(issueNumber)?.closed === false),
        };
        (entry.bucketed ? unattributed : attributed).push(renderPr);
    }
    for (const warning of issueFactsWarnings)
        core.warning(warning);
    const auditWarnings = [...rangeReasons, ...processed.flatMap((entry) => entry?.warnings ?? []), ...issueFactsWarnings];
    const result = (0, render_1.render)(attributed, unattributed, {
        version: input.targetVersion,
        allowUnattributed: input.allowUnattributed,
        unattributedReason: input.unattributedReason,
        warnings: auditWarnings,
    });
    (0, node_fs_1.mkdirSync)(input.outputDir, { recursive: true }); // writeFileSync doesn't create the dir; recursive for a nested output-dir too
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/CHANGELOG-${input.targetVersion}.md`, result.fullAsset);
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/changelog.json`, JSON.stringify(result.changelogJson, null, 2));
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/labels.json`, JSON.stringify(result.labelsJson, null, 2));
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/audit.json`, JSON.stringify(result.auditJson, null, 2));
    (0, node_fs_1.writeFileSync)(`${input.outputDir}/comments.json`, JSON.stringify(result.commentsJson, null, 2));
    core.setOutput('customer-body', result.customerBody);
    await core.summary // both bodies, written even when the unattributed guard trips, never skipped on failure
        .addHeading(`Release notes — ${input.targetVersion}`, 2)
        .addHeading('Customer-facing body', 3)
        .addRaw(result.customerBody)
        .addHeading('Full asset (includes internal-only sections)', 3)
        .addRaw(result.fullAsset)
        .write();
    if (result.failureReason)
        throw new Error(result.failureReason); // fails only AFTER every diagnostic output exists on disk
    core.info(`Generated release notes for ${input.targetVersion}: ${attributed.length} attributed PR(s).`);
}
run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));


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
const MAX_RETRIES = 5;
const MAX_RETRY_AFTER_MS = 60_000; // beyond this the job should fail rather than hold a runner
/** 429, or 403 with a `retry-after` (a bare 403 is a real permission failure).
 *  5xx is transient. Mirrors resolve/index.ts's GraphQL-side check — same
 *  throttle shapes, REST transport. */
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
            if (attempt >= MAX_RETRIES - 1) {
                throw new Error(`GitHub API returned an unparseable body past ${MAX_RETRIES} attempts (${url}).`);
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

/***/ 782:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.processPr = processPr;
const attribution_1 = __nccwpck_require__(233);
const categorize_1 = __nccwpck_require__(493);
const parser_1 = __nccwpck_require__(883);
const title_1 = __nccwpck_require__(150);
/** The legacy scan resolves only when earlier steps can't terminate — every
 *  ref costs an API call, and section refs would otherwise resolve twice. */
async function attributeDirectly(resolver, body, closingIssuesReferences) {
    const section = (0, parser_1.extractSection)(body);
    const optOut = section ? (0, parser_1.isOptOutTicked)(section) : false;
    const sectionRefs = section ? await resolver.resolveRefs((0, parser_1.parseRefs)(section)) : [];
    const needsLegacyScan = !optOut && !(0, attribution_1.hasEligibleRefs)(sectionRefs) && closingIssuesReferences.length === 0;
    const legacyRefs = needsLegacyScan ? await resolver.resolveRefs((0, parser_1.parseRefs)(body)) : [];
    return (0, attribution_1.decideAttribution)({ optOut, sectionRefs, closingIssuesReferences, legacyRefs });
}
/** The trigger for the bot-link exemption. Mirrors the gate's own
 *  failing-link outcomes, not just "nothing found". */
const UNRESOLVED_SOURCES = new Set(['unattributed', 'resolutionFailed']);
/** Direct scan, then the backport hop, then bot link exemption last — an
 *  exempt bot that did link a real issue keeps it. See GENERATOR.md § 3 for
 *  why the hop always trusts the original over the backport's own body. */
async function attributePr(resolver, pr, original) {
    let decision = await attributeDirectly(resolver, pr.body, pr.closingIssuesReferences);
    let mergedAt = pr.mergedAt;
    if (decision.source !== 'optOut') {
        const originalPull = await original(); // null for an ordinary PR — costs nothing
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
/** Category-detection title and display title share one lookup — an
 *  inherit-original bot's own title is garbage for both. */
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
        const updates = (0, categorize_1.parseDependencyUpdate)({ title: pr.title, body: pr.body });
        if (updates.length > 0)
            return (0, categorize_1.formatDependencyUpdates)(updates);
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
    let pending; // memoized: attribution + inherit-original both want the same original PR
    const original = () => (pending ??= backport ? resolver.fetchOriginalPull(backport.number, backport.repo) : Promise.resolve(null));
    const { decision: attribution, mergedAt } = await attributePr(resolver, pr, original);
    const { displayTitle, categorization } = await categorizePr(resolver, pr, original, override);
    const title = await resolveDisplayTitle(resolver, pr, categorization, attribution, displayTitle);
    const anomaly = (0, attribution_1.evaluatePostGateAnomaly)({
        mergedAt,
        gateRequiredAt: options.gateRequiredAt,
        source: attribution.source,
    });
    const dependencies = categorization.section === 'Dependency updates' ? (0, categorize_1.parseDependencyUpdate)({ title: pr.title, body: pr.body }) : [];
    return { number: pr.number, title, attribution, categorization, anomaly, dependencies };
}


/***/ }),

/***/ 53:
/***/ ((__unused_webpack_module, exports) => {


/**
 * The pure part of the range resolver: which previous point to diff against,
 * and how to turn git's answer into a deduped PR list. The git calls
 * themselves live in ./walk. See GENERATOR.md § 1 for the baseline table.
 */
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.resolveBaselineStrategy = resolveBaselineStrategy;
exports.resolveCommitsToPrs = resolveCommitsToPrs;
// Alphas and candidates are both 1-based (no `-alpha0`/`-rc0`). Dotted alphas
// (`8.8.0-alpha4.1`) are deliberately unaccepted — a closed shape from a
// retired process, not an oversight.
const VERSION = /^(\d+)\.(\d+)\.(\d+)(?:-alpha([1-9]\d*))?$/;
const RC_SUFFIX = /-rc([1-9]\d*)$/;
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
    // Validated against the version a candidate is FOR, so `rcN > 1`'s
    // short-circuit below still gets the right error on a bad shape.
    const rc = RC_SUFFIX.exec(target);
    const baseVersion = target.replace(RC_SUFFIX, '');
    const v = parseVersion(baseVersion, target);
    if (v.alpha !== null && v.patch !== 0) {
        throw new Error(`Unsupported release version "${target}": an alpha is a pre-release of a minor, so it must carry patch 0.`);
    }
    // rcN -> rc(N-1); rc1 has no previous candidate and falls through below.
    if (rc && Number(rc[1]) > 1) {
        return { kind: 'previousTag', ref: `${baseVersion}-rc${Number(rc[1]) - 1}` };
    }
    // alpha1-of-cycle: no prior tag on this line yet, so fork off stable/<prev minor>.
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
/** The only legitimate PR-less commits — release-plugin version bumps, and
 *  the reverts that repair an orphaned tag. See GENERATOR.md § 1. */
const AUTOMATION_WHITELIST = /^(?:Revert ")?\[maven-release-plugin\]/;
/** A release branch is `release-<version>` — `release-8.9.19`,
 *  `release-8.10.0-alpha5`. Anchored on the version so it cannot swallow a
 *  feature branch that merely starts with the word. */
const RELEASE_BRANCH = /^release-(\d+)\.(\d+)\.\d+/;
/**
 * The branches a delivered pull request in this release could have targeted.
 * `RELEASE_BRANCH` is the temporary `release-X.Y.Z` tag branch, which nothing
 * merges into, so this maps it to the real line(s): `stable/X.Y` for a patch
 * or post-branch alpha, `main` for a pre-branch alpha. Both are accepted —
 * guessing between them would be wrong half the time.
 */
function releaseLineBranches(releaseBranch) {
    const match = RELEASE_BRANCH.exec(releaseBranch);
    return match ? [`stable/${match[1]}.${match[2]}`, 'main'] : [releaseBranch];
}
/**
 * A release-branch merge-back delivers nothing of its own — it merges
 * `release-X.Y.Z` back into the line it was cut from, already published in
 * that release's own notes. Checked by branch topology, not title, since
 * that also catches ones merged before this rule existed. See GENERATOR.md § 1.
 */
function isReleaseMergeBack(pr) {
    // Version-shaped, not a bare `release-` prefix: a feature branch called
    // `release-notes-gate` targeting a stable line is ordinary work, not a
    // merge-back, and excluding it would drop real delivered work.
    return RELEASE_BRANCH.test(pr.headRefName) && (pr.baseRefName.startsWith('stable/') || pr.baseRefName === 'main');
}
/**
 * Dedupe a first-parent commit walk to one entry per PR. Ambiguity rule: prefer
 * the PR targeting the release LINE (see `releaseLineBranches`); still tied ->
 * audit, never guess. `rangeShas` restricts "shipped" to merges actually inside
 * the walk — a commit's associated PR can otherwise be the *next* release's
 * merge-back, merged after this tag was cut. See GENERATOR.md § 1.
 */
function resolveCommitsToPrs(commits, releaseBranch, rangeShas) {
    const reasons = [];
    const lineBranches = releaseLineBranches(releaseBranch);
    // Insertion-ordered, so this both dedupes and preserves walk order.
    const prNumbers = new Set();
    for (const commit of commits) {
        // Checked first, ahead of any associated-PR anomaly below: a release-plugin
        // commit must never be attributed to a pull request, even one GitHub
        // reports with a null mergeCommitOid (which the shipped-anomaly branch
        // below otherwise keeps unconditionally).
        if (AUTOMATION_WHITELIST.test(commit.message))
            continue;
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
        const matchingBranch = shipped.filter((pr) => lineBranches.includes(pr.baseRefName));
        if (matchingBranch.length === 1) {
            prNumbers.add(matchingBranch[0].number);
        }
        else {
            const list = shipped.map((pr) => `#${pr.number}`).join(', ');
            reasons.push(`Ambiguous commit ${commit.sha}: associated with multiple pull requests (${list}) and no unique match targeting ${lineBranches.join(' or ')} — never guessing.`);
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
 * reads. Pure — see GENERATOR.md § 6. Which issues a PR closed is supplied
 * by the caller, never derived here from a `closes` keyword.
 */
/** Bumped deliberately on any output-shape change, so a consumer never
 *  silently misreads a shape it wasn't built for. */
exports.SCHEMA_VERSION = '2.0.0';
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
/** An opt-out PR is grouped under its own section, never its type's. */
function groupNameFor(pr) {
    if (pr.attributionSource === 'optOut')
        return 'Changes without a tracked issue';
    return pr.section ?? 'Uncategorized';
}
function entryKeyFor(pr) {
    return pr.issueNumbers.length > 0 ? `issue:${pr.issueNumbers[0]}` : `pr:${pr.number}`;
}
/** Unknown section sorts after every known one — appended, never dropped. */
function sectionRank(name) {
    const index = SECTION_ORDER.indexOf(name);
    return index === -1 ? SECTION_ORDER.length : index;
}
/** Where a group's PRs disagree on section, the most customer-visible one
 *  wins (ranked by SECTION_ORDER) rather than "whichever PR closed the
 *  issue" — see GENERATOR.md § 6 for why. */
function toEntries(prs) {
    // Grouped by package, not by its own PR; anything with a linked issue keeps issue grouping below.
    const isDependencyBump = (pr) => (pr.dependencies?.length ?? 0) > 0 && pr.issueNumbers.length === 0;
    const grouped = new Map();
    for (const pr of prs) {
        if (isDependencyBump(pr))
            continue;
        const key = entryKeyFor(pr);
        const list = grouped.get(key) ?? [];
        list.push(pr);
        grouped.set(key, list);
    }
    const entries = [...grouped.values()].map((group) => {
        const lead = group.reduce((best, pr) => sectionRank(groupNameFor(pr)) < sectionRank(groupNameFor(best)) ? pr : best);
        // Delivered is asked of the entry's own titling issue, not any issue the group touches.
        const [keyIssue] = lead.issueNumbers;
        const stillOpen = keyIssue !== undefined && group.some((pr) => pr.openIssueNumbers?.includes(keyIssue));
        return {
            groupName: groupNameFor(lead),
            title: lead.title,
            issueNumbers: [...new Set(group.flatMap((pr) => pr.issueNumbers))],
            prNumbers: group.map((pr) => pr.number),
            breaking: group.some((pr) => pr.breaking),
            delivered: !stillOpen,
        };
    });
    return [...entries, ...collapseDependencies(prs.filter(isDependencyBump))];
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
    const partial = entry.delivered ? '' : ' (partially delivered)'; // issue still OPEN — work landed, issue didn't finish
    return `- ${entry.title} (${entry.issueNumbers.map((n) => `#${n}`).join(', ')}) — ${prs}${partial}`;
}
/** One line per dependency, not per bump — collapsed to the earliest `from`
 *  and latest `to` across every PR that moved it. See GENERATOR.md § 6. */
/** Dotted-numeric versions compare numerically; a digest/sha/date tag has no order and returns null. */
function versionKey(value) {
    const trimmed = value.replace(/^v/, '');
    return /^\d+(\.\d+)*$/.test(trimmed) ? trimmed.split('.').map(Number) : null;
}
function isLower(candidate, current) {
    const [a, b] = [versionKey(candidate), versionKey(current)];
    if (!a || !b)
        return false;
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
        const [left, right] = [a[i] ?? 0, b[i] ?? 0];
        if (left !== right)
            return left < right;
    }
    return false;
}
/** The release's actual start/end version for one package — numeric compare
 *  where possible, walk-order positional fallback otherwise (a digest/sha,
 *  where walk order IS the chronology). See GENERATOR.md § 6. */
function versionRange(updates) {
    let from = updates[updates.length - 1].from;
    let to = updates[0].to;
    for (const update of updates) {
        if (isLower(update.from, from))
            from = update.from;
        if (isLower(to, update.to))
            to = update.to;
    }
    return { from, to };
}
function collapseDependencies(prs) {
    const byName = new Map();
    for (const pr of prs) {
        for (const update of pr.dependencies ?? []) {
            const existing = byName.get(update.name) ?? { prNumbers: [], updates: [], groupName: groupNameFor(pr) };
            if (!existing.prNumbers.includes(pr.number))
                existing.prNumbers.push(pr.number);
            existing.updates.push(update);
            byName.set(update.name, existing);
        }
    }
    return [...byName].map(([name, group]) => ({
        groupName: group.groupName,
        title: `${name}: ${versionRange(group.updates).from} → ${versionRange(group.updates).to}`,
        issueNumbers: [],
        prNumbers: group.prNumbers,
        breaking: false,
        delivered: true,
    }));
}
/** One comment per issue, not per PR that touched it — the marker is keyed
 *  on `<version>:issue-<N>`, so two PRs sharing an issue must aggregate into
 *  one row or the marker collision drops one silently on publish. */
function commentFor(prs, issueNumber, version) {
    const numbers = prs.map((pr) => `#${pr.number}`).join(', ');
    return prs.some((pr) => pr.closesIssueNumbers.includes(issueNumber))
        ? { relationKind: 'closing', text: `Released in ${version} (${numbers}).` }
        : { relationKind: 'contributor', text: `Partially delivered in ${version} by ${numbers}.` };
}
/** Two different failures need opposite fixes (add a link vs. repair the
 *  target), so name them apart rather than one generic "unattributed". */
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
    const unattributedReason = options.unattributedReason ?? '';
    const all = [...prs, ...unattributed];
    const customerPrs = prs.filter((pr) => pr.visibility === 'customer' && pr.section !== null);
    const assetPrs = all.filter((pr) => pr.section !== null);
    const customerBody = renderSectionedBody(customerPrs);
    const fullAsset = renderSectionedBody(assetPrs);
    const prsByIssue = new Map(); // insertion-ordered: issues come out in walk order
    for (const pr of all) {
        for (const issueNumber of pr.issueNumbers) {
            prsByIssue.set(issueNumber, [...(prsByIssue.get(issueNumber) ?? []), pr]);
        }
    }
    const commentEntries = [...prsByIssue].map(([issueNumber, prs]) => ({
        issueNumber,
        prNumbers: prs.map((pr) => pr.number),
        ...commentFor(prs, issueNumber, options.version),
        marker: `<!-- release-notes:${options.version}:issue-${issueNumber} -->`,
    }));
    // Only recorded when the override actually let the guard pass — else a plain
    // failure would look identical to an approved exception in this file.
    const overrides = guardFailed ? [] : unattributed.map((pr) => ({ number: pr.number, reason: unattributedReason }));
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
        auditJson: {
            schemaVersion: exports.SCHEMA_VERSION,
            version: options.version,
            overrides,
            warnings: options.warnings ?? [],
        },
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
/** `associatedPullRequests` walks branch history per commit; `pullRequest(number:)`
 *  is a direct lookup — an order of magnitude cheaper, so the two can't share
 *  a batch size. 100 commit aliases 502s at ~11s server-side timeout; 25 is
 *  the size that doesn't. */
const COMMIT_BATCH_SIZE = 25;
/** A direct lookup — 100 aliases return in under a second. */
const PR_METADATA_BATCH_SIZE = 100;
const MAX_RETRIES = 5;
exports.RATE_LIMITED_ERROR_TYPE = 'RATE_LIMITED';
/** A field-level error, not a null field — the rest of the batch still comes back. */
const NOT_FOUND_ERROR_TYPE = 'NOT_FOUND';
/** Thrown when every retry failed for a reason a SMALLER request might
 *  survive. A malformed response fails identically at any size — bisecting
 *  that would replace one clear error with a storm of requests. */
class RetriesExhaustedError extends Error {
}
exports.RetriesExhaustedError = RetriesExhaustedError;
const MAX_RETRY_AFTER_MS = 60_000; // beyond this the job should fail rather than hold a runner
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
            const query = `query($owner: String!, $name: String!, ${batch.map((_, j) => `$n${j}: Int!`).join(', ')}) {
        repository(owner: $owner, name: $name) {
          ${batch
                .map((_, j) => `i${j}: issue(number: $n${j}) { closed stateReason labels(first: 20) { nodes { name } pageInfo { hasNextPage } } timelineItems(last: 1, itemTypes: CLOSED_EVENT) { nodes { ... on ClosedEvent { closer { __typename ... on PullRequest { number repository { nameWithOwner } } } } } } }`)
                .join('\n')}
        }
      }`;
            const variables = { owner: this.owner, name: this.repo };
            batch.forEach((number, j) => (variables[`n${j}`] = number));
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
        const query = `query($owner: String!, $name: String!, ${numbers.map((_, i) => `$n${i}: Int!`).join(', ')}) {
      repository(owner: $owner, name: $name) {
        ${numbers
            .map((_, i) => `pr${i}: pullRequest(number: $n${i}) { number title body mergedAt baseRefName headRefName mergeCommit { oid } author { login __typename } labels(first: 20) { nodes { name } pageInfo { hasNextPage } } closingIssuesReferences(first: 20) { nodes { number } pageInfo { hasNextPage } } }`)
            .join('\n')}
      }
    }`;
        const variables = { owner: this.owner, name: this.repo };
        numbers.forEach((number, i) => (variables[`n${i}`] = number));
        const repository = await this.requestRepository(query, variables, speculative);
        return numbers.flatMap((number, i) => {
            const node = repository[`pr${i}`];
            if (speculative && (node === null || node === undefined))
                return [];
            const pr = assertField(node, `repository.pr${i} (PR #${number})`);
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
                    closingIssuesReferences: assertField(pr.closingIssuesReferences?.nodes, `closingIssuesReferences.nodes on PR #${number}`).map((issue) => issue.number),
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
                if (!(await retryableStatus(res)))
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
function buildPipelineResolver(rest, warmRefs) {
    // Cross-repo refs are never pre-warmed — the REST path classifies those without an API call.
    const sameRepoNumber = (ref) => (ref.repo === null ? ref.number : null);
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
                return freshByPosition.get(ref.index) ?? { ...ref, target: 'missing', crossRepo: ref.repo !== null };
            })
                .sort((first, second) => first.index - second.index);
        },
        fetchOriginalPull: (number, repo) => rest.fetchOriginalPull(number, repo),
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

/***/ 421:
/***/ ((module) => {

module.exports = require("node:child_process");

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
/******/ 	var __webpack_exports__ = __nccwpck_require__(516);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;