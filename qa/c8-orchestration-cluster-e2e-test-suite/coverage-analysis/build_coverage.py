#!/usr/bin/env python3
"""
Build OC API v2 test coverage artifacts.

Run from any directory:
    python3 path/to/coverage-analysis/build_coverage.py

The script locates the test directory and the output directory relative to
its own path (`__file__`), so the working directory does not matter.

Scans ../tests/api/v2/**/*.spec.ts and writes outputs next to this script:
  - tests.csv                : per-test labels (file, line, entity, test_name, category,
                                operation, form_step, prerequisite, variants)
  - coverage_matrix.csv      : entity × operation grid, counts per variant
  - coverage_matrix.md       : readable markdown view of the matrix
  - category_breakdown.md    : per-category → per-entity narrative with form,
                                prerequisites, variants, and the test names themselves
  - gaps.md                  : heuristic gap report
"""
import csv
import os
import re
from collections import defaultdict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(SCRIPT_DIR, '..', 'tests', 'api', 'v2')
OUT = SCRIPT_DIR

# ---------- 1. Extract every test() call ----------
# Capture the opening quote (group 1) and the title up to the matching quote
# (group 2). Supports escaped quotes (\') so test titles with embedded ' or "
# are not truncated.
TEST_RE = re.compile(
    r"""(?m)^[ \t]*test(?:\.(?:skip|only|fixme|fail))?\s*\(\s*(['"`])((?:\\.|(?!\1).)*)\1"""
)
# Parameterized loops with dynamic names — name is an identifier or
# property access, e.g. `tc.description`.
DYNAMIC_RE = re.compile(
    r"""(?m)^[ \t]*test(?:\.(?:skip|only|fixme|fail))?\s*\(\s*([A-Za-z_][\w.]*)\s*,"""
)

# Entity -> top-level directory (or root-file slug)
def entity_of(path):
    # Use os.sep-aware split so the script works on Windows too.
    parts = os.path.relpath(path, ROOT).split(os.sep)
    if len(parts) > 1:
        return parts[0]
    base = parts[0]
    return re.sub(r'(-api-tests|-api)?\.spec\.ts$', '', base)

# ---------- 2. Category (the §4 buckets from the report) ----------
CRUD_ENTITIES = {
    'user', 'group', 'role', 'tenant', 'mapping-rule', 'authorization',
    'cluster-variables', 'global-task-listener', 'document',
}
MEMBERSHIP_FILE_RE = re.compile(
    # `roles?` mirrors the existing `mapping-rules?` — needed because
    # `tenant-role-api-tests.spec.ts` uses the singular form (the other
    # membership files all use plural).
    r'(group|role|tenant)-(users|clients|mapping-rules?|roles?|groups)-api-tests\.spec\.ts$'
)
DEPLOYMENT_ENTITIES = {
    'resource', 'process-definition', 'decision-definition', 'decision-requirements',
}
OBSERVATION_ENTITIES = {'element-instance', 'variable', 'audit-log'}
MESSAGING_ENTITIES = {'message', 'signal', 'message-subscriptions'}
ENGINE_EVAL_ENTITIES = {'expression', 'conditional'}
SYSTEM_ENTITIES = {
    'authentication', 'cluster', 'license', 'clock', 'usage-metrics', 'optimize',
}

def category_of(path, entity):
    base = os.path.basename(path)
    if MEMBERSHIP_FILE_RE.search(base):
        return 'B. Membership/Association'
    if entity in CRUD_ENTITIES:
        return 'A. Entity Lifecycle (CRUD)'
    if entity in DEPLOYMENT_ENTITIES:
        return 'C. Deployment Lifecycle'
    if entity == 'process-instance':
        return 'D. Process-Instance Lifecycle & Ops'
    if entity == 'batch-operation':
        return 'E. Batch-Operation Lifecycle'
    if entity == 'user-task':
        return 'F. User-Task Lifecycle'
    if entity == 'job':
        return 'G. Job Lifecycle & Stats'
    if entity == 'incident':
        return 'H. Incident Lifecycle'
    if entity == 'decision-instance':
        return 'I. Decision-Instance Lifecycle'
    if entity in OBSERVATION_ENTITIES:
        return 'J/K/L. Observation-only'
    if entity in MESSAGING_ENTITIES:
        return 'M. Messaging/Signals'
    if entity in ENGINE_EVAL_ENTITIES:
        return 'N. Engine Evaluation'
    if entity in SYSTEM_ENTITIES:
        return 'O. System/Admin'
    return 'Z. Uncategorised'

