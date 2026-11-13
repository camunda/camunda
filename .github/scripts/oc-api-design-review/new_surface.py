#!/usr/bin/env python3
"""List the contract surface a pull request adds to the OC REST API spec.

    python3 new_surface.py --worktree <dir> --out <new-surface.md>

<dir> is a checkout of the pull request merge commit: HEAD^1 is the base branch tip and HEAD^2 the
pull request head. The script parses the spec files at both and writes the operations, schemas,
properties, parameters, response codes and enum values that exist at the head and not at the base,
each with its `path:line` in the head file. Without <dir> it writes a note, and on any other failure
no file.
"""

import argparse
import os
import subprocess

from ruamel.yaml import YAML

SPEC_DIR = "zeebe/gateway-protocol/src/main/proto/v2"
HTTP_METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
KINDS = {
    "operations": "Operations",
    "schemas": "Schemas",
    "properties": "Properties",
    "parameters": "Parameters",
    "responses": "Response codes",
    "enums": "Enum values",
}


def git(worktree, *args):
    return subprocess.run(
        ["git", "-C", worktree, *args], check=True, capture_output=True, encoding="utf-8"
    ).stdout


def mapping(node, key):
    value = node.get(key) if isinstance(node, dict) else None
    return value if isinstance(value, dict) else {}


def sequence(node, key):
    value = node.get(key) if isinstance(node, dict) else None
    return value if isinstance(value, list) else []


def describe(param):
    if "$ref" in param:
        return f"$ref {param['$ref']}"
    return f"{param.get('name')} ({param.get('in')})"


def where(trail):
    if trail[:2] == ["components", "schemas"]:
        trail = trail[2:]
    elif trail[:1] == ["paths"] and len(trail) > 2:
        trail = [f"{trail[2].upper()} {trail[1]}"] + trail[3:]
    return ".".join(t for t in trail if t != "properties").replace(".[", "[")


def walk_enums(node, trail, found, path):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "enum" and isinstance(value, list):
                for i, member in enumerate(value):
                    found["enums"].setdefault(
                        (where(trail), str(member)),
                        (path, value.lc.item(i)[0] + 1, f"`{member}` on `{where(trail)}`"),
                    )
            walk_enums(value, trail + [str(key)], found, path)
    elif isinstance(node, list):
        for i, item in enumerate(node):
            name = item.get("name") if isinstance(item, dict) else None
            walk_enums(item, trail + [f"[{name if isinstance(name, str) else i}]"], found, path)


def walk_schema(node, trail, found, path):
    """Records the properties of a schema at any depth: under properties, items,
    additionalProperties and not, and in allOf, oneOf and anyOf members. A composition member
    adds <keyword>[<index>] to the identity, so oneOf[0] and oneOf[1] can each have a property
    of the same name and one that is new in a single member is listed. A $ref is not followed:
    the schema it points to is walked where it is defined."""
    if not isinstance(node, dict):
        return
    props = mapping(node, "properties")
    for prop, child in props.items():
        identity = trail + (str(prop),)
        text = ".".join(identity).replace(".[]", "[]")
        found["properties"].setdefault(identity, (path, props.lc.key(prop)[0] + 1, f"`{text}`"))
        walk_schema(child, identity, found, path)
    walk_schema(node.get("items"), trail + ("[]",), found, path)
    walk_schema(node.get("additionalProperties"), trail + ("*",), found, path)
    walk_schema(node.get("not"), trail, found, path)
    for keyword in ("allOf", "oneOf", "anyOf"):
        for i, member in enumerate(sequence(node, keyword)):
            walk_schema(member, trail + (f"{keyword}[{i}]",), found, path)


