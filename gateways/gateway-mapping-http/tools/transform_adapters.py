#!/usr/bin/env python3
"""
Transform Default*ServiceAdapter files to use strict contracts instead of protocol models.

Strategy:
1. For each method param that uses a protocol model type with a strict contract equivalent:
   - Change the param type from ProtocolModel to GeneratedXxxStrictContract
   - Rename the param from 'xxx' to 'xxxStrict'
   - Insert a bridge variable after the method's opening '{':
     final ProtocolModel xxx = ProtocolBridge.toProtocol(xxxStrict, ProtocolModel.class);
2. Add strict contract imports and ProtocolBridge import
3. Keep protocol model imports (still needed for bridge variable declarations)
"""

import os
import re
import glob
import sys

# Paths
ADAPTER_DIR = os.path.expanduser(
    "~/workspace/camunda/zeebe/gateway-rest/src/main/java/"
    "io/camunda/zeebe/gateway/rest/controller/generated"
)
CONTRACT_DIR = os.path.expanduser(
    "~/workspace/camunda/gateways/gateway-mapping-http/src/main/java/"
    "io/camunda/gateway/mapping/http/search/contract/generated"
)

PROTOCOL_PACKAGE = "io.camunda.gateway.protocol.model"
CONTRACT_PACKAGE = "io.camunda.gateway.mapping.http.search.contract.generated"
BRIDGE_IMPORT = "import io.camunda.gateway.mapping.http.ProtocolBridge;"

# The 3 polymorphic oneOf types that DON'T have strict contracts
EXCLUDED_TYPES = {
    "AuthorizationRequest",
    "DecisionEvaluationInstruction",
    "ProcessInstanceCreationInstruction",
}


def build_type_mapping():
    """Build mapping from protocol model type name -> strict contract type name."""
    proto_to_contract = {}
    for f in glob.glob(os.path.join(CONTRACT_DIR, "Generated*StrictContract.java")):
        contract_name = os.path.basename(f).replace(".java", "")
        # Extract schema name: GeneratedXxxStrictContract -> Xxx
        if contract_name.startswith("Generated") and contract_name.endswith(
            "StrictContract"
        ):
            schema_name = contract_name[len("Generated") : -len("StrictContract")]
            if schema_name not in EXCLUDED_TYPES:
                proto_to_contract[schema_name] = contract_name
    return proto_to_contract


def extract_protocol_imports(lines):
    """Extract protocol model type names from import statements."""
    imports = {}
    for i, line in enumerate(lines):
        m = re.match(
            r"\s*import\s+" + re.escape(PROTOCOL_PACKAGE) + r"\.(\w+)\s*;", line
        )
        if m:
            imports[m.group(1)] = i
    return imports


def find_method_params_to_replace(lines, replaceable_types):
    """
    Find method parameters that use replaceable protocol model types.
    Returns list of (line_index, proto_type, var_name) tuples.
    """
    params = []
    for i, line in enumerate(lines):
        for proto_type in replaceable_types:
            # Match: final ProtoType varName   (in method signatures)
            pattern = rf"\bfinal\s+{re.escape(proto_type)}\s+(\w+)"
            m = re.search(pattern, line)
            if m:
                var_name = m.group(1)
                params.append((i, proto_type, var_name))
    return params


def find_method_body_start(lines, param_line_idx):
    """
    Find the line index of the opening '{' for the method containing the given param.
    Also returns the indentation of the method body.
    """
    for i in range(param_line_idx, min(param_line_idx + 15, len(lines))):
        line = lines[i]
        if re.search(r"\)\s*\{", line):
            # Found the ') {' - body starts on next line
            # Determine body indentation from next non-empty line
            if i + 1 < len(lines):
                next_line = lines[i + 1]
                indent_match = re.match(r"^(\s+)", next_line)
                indent = indent_match.group(1) if indent_match else "    "
            else:
                indent = "    "
            return i, indent
    return None, None