# ---------- 3. Operation (CRUD verb) — first match wins ----------
# Order matters. search/get are checked before update so names like
# "Search Incidents With Error Type Filter" classify as `search`, not
# `update` — even though `error` is a verb in some job-mutation tests.
# `update` keeps action-specific verbs only (no bare `error`/`failure`
# anymore) to avoid swallowing names that merely *mention* an error type.
OP_RULES = [
    ('create',   re.compile(r'\b(create|creating|created|add(ed|s|ing)?|deploy|publish|broadcast|pin|register)\b', re.I)),
    ('delete',   re.compile(r'\b(delete|delet(ed|ing)|remove|removed|removing|unassign|unassigned|unassigning|cancel|cancell(ed|ing)?|reset)\b', re.I)),
    ('search',   re.compile(r'\b(search|sort|filter|pagin|list|listing|statistics)\b', re.I)),
    ('get',      re.compile(r'\b(get|getting|fetch|fetching|retrieve|retrieving|return|returning|read|reading|exists?|existence|by[ -]?id)\b', re.I)),
    ('update',   re.compile(r'\b(update|updat(ed|ing)|assign|assigned|assigning|complete|completed|completing|migrate|modify|modified|modifying|resolve|resolved|resolving|correlate|correlat(ed|ing)|evaluate|evaluat(ed|ing)|fail\s+job|throw\s+error|report\s+error|raise\s+error|resume|suspend)\b', re.I)),
]
def op_of(name):
    for op, pat in OP_RULES:
        if pat.search(name):
            return op
    return 'other'

# ---------- 4. Variant — multi-label allowed, joined by '|' ----------
# Each rule matches descriptive phrases AND the numeric status code, so titles
# that only mention the code (e.g. "Get Document Without Hash 400") still get
# the right variant tag.
VARIANT_RULES = [
    ('unauthorized',         re.compile(r'unauthor|\b401\b', re.I)),
    ('forbidden',            re.compile(r'forbidden|no(t)?[ -]granted|no permission|missing.*permission|without.*permission|\b403\b', re.I)),
    ('not-found',            re.compile(r'not[ -]?found|non[ -]?existing|nonexistent|does not exist|\b404\b', re.I)),
    # `empty` is qualified by an input-noun so happy-path "returns empty result"
    # / "is empty" / "empty response" tests are no longer mislabelled.
    # 4xx codes for bad input: 400 (bad request), 415 (unsupported media type),
    # 422 (unprocessable entity).
    ('bad-request',          re.compile(r'bad request|invalid|missing.*(field|param|body|required)|empty\s+(name|username|body|field|param|required|value|input|argument|id|payload|key)|null .*(field|value)|negative|exceed|too long|too short|\b400\b|\b415\b|\b422\b', re.I)),
    ('conflict',             re.compile(r'conflict|duplicate|already|\b409\b', re.I)),
    ('pagination-sort',      re.compile(r'pagin|sort|page (limit|size)|cursor', re.I)),
    ('filter',               re.compile(r'filter', re.I)),
    ('observe-via-search',   re.compile(r'search', re.I)),
    # `return`/`returns` deliberately excluded — appears in many search
    # tests ("Returns Empty", "returns 400") which observe via search, not GET.
    ('observe-via-get',      re.compile(r'\bget\b|fetch|retrieve', re.I)),
    ('observe-absence',      re.compile(r'(after|once|when).*(delete|remove|cancel)|no longer|absen[ct]|gone|deleted .*not', re.I)),
    ('happy-path',           re.compile(r'success|should (create|get|update|delete|return|fetch|retrieve|search|list|assign|unassign|complete|cancel|publish|broadcast|correlate|evaluate|migrate|modify|resolve|deploy|suspend|resume|pin|reset)', re.I)),
]
def variants_of(name):
    hits = [k for k, pat in VARIANT_RULES if pat.search(name)]
    return '|'.join(hits) if hits else 'unlabeled'

