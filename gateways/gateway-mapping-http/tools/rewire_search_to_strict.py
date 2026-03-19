#!/usr/bin/env python3
"""
Step 1: Rewire Default*ServiceAdapter files to use strict search query methods
directly, removing the ProtocolBridge hop for search queries.

For each file:
- Find pattern: ProtocolBridge.toProtocol(xxxStrict, XxxType.class) -> var xxx
- Then: SearchQueryRequestMapper.toYyyQuery(xxx) or .toYyyMemberQuery(xxx)
- Replace with: GeneratedSearchQueryRequestMapper.toYyyQueryStrict(xxxStrict)
- Remove the bridge variable declaration
- Update imports
"""

import os
import re
import glob
import json
import sys

ADAPTER_DIR = os.path.expanduser(
    "~/workspace/camunda/zeebe/gateway-rest/src/main/java/"
    "io/camunda/zeebe/gateway/rest/controller/generated"
)

# Maps: SearchQueryRequestMapper method -> GeneratedSearchQueryRequestMapper strict method
# For overloaded methods (like toRoleMemberQuery which handles Role/Group/Client),
# we need the strict method name which differs per variant.
STRICT_METHOD_MAP = {
    "toProcessInstanceQuery": "toProcessInstanceQueryStrict",
    "toJobQuery": "toJobQueryStrict",
    "toRoleQuery": "toRoleQueryStrict",
    "toGroupQuery": "toGroupQueryStrict",
    "toTenantQuery": "toTenantQueryStrict",
    "toMappingRuleQuery": "toMappingRuleQueryStrict",
    "toDecisionDefinitionQuery": "toDecisionDefinitionQueryStrict",
    "toDecisionRequirementsQuery": "toDecisionRequirementsQueryStrict",
    "toElementInstanceQuery": "toElementInstanceQueryStrict",
    "toDecisionInstanceQuery": "toDecisionInstanceQueryStrict",
    "toUserTaskQuery": "toUserTaskQueryStrict",
    "toUserTaskVariableQuery": "toUserTaskVariableQueryStrict",
    "toVariableQuery": "toVariableQueryStrict",
    "toClusterVariableQuery": "toClusterVariableQueryStrict",
    "toUserQuery": "toUserQueryStrict",
    "toIncidentQuery": "toIncidentQueryStrict",
    "toBatchOperationQuery": "toBatchOperationQueryStrict",
    "toBatchOperationItemQuery": "toBatchOperationItemQueryStrict",
    "toAuthorizationQuery": "toAuthorizationQueryStrict",
    "toAuditLogQuery": "toAuditLogQueryStrict",
    "toUserTaskAuditLogQuery": "toUserTaskAuditLogQueryStrict",
    "toMessageSubscriptionQuery": "toMessageSubscriptionQueryStrict",
    "toCorrelatedMessageSubscriptionQuery": "toCorrelatedMessageSubscriptionQueryStrict",
}

# For overloaded toRoleMemberQuery / toGroupMemberQuery / toTenantMemberQuery,
# we need to determine the strict method based on the type being bridged
MEMBER_QUERY_MAP = {
    # Role member queries
    "RoleUserSearchQueryRequest": "toRoleUserQueryStrict",
    "RoleGroupSearchQueryRequest": "toRoleGroupQueryStrict",
    "RoleClientSearchQueryRequest": "toRoleClientQueryStrict",
    # Group member queries
    "GroupUserSearchQueryRequest": "toGroupUserQueryStrict",
    "GroupClientSearchQueryRequest": "toGroupClientQueryStrict",
    # Tenant member queries
    "TenantGroupSearchQueryRequest": "toTenantGroupQueryStrict",
    "TenantUserSearchQueryRequest": "toTenantUserQueryStrict",
    "TenantClientSearchQueryRequest": "toTenantClientQueryStrict",
}


