#!/usr/bin/env python3
"""
Generate strict-contract filter mapper overloads for SearchQueryFilterMapper.

For each filter mapper method used by REQUEST_ENTRIES, generates an overload that
accepts the strict contract type. The method body stays the same — only the parameter
type changes and bean getters (filter.getXxx()) become record accessors (filter.xxx()).

Output: prints the overload methods and needed imports to stdout.
"""
import re, os, sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILTER_MAPPER = os.path.join(BASE, "src/main/java/io/camunda/gateway/mapping/http/search/SearchQueryFilterMapper.java")
GENERATED_DIR = os.path.join(BASE, "src/main/java/io/camunda/gateway/mapping/http/search/contract/generated")
TARGET_PACKAGE = "io.camunda.gateway.mapping.http.search.contract.generated"

# Build map: schema-name → strict contract class name
STRICT_MAP = {}
for f in os.listdir(GENERATED_DIR):
    if not f.startswith("Generated") or not f.endswith(".java"):
        continue
    classname = f[:-5]  # strip .java
    if "StrictContract" not in classname:
        continue
    schema = classname[len("Generated"):-len("StrictContract")]
    STRICT_MAP[schema] = classname

# Map from protocol model filter simple name → strict contract class name
OVERRIDES = {
    "ProcessInstanceFilter": "GeneratedProcessInstanceFilterStrictContract",
    "ProcessInstanceFilterFields": "GeneratedProcessInstanceFilterFieldsStrictContract",
    "ClusterVariableSearchQueryFilterRequest": STRICT_MAP.get("ClusterVariableSearchQueryFilterRequest"),
}

# Protocol model FQN method refs → strict contract replacements
PROTOCOL_ENUM_REPLACEMENTS = {
    "io.camunda.gateway.protocol.model.AuditLogActorTypeEnum::getValue":
        "GeneratedAuditLogActorTypeEnum::getValue",
}

def get_strict(proto_simple):
    if proto_simple in OVERRIDES:
        return OVERRIDES[proto_simple]
    return STRICT_MAP.get(proto_simple)


def extract_methods(content):
    """Extract single-param toXxxFilter/toXxxFilterFields methods with full body."""
    methods = []
    lines = content.split('\n')
    i = 0
    while i < len(lines):
        line = lines[i]

        # Single-line signature: static RetType toXxxFilter(ParamType filter) {
        m1 = re.match(
            r'^  ((?:public )?static\s+)([\w.<>, ]+)\s+(to\w+Filter(?:Fields)?)\s*\(\s*(?:final\s+)?([\w.]+)\s+filter\s*\)\s*\{',
            line
        )
        # Multi-line signature: static RetType toXxxFilter(
        m2 = re.match(
            r'^  ((?:public )?static\s+)([\w.<>, ]+)\s+(to\w+Filter(?:Fields)?)\s*\(\s*$',
            line
        )

        method_name = ret_type = param_type = None
        body_start = None

        if m1:
            ret_type, method_name, param_type = m1.group(2), m1.group(3), m1.group(4)
            body_start = i
        elif m2:
            ret_type, method_name = m2.group(2), m2.group(3)
            if i + 1 < len(lines):
                pm = re.match(r'\s+(?:final\s+)?([\w.]+)\s+filter\s*\)\s*\{', lines[i + 1])
                if pm:
                    param_type = pm.group(1)
                    body_start = i

        if body_start is not None and method_name and param_type:
            brace_count = 0
            body_lines = []
            j = body_start
            while j < len(lines):
                body_lines.append(lines[j])
                brace_count += lines[j].count('{') - lines[j].count('}')
                if brace_count == 0 and j > body_start:
                    break
                j += 1
            methods.append({
                'name': method_name,
                'ret_type': ret_type,
                'param_type': param_type,
                'param_simple': param_type.split('.')[-1],
                'body_lines': body_lines,
            })
            i = j + 1
            continue
        i += 1
    return methods


# Methods that need hand-written overloads (skipped by script)
MANUAL_METHODS = {"toElementInstanceFilter"}

def replace_getter(m):
    """Convert x.getXxx() to x.xxx() — handles $ prefix too."""
    var = m.group(1)
    field = m.group(2)
    if field.startswith('$'):
        # $Or → $or
        accessor = '$' + field[1].lower() + field[2:]
    else:
        accessor = field[0].lower() + field[1:]
    return f"{var}.{accessor}()"