# ---------- 4b. Form step (lifecycle phase) ----------
# Maps the test's role inside the canonical lifecycle FORM:
#   Create Entity → Observe Present (GET) → Observe Present (Search) → Mutate
#   → Delete Entity → Observe Absence → Aggregate (statistics) → Evaluate (stateless)
#
# Negative paths (unauthorized/forbidden/bad-request/conflict/not-found-without-delete)
# are tagged as 'negative-<op>' so the FORM step still records which step is being
# exercised, just on the unhappy path.
def form_step_of(operation, name, variants):
    v = set(variants.split('|')) if variants else set()
    is_404     = 'not-found' in v
    is_happy   = 'happy-path' in v
    is_absence = 'observe-absence' in v
    # 4xx variants always count as errors. A 404 also counts as an error
    # UNLESS the test self-identifies as a happy path (e.g. "Returns Empty
    # - Success" with non-existing filter) or as observe-absence (already
    # has its own form step).
    has_error  = bool(v & {'unauthorized','forbidden','bad-request','conflict'})
    if is_404 and not is_happy and not is_absence:
        has_error = True
    is_stats   = bool(re.search(r'\b(statistics|stat\b|count|metric)', name, re.I))
    is_eval    = bool(re.search(r'\b(evaluate|evaluat(ed|ing))', name, re.I))

    if is_absence:
        return 'observe-absence'
    if is_stats:
        return 'negative-aggregate' if has_error else 'aggregate'
    if is_eval and operation == 'update':
        return 'evaluate'

    if operation == 'create':
        return 'negative-create' if has_error else 'create'
    if operation == 'delete':
        return 'negative-delete' if has_error else 'delete'
    if operation == 'update':
        return 'negative-mutate' if has_error else 'mutate'
    if operation == 'get':
        if is_404 and not is_happy:
            # Treat 404 on GET as observe-absence when the title says so
            # ("after delete", "once removed"), otherwise as a negative-get.
            return 'observe-absence' if re.search(r'(after|once|when).*(delet|remove|cancel)', name, re.I) else 'negative-get'
        return 'negative-get' if has_error else 'observe-present-get'
    if operation == 'search':
        return 'negative-search' if has_error else 'observe-present-search'
    if operation == 'parameterized':
        return 'parameterized'
    return 'other'

# ---------- 4c. Prerequisite — what the test needs set up before it can run ----------
# Mostly entity-driven; a few file-name overrides for sub-resources.
PREREQ_BY_ENTITY = {
    # root-creatable, no prerequisite
    'user': 'none',
    'group': 'none',
    'role': 'none',
    'tenant': 'none',
    'mapping-rule': 'none',
    'authorization': 'owner-entity-or-resource',  # auth always binds a subject + resource
    'cluster-variables': 'none',
    'global-task-listener': 'none',
    'document': 'none',
    'clock': 'none',
    'license': 'none',
    'cluster': 'none',
    'authentication': 'authenticated-user',
    'optimize': 'none',

    # Deploy is the prerequisite for everything in this group
    'resource': 'none',  # resource deploys ARE the prerequisite for others
    'process-definition': 'deployed-process',
    'decision-definition': 'deployed-decision',
    'decision-requirements': 'deployed-drd',

    # Need a running process instance
    'process-instance': 'deployed-process',
    'element-instance': 'running-process-instance',
    'variable': 'running-process-instance',
    'user-task': 'running-process-instance-with-user-task',
    'incident': 'running-process-instance-with-failing-job',
    'job': 'running-process-instance-with-job',
    'batch-operation': 'running-process-instance(s)',

    # Decision instance: evaluation creates the instance
    'decision-instance': 'deployed-decision',

    # Events
    'message': 'deployed-process-with-message-catch-event',
    'signal': 'deployed-process-with-signal-catch-event',
    'message-subscriptions': 'deployed-process-with-message-catch-event',

    # Stateless
    'expression': 'none',
    'conditional': 'none',

    # Observation
    'audit-log': 'any-prior-action',
    'usage-metrics': 'metered-activity',
}
def prerequisite_of(path, entity):
    base = os.path.basename(path)
    # Membership files: parent entity + member entity must already exist
    m = MEMBERSHIP_FILE_RE.search(base)
    if m:
        parent, member = m.group(1), m.group(2)
        member_singular = member.rstrip('s')
        return f'{parent} + {member_singular}'
    return PREREQ_BY_ENTITY.get(entity, 'unknown')