def process_file(filepath, dry_run=False):
    """Process a single Default*ServiceAdapter file."""
    with open(filepath, "r") as f:
        content = f.read()

    filename = os.path.basename(filepath)
    lines = content.split("\n")
    changes = 0

    # Find all ProtocolBridge.toProtocol lines and their bridge targets
    # Pattern: final XxxType varName = ProtocolBridge.toProtocol(varNameStrict, XxxType.class);
    bridge_pattern = re.compile(
        r"(\s*)final\s+(\w+)\s+(\w+)\s*=\s*ProtocolBridge\.toProtocol\((\w+),\s*(\w+)\.class\);"
    )

    # Find lines to remove and replacements to make
    lines_to_remove = set()
    replacements = []  # (line_idx, old_text, new_text)

    for i, line in enumerate(lines):
        m = bridge_pattern.search(line)
        if not m:
            continue

        indent = m.group(1)
        proto_type = m.group(2)
        bridge_var = m.group(3)  # e.g., 'incidentSearchQuery'
        strict_var = m.group(4)  # e.g., 'incidentSearchQueryStrict'
        bridge_class = m.group(5)  # e.g., 'IncidentSearchQuery'

        # Now look for SearchQueryRequestMapper.toXxxQuery(bridge_var) in following lines
        found_search_call = False
        for j in range(i + 1, min(i + 20, len(lines))):
            # Match: SearchQueryRequestMapper.toXxxQuery(bridge_var)
            sqm_pattern = re.compile(
                r"SearchQueryRequestMapper\.(to\w+Query)\(" + re.escape(bridge_var) + r"\)"
            )
            sqm_match = sqm_pattern.search(lines[j])
            if sqm_match:
                old_method = sqm_match.group(1)

                # Determine the strict method name
                strict_method = None

                # Check if it's a member query with overloads
                if old_method in ("toRoleMemberQuery", "toGroupMemberQuery", "toTenantMemberQuery"):
                    strict_method = MEMBER_QUERY_MAP.get(bridge_class)
                else:
                    strict_method = STRICT_METHOD_MAP.get(old_method)

                if strict_method:
                    # Replace SearchQueryRequestMapper.toXxxQuery(bridge_var)
                    # with GeneratedSearchQueryRequestMapper.toXxxQueryStrict(strict_var)
                    old_call = f"SearchQueryRequestMapper.{old_method}({bridge_var})"
                    new_call = f"GeneratedSearchQueryRequestMapper.{strict_method}({strict_var})"
                    replacements.append((j, old_call, new_call))
                    lines_to_remove.add(i)  # Remove the bridge line
                    found_search_call = True
                    changes += 1
                    break

        if not found_search_call:
            # Not a search query bridge - might be RequestMapper, skip for now
            pass

    if changes == 0:
        return 0

    # Apply replacements (on the lines, not removing yet)
    for line_idx, old_text, new_text in replacements:
        lines[line_idx] = lines[line_idx].replace(old_text, new_text)

    # Remove bridge lines (in reverse order to maintain indices)
    for idx in sorted(lines_to_remove, reverse=True):
        del lines[idx]

    # Update imports
    new_content = "\n".join(lines)

    # Add GeneratedSearchQueryRequestMapper import if not present
    gen_import = "import io.camunda.gateway.mapping.http.search.GeneratedSearchQueryRequestMapper;"
    if gen_import not in new_content:
        # Find a good place to insert - after the last search import
        search_import_pattern = r"(import io\.camunda\.gateway\.mapping\.http\.search\.[^;]+;)"
        last_search_import = None
        for m in re.finditer(search_import_pattern, new_content):
            last_search_import = m
        if last_search_import:
            pos = last_search_import.end()
            new_content = new_content[:pos] + "\n" + gen_import + new_content[pos:]
        else:
            # Insert after first import
            first_import = re.search(r"(import [^;]+;)", new_content)
            if first_import:
                pos = first_import.end()
                new_content = new_content[:pos] + "\n" + gen_import + new_content[pos:]

    # Remove protocol model imports that are no longer used
    # Check each protocol model import - if the type name is no longer in the file body
    protocol_import_pattern = re.compile(
        r"import io\.camunda\.gateway\.protocol\.model\.(\w+);"
    )
    lines_final = new_content.split("\n")
    body_start = 0
    for idx, line in enumerate(lines_final):
        if line.startswith("public class") or line.startswith("public interface"):
            body_start = idx
            break

    body_text = "\n".join(lines_final[body_start:])
    imports_to_remove = []
    for idx, line in enumerate(lines_final):
        m = protocol_import_pattern.match(line.strip())
        if m:
            type_name = m.group(1)
            # Check if this type is still referenced in the body
            if re.search(r"\b" + re.escape(type_name) + r"\b", body_text) is None:
                imports_to_remove.append(idx)

    # Also check if ProtocolBridge import can be removed
    if "ProtocolBridge" not in body_text:
        for idx, line in enumerate(lines_final):
            if "import io.camunda.gateway.mapping.http.ProtocolBridge;" in line:
                imports_to_remove.append(idx)

    # Also check if SearchQueryRequestMapper import can be removed
    if "SearchQueryRequestMapper" not in body_text:
        for idx, line in enumerate(lines_final):
            if "import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;" in line:
                imports_to_remove.append(idx)

    for idx in sorted(set(imports_to_remove), reverse=True):
        del lines_final[idx]

    new_content = "\n".join(lines_final)

    if not dry_run:
        with open(filepath, "w") as f:
            f.write(new_content)

    print(f"  {filename}: {changes} search calls rewired to strict")
    return changes


def main():
    dry_run = "--dry-run" in sys.argv
    print("Step 1: Rewire search query calls to strict methods\n")

    adapter_files = sorted(
        glob.glob(os.path.join(ADAPTER_DIR, "Default*ServiceAdapter.java"))
    )
    print(f"Found {len(adapter_files)} files\n")

    total = 0
    files_changed = 0
    for f in adapter_files:
        count = process_file(f, dry_run=dry_run)
        if count > 0:
            total += count
            files_changed += 1

    print(f"\n{'[DRY RUN] ' if dry_run else ''}Summary:")
    print(f"  Files changed: {files_changed}/{len(adapter_files)}")
    print(f"  Search calls rewired: {total}")


if __name__ == "__main__":
    main()