def transform_method(method, strict_class):
    """Transform a method to accept the strict contract type."""
    lines = list(method['body_lines'])
    param_fqn = method['param_type']
    param_simple = method['param_simple']

    new_lines = []
    is_signature = True  # first line(s) are the signature

    for idx, line in enumerate(lines):
        new_line = line

        # Replace param type ONLY in the parameter declaration, not in method name
        if is_signature:
            # Match the parameter declaration pattern: "final ParamType filter" or "ParamType filter"
            if param_fqn in new_line:
                # Replace FQN in parameter declaration only
                new_line = re.sub(
                    r'(?:final\s+)?' + re.escape(param_fqn) + r'(\s+filter)',
                    f'final {strict_class}\\1',
                    new_line
                )
            elif param_simple in new_line and 'filter' in new_line:
                # Replace simple name in parameter declaration only
                new_line = re.sub(
                    r'(?:final\s+)?' + re.escape(param_simple) + r'(\s+filter)',
                    f'final {strict_class}\\1',
                    new_line
                )
            # Signature ends when we see the opening brace
            if '{' in new_line:
                is_signature = False
        else:
            # In the method body: replace protocol model enum FQN references
            for old_ref, new_ref in PROTOCOL_ENUM_REPLACEMENTS.items():
                if old_ref in new_line:
                    new_line = new_line.replace(old_ref, new_ref)

            # In the method body: replace protocol model class references
            # in for-loop type declarations and similar contexts
            # e.g., "final ProcessInstanceFilterFields or" → "final GeneratedProcessInstanceFilterFieldsStrictContract or"
            if param_simple == "ProcessInstanceFilter" and "ProcessInstanceFilterFields" in new_line:
                # Only replace standalone ProcessInstanceFilterFields as a type
                new_line = re.sub(
                    r'\bProcessInstanceFilterFields\b',
                    'GeneratedProcessInstanceFilterFieldsStrictContract',
                    new_line
                )
            # Don't replace other occurrences of the param type in the body
            # (they refer to internal method calls, type casts, etc.)

        # Transform bean-style getters to record accessors
        # Handles filter.getXxx(), f.getXxx(), and filter.get$Or()
        new_line = re.sub(r'(filter|f)\.get(\$?\w+)\(\)', replace_getter, new_line)

        new_lines.append(new_line)

    return '\n'.join(new_lines)


def main():
    with open(FILTER_MAPPER) as f:
        content = f.read()

    methods = extract_methods(content)

    # Identify used methods from generator
    with open(os.path.join(BASE, "tools/GenerateContractMappingPoc.java")) as f:
        gen = f.read()
    used = set(re.findall(r'SearchQueryFilterMapper\.(to\w+Filter)\(', gen))
    # Also include toProcessInstanceFilterFields (called by toProcessInstanceFilter)
    used.add("toProcessInstanceFilterFields")

    generated = []
    skipped = []
    for m in methods:
        if m['name'] not in used:
            continue
        if m['name'] in MANUAL_METHODS:
            skipped.append((m['name'], m['param_simple'], 'MANUAL'))
            continue
        strict = get_strict(m['param_simple'])
        if strict is None:
            skipped.append((m['name'], m['param_simple'], 'NO_CONTRACT'))
            continue
        code = transform_method(m, strict)
        generated.append({'name': m['name'], 'strict': strict, 'code': code})

    # Print summary
    print(f"// {len(generated)} strict overloads generated, {len(skipped)} skipped", file=sys.stderr)
    for name, param, reason in skipped:
        print(f"//   SKIP {name} ({param}) [{reason}]", file=sys.stderr)

    # Print overloads
    for g in generated:
        print()
        print(f"  // Strict-contract overload for {g['name']}")
        print(g['code'])

    # Print imports
    print("\n  // IMPORTS NEEDED:", file=sys.stderr)
    imports = sorted(set(f"import {TARGET_PACKAGE}.{g['strict']};" for g in generated))
    for imp in imports:
        print(f"  {imp}", file=sys.stderr)


if __name__ == "__main__":
    main()