# ---------- 4d. Stateless tag ----------
# A test is "stateless" when the API call doesn't persist anything to the
# engine: submit input, receive a result, nothing left behind. Useful for
# the AI-generator coverage comparison since stateless endpoints have no
# prerequisite/cleanup and are the easiest to generate.
#
# `clock` mutates engine clock state (pin/reset) → not stateless.
# `optimize` tests deploy processes and observe variable export → not
# stateless. `usage-metrics` is a pure read of metered counters.
STATELESS_ENTITIES = {
    'expression', 'conditional',
    'authentication', 'cluster', 'license', 'usage-metrics',
}
def stateless_of(entity):
    return 'yes' if entity in STATELESS_ENTITIES else 'no'

# ---------- 5. Walk + emit CSV ----------
rows = []
for root, dirs, files in os.walk(ROOT):
    # Sort both dirs and files for deterministic traversal — os.walk's
    # order depends on the underlying filesystem and is not stable across
    # platforms. Since the artifacts are checked in as a diffable snapshot,
    # any non-determinism would show up as spurious churn in tests.csv.
    dirs.sort()
    for f in sorted(files):
        if not f.endswith('.spec.ts'):
            continue
        path = os.path.join(root, f)
        with open(path, encoding='utf-8') as fp:
            content = fp.read()
        # Find each test() with literal-string name and its line number.
        # group(1) is the opening quote, group(2) is the captured title.
        # Un-escape backslash-escaped characters (e.g. \", \', \`, \\) so the
        # extracted name matches what Playwright actually sees at runtime.
        for m in TEST_RE.finditer(content):
            name = re.sub(r'\\(.)', r'\1', m.group(2))
            line_no = content.count('\n', 0, m.start()) + 1
            ent = entity_of(path)
            op = op_of(name)
            variants = variants_of(name)
            rows.append({
                'file': os.path.relpath(path, ROOT),
                'line': line_no,
                'entity': ent,
                'test_name': name,
                'category': category_of(path, ent),
                'operation': op,
                'form_step': form_step_of(op, name, variants),
                'prerequisite': prerequisite_of(path, ent),
                'stateless': stateless_of(ent),
                'variants': variants,
                'dynamic': '',
            })
        # Find parameterized test() calls (dynamic names) — emit one row per loop
        # so the CSV at least records that the file has parameterized cases.
        for m in DYNAMIC_RE.finditer(content):
            # Skip if this position is also a literal-string match
            literal_at = any(abs(m.start() - mm.start()) < 4 for mm in TEST_RE.finditer(content))
            if literal_at:
                continue
            line_no = content.count('\n', 0, m.start()) + 1
            ent = entity_of(path)
            rows.append({
                'file': os.path.relpath(path, ROOT),
                'line': line_no,
                'entity': ent,
                'test_name': f'<parameterized: {m.group(1)}>',
                'category': category_of(path, ent),
                'operation': 'parameterized',
                'form_step': 'parameterized',
                'prerequisite': prerequisite_of(path, ent),
                'stateless': stateless_of(ent),
                'variants': 'data-driven',
                'dynamic': 'yes',
            })

os.makedirs(OUT, exist_ok=True)
csv_path = os.path.join(OUT, 'tests.csv')
with open(csv_path, 'w', newline='', encoding='utf-8') as fp:
    w = csv.DictWriter(
        fp,
        fieldnames=['file','line','entity','category','operation','form_step','prerequisite','stateless','variants','dynamic','test_name'],
        # repo-wide .editorconfig is end_of_line = lf
        lineterminator='\n',
    )
    w.writeheader()
    w.writerows(rows)
print(f"wrote {csv_path} ({len(rows)} rows)")

# ---------- 6. Coverage matrix: entity × operation, variant counts ----------
# matrix[entity][op] = dict of variant -> count (counts labels)
# tests_per_eop[entity][op] = number of test declarations (counts tests)
# These are kept separate so the matrix `total` reflects test count, not
# label count — a single test can carry multiple variant labels.
matrix = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))
tests_per_eop = defaultdict(lambda: defaultdict(int))
entity_totals = defaultdict(int)
op_set = ['create','get','update','delete','search','other','parameterized']
variant_set = ['happy-path','bad-request','unauthorized','forbidden','not-found','conflict','pagination-sort','filter','observe-absence','data-driven','unlabeled']