def collect(worktree, ref):
    """Maps each kind to {identity: (file, line, text)} for the spec files at ref. It reads the
    operations with their inline schemas and the components. A reusable parameter, response
    or request body is read where it is defined, because an operation only references it."""
    found = {kind: {} for kind in KINDS}
    listing = git(worktree, "ls-tree", "-z", "--name-only", ref, SPEC_DIR + "/")
    for path in sorted(p for p in listing.split("\0") if p.endswith(".yaml")):
        doc = YAML(typ="rt").load(git(worktree, "show", f"{ref}:{path}"))
        if not isinstance(doc, dict):
            continue
        for route, item in mapping(doc, "paths").items():
            for method, op in item.items() if isinstance(item, dict) else []:
                if method not in HTTP_METHODS or not isinstance(op, dict):
                    continue
                label = f"{method.upper()} {route}"
                text = f"`{label}`" + (f" ({op['operationId']})" if "operationId" in op else "")
                found["operations"].setdefault(label, (path, item.lc.key(method)[0] + 1, text))
                for owner in (item, op):
                    params = sequence(owner, "parameters")
                    for i, param in enumerate(params):
                        if not isinstance(param, dict):
                            continue
                        desc = describe(param)
                        found["parameters"].setdefault(
                            (label, desc),
                            (path, params.lc.item(i)[0] + 1, f"`{desc}` on `{label}`"),
                        )
                        name = param.get("name")
                        root = (f"{label} parameters.{name if isinstance(name, str) else i}",)
                        walk_schema(param.get("schema"), root, found, path)
                for media in mapping(mapping(op, "requestBody"), "content").values():
                    walk_schema(mapping(media, "schema"), (f"{label} requestBody",), found, path)
                responses = mapping(op, "responses")
                for code, response in responses.items():
                    found["responses"].setdefault(
                        (label, str(code)),
                        (path, responses.lc.key(code)[0] + 1, f"`{code}` on `{label}`"),
                    )
                    for media in mapping(response, "content").values():
                        walk_schema(mapping(media, "schema"), (f"{label} responses.{code}",), found, path)
        components = mapping(doc, "components")
        schemas = mapping(components, "schemas")
        for name, schema in schemas.items():
            found["schemas"].setdefault(str(name), (path, schemas.lc.key(name)[0] + 1, f"`{name}`"))
            walk_schema(schema, (str(name),), found, path)
        reusable = mapping(components, "parameters")
        for name, param in reusable.items():
            if not isinstance(param, dict):
                continue
            desc = f"{name}: {describe(param)}"
            found["parameters"].setdefault(
                ("components.parameters", desc),
                (path, reusable.lc.key(name)[0] + 1, f"`{desc}` on `components.parameters`"),
            )
            walk_schema(param.get("schema"), ("parameters", str(name), "schema"), found, path)
        for section in ("responses", "requestBodies"):
            reusable = mapping(components, section)
            for name, body in reusable.items():
                identity = f"{section}.{name}"
                found["schemas"].setdefault(
                    identity, (path, reusable.lc.key(name)[0] + 1, f"`{identity}`")
                )
                for media in mapping(body, "content").values():
                    walk_schema(mapping(media, "schema"), (section, str(name)), found, path)
        walk_enums(doc, [], found, path)
    return found


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--worktree", required=True, help="checkout of the pull request merge commit")
    parser.add_argument("--out", required=True, help="the Markdown file to write")
    args = parser.parse_args(argv)

    if not os.path.isdir(args.worktree):
        with open(args.out, "w") as out:
            out.write("No list of added elements: the merge commit checkout is not available.\n")
        return

    base = collect(args.worktree, "HEAD^1")
    head = collect(args.worktree, "HEAD^2")
    added = {
        kind: sorted(entry for ident, entry in head[kind].items() if ident not in base[kind])
        for kind in KINDS
    }
    counts = ", ".join(f"{label.lower()} {len(added[kind])}" for kind, label in KINDS.items())
    lines = [
        "# New contract surface of this pull request",
        "",
        "Elements that exist at the pull request head and not at its base, found by comparing the",
        "parsed spec files (`path:line` is the line in the head file, as in `changed-lines.txt`).",
        "Check every item against the guideline sections that govern it. Changes to existing",
        "elements, such as descriptions, types, `required` and `nullable`, are not listed: read the",
        "diff for those.",
        "",
        f"Counts: {counts}.",
    ]
    for kind, label in KINDS.items():
        lines += ["", f"## {label} ({len(added[kind])})", ""]
        lines += [f"- {path}:{line}: {text}" for path, line, text in added[kind]] or ["None."]
    with open(args.out, "w") as out:
        out.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
