#!/usr/bin/env python3
"""
Build OC API v2 test coverage artifacts.

Run from this script's directory (qa/c8-orchestration-cluster-e2e-test-suite/coverage-analysis/):
    python3 build_coverage.py

Scans ../tests/api/v2/**/*.spec.ts and writes outputs next to this script:
  - tests.csv                : (file, line, entity, test_name, category, operation, variant)
  - coverage_matrix.csv      : entity x operation, counts per variant
  - coverage_matrix.md       : readable markdown view of the matrix
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
TEST_RE = re.compile(
    r"""(?m)^[ \t]*test(?:\.(?:skip|only|fixme|fail))?\s*\(\s*['"`]([^'"`]+)['"`]"""
)
# Parameterized loops with dynamic names
DYNAMIC_RE = re.compile(
    r"""(?m)^[ \t]*test(?:\.(?:skip|only|fixme|fail))?\s*\(\s*([A-Za-z_][\w.]*)\s*,"""
)

# Entity -> top-level directory (or root-file slug)
def entity_of(path):
    parts = os.path.relpath(path, ROOT).split('/')
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
    r'(group|role|tenant)-(users|clients|mapping-rules?|roles|groups)-api-tests\.spec\.ts$'
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
OP_RULES = [
    ('create',   re.compile(r'\b(create|creating|created|add(ed|s|ing)?|deploy|publish|broadcast|pin|register)\b', re.I)),
    ('delete',   re.compile(r'\b(delete|delet(ed|ing)|remove|removed|removing|unassign|unassigned|unassigning|cancel|cancell(ed|ing)?|reset)\b', re.I)),
    ('update',   re.compile(r'\b(update|updat(ed|ing)|assign|assigned|assigning|complete|completed|completing|migrate|modify|modified|modifying|resolve|resolved|resolving|correlate|correlat(ed|ing)|evaluate|evaluat(ed|ing)|fail|failure|error|resume|suspend)\b', re.I)),
    ('search',   re.compile(r'\b(search|sort|filter|pagin|list|listing|statistics)\b', re.I)),
    ('get',      re.compile(r'\b(get|getting|fetch|fetching|retrieve|retrieving|return|returning|read|reading|exists?|existence|by[ -]?id)\b', re.I)),
]
def op_of(name):
    for op, pat in OP_RULES:
        if pat.search(name):
            return op
    return 'other'

# ---------- 4. Variant — multi-label allowed, joined by '|' ----------
VARIANT_RULES = [
    ('unauthorized',         re.compile(r'unauthor', re.I)),
    ('forbidden',            re.compile(r'forbidden|no(t)?[ -]granted|no permission|missing.*permission|without.*permission', re.I)),
    ('not-found',            re.compile(r'not[ -]?found|non[ -]?existing|nonexistent|does not exist', re.I)),
    ('bad-request',          re.compile(r'bad request|invalid|missing.*(field|param|body|required)|empty|null .*(field|value)|negative|exceed|too long|too short', re.I)),
    ('conflict',             re.compile(r'conflict|duplicate|already', re.I)),
    ('pagination-sort',      re.compile(r'pagin|sort|page (limit|size)|cursor', re.I)),
    ('filter',               re.compile(r'filter', re.I)),
    ('observe-via-search',   re.compile(r'search', re.I)),
    ('observe-via-get',      re.compile(r'\bget\b|fetch|retrieve|return', re.I)),
    ('observe-absence',      re.compile(r'(after|once|when).*(delete|remove|cancel)|no longer|absen[ct]|gone|deleted .*not', re.I)),
    ('happy-path',           re.compile(r'success|should (create|get|update|delete|return|fetch|retrieve|search|list|assign|unassign|complete|cancel|publish|broadcast|correlate|evaluate|migrate|modify|resolve|deploy|suspend|resume|pin|reset)', re.I)),
]
def variants_of(name):
    hits = [k for k, pat in VARIANT_RULES if pat.search(name)]
    return '|'.join(hits) if hits else 'unlabeled'

# ---------- 5. Walk + emit CSV ----------
rows = []
for root, _dirs, files in os.walk(ROOT):
    for f in sorted(files):
        if not f.endswith('.spec.ts'):
            continue
        path = os.path.join(root, f)
        with open(path, encoding='utf-8') as fp:
            content = fp.read()
        lines = content.splitlines()
        # Find each test() with literal-string name and its line number
        for m in TEST_RE.finditer(content):
            name = m.group(1)
            line_no = content.count('\n', 0, m.start()) + 1
            ent = entity_of(path)
            rows.append({
                'file': os.path.relpath(path, ROOT),
                'line': line_no,
                'entity': ent,
                'test_name': name,
                'category': category_of(path, ent),
                'operation': op_of(name),
                'variants': variants_of(name),
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
                'variants': 'data-driven',
                'dynamic': 'yes',
            })

os.makedirs(OUT, exist_ok=True)
csv_path = os.path.join(OUT, 'tests.csv')
with open(csv_path, 'w', newline='', encoding='utf-8') as fp:
    w = csv.DictWriter(fp, fieldnames=['file','line','entity','category','operation','variants','dynamic','test_name'])
    w.writeheader()
    w.writerows(rows)
print(f"wrote {csv_path} ({len(rows)} rows)")

# ---------- 6. Coverage matrix: entity × operation, variant counts ----------
# matrix[entity][op] = dict of variant -> count
matrix = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))
entity_totals = defaultdict(int)
op_set = ['create','get','update','delete','search','other','parameterized']
variant_set = ['happy-path','bad-request','unauthorized','forbidden','not-found','conflict','pagination-sort','filter','observe-absence','data-driven','unlabeled']

for r in rows:
    entity_totals[r['entity']] += 1
    for v in (r['variants'].split('|') if r['variants'] else ['unlabeled']):
        if v in variant_set:
            matrix[r['entity']][r['operation']][v] += 1
        else:
            # secondary observation tags — still tally under a known bucket
            matrix[r['entity']][r['operation']][v] = matrix[r['entity']][r['operation']].get(v, 0) + 1

# CSV matrix
mat_csv = os.path.join(OUT, 'coverage_matrix.csv')
with open(mat_csv, 'w', newline='', encoding='utf-8') as fp:
    w = csv.writer(fp)
    w.writerow(['entity','operation','total'] + variant_set)
    for ent in sorted(entity_totals, key=lambda x: -entity_totals[x]):
        for op in op_set:
            cell = matrix[ent].get(op, {})
            total = sum(cell.values())
            if total == 0:
                continue
            w.writerow([ent, op, total] + [cell.get(v, 0) for v in variant_set])
print(f"wrote {mat_csv}")

# Markdown matrix — one table per entity
md_path = os.path.join(OUT, 'coverage_matrix.md')
with open(md_path, 'w', encoding='utf-8') as fp:
    fp.write('# OC API v2 — Coverage matrix (entity × operation × variant)\n\n')
    fp.write(f'Total test declarations: **{len(rows)}** across **{len(entity_totals)}** entities.\n\n')
    fp.write('Variants are first-match labels from test names; one test can carry multiple labels (sum may exceed row total).\n\n')
    fp.write('Legend: ✓ = at least 1, blank = 0.\n\n')
    # Compact "presence" matrix first
    fp.write('## At-a-glance presence (✓ = ≥1 test)\n\n')
    header_vars = ['happy','bad-req','401','403','404','conflict','pagin/sort','filter','absence']
    var_keys    = ['happy-path','bad-request','unauthorized','forbidden','not-found','conflict','pagination-sort','filter','observe-absence']
    fp.write('| entity | op | total | ' + ' | '.join(header_vars) + ' |\n')
    fp.write('|--|--|--:|' + '|'.join(['--']*len(header_vars)) + '|\n')
    for ent in sorted(entity_totals, key=lambda x: -entity_totals[x]):
        for op in op_set:
            cell = matrix[ent].get(op, {})
            total = sum(cell.values())
            if total == 0:
                continue
            marks = ['✓' if cell.get(v,0) > 0 else '' for v in var_keys]
            fp.write(f'| {ent} | {op} | {total} | ' + ' | '.join(marks) + ' |\n')
    fp.write('\n## Counts per cell\n\n')
    fp.write('| entity | op | total | ' + ' | '.join(header_vars) + ' |\n')
    fp.write('|--|--|--:|' + '|'.join(['--:']*len(header_vars)) + '|\n')
    for ent in sorted(entity_totals, key=lambda x: -entity_totals[x]):
        for op in op_set:
            cell = matrix[ent].get(op, {})
            total = sum(cell.values())
            if total == 0:
                continue
            nums = [str(cell.get(v,0)) if cell.get(v,0)>0 else '' for v in var_keys]
            fp.write(f'| {ent} | {op} | {total} | ' + ' | '.join(nums) + ' |\n')
print(f"wrote {md_path}")

# Gap report: entities that have a 'create' but no 'observe-absence' on delete
fp = open(os.path.join(OUT, 'gaps.md'), 'w')
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
fp.close()
print(f"wrote {os.path.join(OUT,'gaps.md')}")