for r in rows:
    entity_totals[r['entity']] += 1
    tests_per_eop[r['entity']][r['operation']] += 1
    for v in (r['variants'].split('|') if r['variants'] else ['unlabeled']):
        matrix[r['entity']][r['operation']][v] += 1

# CSV matrix
mat_csv = os.path.join(OUT, 'coverage_matrix.csv')
with open(mat_csv, 'w', newline='', encoding='utf-8') as fp:
    w = csv.writer(fp, lineterminator='\n')  # repo-wide .editorconfig is lf
    w.writerow(['entity','operation','total'] + variant_set)
    for ent in sorted(entity_totals, key=lambda x: -entity_totals[x]):
        for op in op_set:
            total = tests_per_eop[ent].get(op, 0)
            if total == 0:
                continue
            cell = matrix[ent].get(op, {})
            w.writerow([ent, op, total] + [cell.get(v, 0) for v in variant_set])
print(f"wrote {mat_csv}")

# Markdown matrix — one table per entity
md_path = os.path.join(OUT, 'coverage_matrix.md')
with open(md_path, 'w', encoding='utf-8') as fp:
    fp.write('# OC API v2 — Coverage matrix (entity × operation × variant)\n\n')
    fp.write(f'Total test declarations: **{len(rows)}** across **{len(entity_totals)}** entities.\n\n')
    fp.write('`total` counts **test declarations** for (entity, operation). Variant columns count **labels** — a single test can match multiple variants, so variant counts can sum to more than `total`.\n\n')
    fp.write('Legend: ✓ = at least 1, blank = 0.\n\n')
    # Compact "presence" matrix first
    fp.write('## At-a-glance presence (✓ = ≥1 test)\n\n')
    header_vars = ['happy','bad-req','401','403','404','conflict','pagin/sort','filter','absence']
    var_keys    = ['happy-path','bad-request','unauthorized','forbidden','not-found','conflict','pagination-sort','filter','observe-absence']
    fp.write('| entity | op | total | ' + ' | '.join(header_vars) + ' |\n')
    fp.write('|--|--|--:|' + '|'.join(['--']*len(header_vars)) + '|\n')
    for ent in sorted(entity_totals, key=lambda x: -entity_totals[x]):
        for op in op_set:
            total = tests_per_eop[ent].get(op, 0)
            if total == 0:
                continue
            cell = matrix[ent].get(op, {})
            marks = ['✓' if cell.get(v,0) > 0 else '' for v in var_keys]
            fp.write(f'| {ent} | {op} | {total} | ' + ' | '.join(marks) + ' |\n')
    fp.write('\n## Counts per cell\n\n')
    fp.write('| entity | op | total | ' + ' | '.join(header_vars) + ' |\n')
    fp.write('|--|--|--:|' + '|'.join(['--:']*len(header_vars)) + '|\n')
    for ent in sorted(entity_totals, key=lambda x: -entity_totals[x]):
        for op in op_set:
            total = tests_per_eop[ent].get(op, 0)
            if total == 0:
                continue
            cell = matrix[ent].get(op, {})
            nums = [str(cell.get(v,0)) if cell.get(v,0)>0 else '' for v in var_keys]
            fp.write(f'| {ent} | {op} | {total} | ' + ' | '.join(nums) + ' |\n')
print(f"wrote {md_path}")

# Gap report: entities that have a 'create' but no 'observe-absence' on delete
gaps_path = os.path.join(OUT, 'gaps.md')
with open(gaps_path, 'w', encoding='utf-8') as fp:
    fp.write('# Coverage gaps (heuristic)\n\n')
    fp.write('## Entities missing delete-then-observe-absence variant\n\n')
    fp.write('Looks for entities with create + delete tests but no `observe-absence` tag.\n\n')
    for ent in sorted(entity_totals):
        cell = matrix[ent]
        has_create = sum(cell.get('create', {}).values()) > 0
        has_delete = sum(cell.get('delete', {}).values()) > 0
        absence_hits = sum(
            cell.get(op, {}).get('observe-absence', 0) for op in op_set
        )
        if has_create and has_delete and absence_hits == 0:
            fp.write(f'- **{ent}** — has create+delete but no "observe absence" test name\n')

    fp.write('\n## Entities with no unauthorized (401) coverage\n\n')
    for ent in sorted(entity_totals):
        cell = matrix[ent]
        if not any(c.get('unauthorized', 0) for c in cell.values()):
            fp.write(f'- {ent}\n')

    fp.write('\n## Entities with no bad-request (400) coverage\n\n')
    for ent in sorted(entity_totals):
        cell = matrix[ent]
        if not any(c.get('bad-request', 0) for c in cell.values()):
            fp.write(f'- {ent}\n')