def transform_file(filepath, proto_to_contract, dry_run=False):
    """Transform a single Default*ServiceAdapter file."""
    with open(filepath, "r") as f:
        content = f.read()

    lines = content.split("\n")
    filename = os.path.basename(filepath)

    # Step 1: Find protocol model imports that have strict contract equivalents
    proto_imports = extract_protocol_imports(lines)
    replaceable_types = {
        pt: proto_to_contract[pt]
        for pt in proto_imports
        if pt in proto_to_contract
    }

    if not replaceable_types:
        print(f"  {filename}: no replaceable types found, skipping")
        return 0

    # Step 2: Find all method params that need replacement
    params_to_replace = find_method_params_to_replace(lines, replaceable_types)
    if not params_to_replace:
        print(f"  {filename}: no method params to replace, skipping")
        return 0

    print(
        f"  {filename}: {len(params_to_replace)} params to bridge across {len(replaceable_types)} types"
    )

    # Step 3: Process params (in reverse order so line indices stay valid)
    # Group params by their method (by finding the method body start for each)
    method_bridges = {}  # method_body_line -> list of (proto_type, var_name, indent)

    for param_line_idx, proto_type, var_name in params_to_replace:
        contract_type = replaceable_types[proto_type]
        new_var_name = var_name + "Strict"

        # Replace the param type and name on this line
        old_text = f"final {proto_type} {var_name}"
        new_text = f"final {contract_type} {new_var_name}"
        lines[param_line_idx] = lines[param_line_idx].replace(old_text, new_text, 1)

        # Find the method body start to insert bridge
        body_line_idx, indent = find_method_body_start(lines, param_line_idx)
        if body_line_idx is not None:
            if body_line_idx not in method_bridges:
                method_bridges[body_line_idx] = []
            method_bridges[body_line_idx].append((proto_type, var_name, new_var_name, indent))

    # Step 4: Insert bridge variable declarations (in reverse order)
    for body_line_idx in sorted(method_bridges.keys(), reverse=True):
        bridges = method_bridges[body_line_idx]
        # Insert bridge lines right after the '{' line
        insert_idx = body_line_idx + 1
        for proto_type, var_name, new_var_name, indent in reversed(bridges):
            bridge_line = (
                f"{indent}final {proto_type} {var_name} = "
                f"ProtocolBridge.toProtocol({new_var_name}, {proto_type}.class);"
            )
            lines.insert(insert_idx, bridge_line)

    # Step 5: Add new imports
    # Find the last import line
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.strip().startswith("import "):
            last_import_idx = i

    # Build new imports
    new_imports = set()
    for proto_type, contract_type in replaceable_types.items():
        # Only add imports for types actually used in params
        if any(pt == proto_type for _, pt, _ in params_to_replace):
            new_imports.add(f"import {CONTRACT_PACKAGE}.{contract_type};")
    new_imports.add(BRIDGE_IMPORT)

    # Remove imports that are already present
    existing_content = "\n".join(lines)
    new_imports = {imp for imp in new_imports if imp not in existing_content}

    if new_imports and last_import_idx >= 0:
        for imp in sorted(new_imports, reverse=True):
            lines.insert(last_import_idx + 1, imp)

    # Step 6: Write back
    new_content = "\n".join(lines)
    if not dry_run:
        with open(filepath, "w") as f:
            f.write(new_content)

    return len(params_to_replace)


def main():
    dry_run = "--dry-run" in sys.argv

    print("Building type mapping...")
    proto_to_contract = build_type_mapping()
    print(f"  Found {len(proto_to_contract)} protocol model -> strict contract mappings")

    print(f"\nScanning Default*ServiceAdapter files in:\n  {ADAPTER_DIR}")
    adapter_files = sorted(
        glob.glob(os.path.join(ADAPTER_DIR, "Default*ServiceAdapter.java"))
    )
    print(f"  Found {len(adapter_files)} files\n")

    total_params = 0
    files_changed = 0

    for filepath in adapter_files:
        count = transform_file(filepath, proto_to_contract, dry_run=dry_run)
        if count > 0:
            total_params += count
            files_changed += 1

    print(f"\n{'[DRY RUN] ' if dry_run else ''}Summary:")
    print(f"  Files changed: {files_changed}/{len(adapter_files)}")
    print(f"  Params bridged: {total_params}")


if __name__ == "__main__":
    main()