print(f"wrote {gaps_path}")

# ---------- 7. Per-category breakdown ----------
# For each category, group tests by entity, then by form_step, and emit:
#   - canonical Form (sequence of steps)
#   - Prerequisite to create
#   - Observation channel split (GET vs Search)
#   - Variants with counts
#   - The actual test names with file:line

CANONICAL_FORM = {
    'A. Entity Lifecycle (CRUD)':
        'Create Entity → Get Entity (Observe Present) → Update Entity → Search Entity (Observe via list) → Delete Entity → Get Entity (Observe Absence)',
    'B. Membership/Association':
        'Create parent + member (prerequisite) → Assign member → Search members (Observe Present) → Unassign member → Search members (Observe Absence)',
    'C. Deployment Lifecycle':
        'Deploy resource → Get definition (XML/JSON) → Search definitions (Observe Present) → Delete resource → Get definition (Observe Absence)',
    'D. Process-Instance Lifecycle & Ops':
        'Deploy process (prerequisite) → Create instance → Get/Search instance → Cancel/Migrate/Modify/Resolve-incident → Delete → Observe absence. Batch creators wrap N instances per call.',
    'E. Batch-Operation Lifecycle':
        'Create batch (via batch-creating process-instance APIs, prerequisite) → Get batch → Search batch → Search items → Suspend → Cancel',
    'F. User-Task Lifecycle':
        'Deploy process w/ user task (prerequisite) → Create instance → Assign → Update → Search/Get → Get form → Search variables → Complete → Unassign',
    'G. Job Lifecycle & Stats':
        'Deploy process w/ job (prerequisite) → Activate → Complete / Fail / Error / Update → Search jobs → Aggregate (5 statistics endpoints)',
    'H. Incident Lifecycle':
        'Deploy process + failing job (prerequisite) → Incident raised → Get incident → Search → Resolve → Statistics (by definition / by error)',
    'I. Decision-Instance Lifecycle':
        'Deploy DRD/DMN (prerequisite) → Evaluate → Get instance → Search → Delete (single + batch) → Search (Observe Absence)',
    'J/K/L. Observation-only':
        'Perform an action elsewhere (prerequisite) → Get / Search to observe',
    'M. Messaging/Signals':
        'Deploy process with catch event (prerequisite) → Publish/Correlate/Broadcast → Search subscriptions / correlated messages',
    'N. Engine Evaluation':
        'Submit expression / conditional → Receive result (stateless, no entity persisted)',
    'O. System/Admin':
        'Read system state (auth, license, cluster, clock, metrics) or perform admin action (pin/reset clock)',
}

cat_path = os.path.join(OUT, 'category_breakdown.md')
with open(cat_path, 'w', encoding='utf-8') as fp:
    fp.write('# OC API v2 — Per-category breakdown\n\n')
    fp.write(f'Total test declarations: **{len(rows)}** across **{len(entity_totals)}** entities.\n\n')
    fp.write('This file answers, per category: **(1) Form** (the canonical sequence the tests embody), '
             '**(2) Prerequisite to create**, **(3) Observation channel split** (GET vs search), '
             '**(4) Variants with counts**, **(5) The actual tests in that category**.\n\n')

    # Group rows
    by_cat = defaultdict(list)
    for r in rows:
        by_cat[r['category']].append(r)

    cat_order = [
        'A. Entity Lifecycle (CRUD)',
        'B. Membership/Association',
        'C. Deployment Lifecycle',
        'D. Process-Instance Lifecycle & Ops',
        'E. Batch-Operation Lifecycle',
        'F. User-Task Lifecycle',
        'G. Job Lifecycle & Stats',
        'H. Incident Lifecycle',
        'I. Decision-Instance Lifecycle',
        'J/K/L. Observation-only',
        'M. Messaging/Signals',
        'N. Engine Evaluation',
        'O. System/Admin',
        'Z. Uncategorised',
    ]
    form_step_order = [
        'create', 'observe-present-get', 'observe-present-search', 'mutate',
        'delete', 'observe-absence', 'aggregate', 'evaluate',
        'negative-create', 'negative-get', 'negative-search', 'negative-mutate',
        'negative-delete', 'negative-aggregate', 'parameterized', 'other',
    ]
    variant_order = [
        'happy-path','observe-via-get','observe-via-search','observe-absence',
        'pagination-sort','filter',
        'bad-request','unauthorized','forbidden','not-found','conflict',
        'data-driven','unlabeled',
    ]

    fp.write('## Table of contents\n\n')
    for cat in cat_order:
        if cat in by_cat:
            fp.write(f'- [{cat}](#{cat.lower().replace(". ","-").replace(" ","-").replace("/","").replace("(","").replace(")","").replace("&","").replace(",","")}) — {len(by_cat[cat])} tests\n')
    fp.write('\n')

    for cat in cat_order:
        if cat not in by_cat:
            continue
        cat_rows = by_cat[cat]
        fp.write(f'## {cat}\n\n')
        fp.write(f'**Form**: {CANONICAL_FORM.get(cat, "(no canonical form)")}\n\n')
        fp.write(f'**Total tests**: {len(cat_rows)}\n\n')

        # Group by entity
        by_ent = defaultdict(list)
        for r in cat_rows:
            by_ent[r['entity']].append(r)

        for ent in sorted(by_ent, key=lambda x: -len(by_ent[x])):
            ent_rows = by_ent[ent]
            # Prerequisites (collapse if all the same)
            prereqs = sorted({r['prerequisite'] for r in ent_rows})
            prereq_str = ', '.join(prereqs)

            # Form-step counts
            step_counts = defaultdict(int)
            for r in ent_rows:
                step_counts[r['form_step']] += 1

            # Observation channel split
            obs_get    = sum(1 for r in ent_rows if 'observe-via-get'    in r['variants'].split('|'))
            obs_search = sum(1 for r in ent_rows if 'observe-via-search' in r['variants'].split('|'))

            # Variant counts (multi-label)
            var_counts = defaultdict(int)
            for r in ent_rows:
                for v in r['variants'].split('|'):
                    if v: var_counts[v] += 1

            # Files used
            files = sorted({r['file'] for r in ent_rows})

            fp.write(f'### `{ent}` — {len(ent_rows)} tests\n\n')
            fp.write(f'- **Prerequisite to create**: {prereq_str}\n')
            fp.write(f'- **Files**: {", ".join(f"`{f}`" for f in files)}\n')
            fp.write(f'- **Observation channel**: GET = {obs_get}, Search = {obs_search}\n')

            step_line = ', '.join(f'{s}={step_counts[s]}' for s in form_step_order if step_counts.get(s,0))
            fp.write(f'- **Form-step counts**: {step_line}\n')

            var_line = ', '.join(f'{v}={var_counts[v]}' for v in variant_order if var_counts.get(v,0))
            fp.write(f'- **Variants**: {var_line}\n\n')

            # Tests table, grouped by form_step
            fp.write('| form step | variants | file:line | test name |\n')
            fp.write('|--|--|--|--|\n')
            # Sort: form-step order, then file, then line
            step_idx = {s: i for i, s in enumerate(form_step_order)}
            sorted_rows = sorted(
                ent_rows,
                key=lambda r: (step_idx.get(r['form_step'], 999), r['file'], r['line']),
            )
            for r in sorted_rows:
                # Trim noisy `observe-via-*` from the variant column when the form step already implies it
                display_vars = [v for v in r['variants'].split('|') if v]
                if r['form_step'] in ('observe-present-get','negative-get','observe-absence'):
                    display_vars = [v for v in display_vars if v != 'observe-via-get']
                if r['form_step'] in ('observe-present-search','negative-search'):
                    display_vars = [v for v in display_vars if v != 'observe-via-search']
                fp.write(f'| {r["form_step"]} | {", ".join(display_vars) or "—"} | '
                         f'`{r["file"]}:{r["line"]}` | {r["test_name"]} |\n')
            fp.write('\n')

print(f"wrote {cat_path}")
