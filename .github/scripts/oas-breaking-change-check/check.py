#!/usr/bin/env python3
"""Report the OC REST API breaking changes a pull request introduces, relative to the last release.

    python3 check.py --base-ref <ref> --oasdiff <bin> --out-dir <dir>

Run from the repository root of the PR's merge commit (HEAD): HEAD^1 is the PR base. The spec is
diffed against a release tag, never a branch head, so a break merged to the base branch stays
reported against the release instead of silently becoming the new reference point:

    new = ERR(baseline -> head) - ERR(baseline -> base)     (matched by oasdiff fingerprint)

Writes <out-dir>/report.md and <out-dir>/status (`none` | `breaking`) and exits 0 for both. When the
check cannot complete it exits non-zero and writes neither.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterator

from ruamel.yaml import YAML

MARKER = "<!-- oas-breaking-change-check -->"
PROTO_DIR = Path("zeebe/gateway-protocol/src/main/proto")
# v2 is the split spec tree; stable/8.8 still ships the monolithic file.
SPEC_ENTRIES = [Path("v2/rest-api.yaml"), Path("rest-api.yaml")]
LEVELS_FILE = Path(__file__).resolve().parent / "severity-levels.txt"

ERR = 3
MAX_OPERATIONS_SHOWN = 10
# GitHub rejects comment bodies over 65,536 chars; leave headroom.
MAX_REPORT_LENGTH = 60000
TRAILING_STATUS = re.compile(r" for (?:the )?status `\d+`\Z", re.ASCII)

GOVERNANCE_FOOTER = [
    "Please start a discussion in [#top-c8-cluster-api-governance](https://camunda.slack.com/archives/C0A154VV8DB). Either change the PR to remove the break, or API governance accepts it by adding the `skip-oasdiff` label.",
    "",
    "See [REST API endpoint guidelines, section 2.7](https://github.com/camunda/camunda/blob/main/docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes). Known false positives are listed in the [check README](https://github.com/camunda/camunda/blob/main/.github/scripts/oas-breaking-change-check/README.md).",
]
REPORTING_ONLY_LINE = "_This check is in reporting-only mode and does not block merging._"
TRUNCATED_LINE = "_Output truncated; see the job logs._"

# A PascalCase `format` on a string schema is SDK branding (`ProcessInstanceKey`, `TenantId`, ...):
# nothing validates it and the wire type is still a string, so it must not surface as a breaking
# change. Standard formats (`date-time`, `uuid`, `int64`, ...) are all lowercase and are kept. A
# `format` on a non-string schema is kept too, it can hide a real type change.
CUSTOM_FORMAT = re.compile(r"[A-Z]")


@dataclass
class Result:
    status: str = "none"
    baseline: str | None = None
    groups: list[dict[str, Any]] = field(default_factory=list)


def step(message: str) -> None:
    """Names the step in the job log, so a failing command can be traced to it."""
    print(f"oas-check: {message}", file=sys.stderr)


def resolve_baseline(base_ref: str) -> str:
    """`stable/X.Y` -> `X.Y.0`: a patch release must not break its own minor, so the first release
    of that minor is the contract. Everything else -> backwards.compat.version, the last released
    minor."""
    stable = re.fullmatch(r"stable/(\d+\.\d+)", base_ref)
    if stable:
        return f"{stable.group(1)}.0"
    pom = Path("parent/pom.xml").read_text(encoding="utf-8")
    compat = re.search(
        r"<backwards\.compat\.version>\s*([^<\s]+)\s*</backwards\.compat\.version>", pom
    )
    if not compat:
        sys.exit("resolving the baseline: backwards.compat.version not found in parent/pom.xml")
    return compat.group(1)


def export_spec(ref: str, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=True)
    archive = target.parent / f"{target.name}.tar"
    step(f"exporting the spec of {ref}")
    subprocess.run(
        ["git", "archive", "--format=tar", "-o", str(archive), ref, str(PROTO_DIR)], check=True
    )
    subprocess.run(["tar", "-xf", str(archive), "-C", str(target)], check=True)


def spec_entry(tree: Path) -> Path:
    proto = tree / PROTO_DIR
    for relative in SPEC_ENTRIES:
        if (proto / relative).exists():
            return proto / relative
    sys.exit(f"no REST spec entry found under {proto}")


def yaml_files(directory: Path) -> Iterator[Path]:
    for entry in sorted(directory.rglob("*")):
        if entry.is_file() and re.search(r"\.ya?ml$", entry.name):
            yield entry


def is_custom_string_format(schema: dict[Any, Any]) -> bool:
    fmt = schema.get("format")
    # A map here is a property that happens to be called "format".
    if not isinstance(fmt, str):
        return False
    schema_type = schema.get("type")
    return bool(CUSTOM_FORMAT.match(fmt)) and (schema_type is None or schema_type == "string")


def _normalize_node(node: Any, key: Any = None) -> bool:
    """Returns whether `node` or anything below it changed. `key` is the key `node` has in its
    parent map, if it has one."""
    changed = False
    if isinstance(node, dict):
        if is_custom_string_format(node):
            del node["format"]
            changed = True
        # `encoding` is the media-type field only directly in a `content` entry keyed by a media
        # type; requiring a "/" in that key keeps a schema property named `encoding` safe.
        if key == "content":
            for media_type, media in node.items():
                encoding = media.get("encoding") if "/" in str(media_type) and isinstance(media, dict) else None
                if not isinstance(encoding, dict):
                    continue
                for entry in encoding.values():
                    if isinstance(entry, dict) and "headers" in entry:
                        del entry["headers"]
                        changed = True
        for child_key, child in node.items():
            changed |= _normalize_node(child, child_key)
    elif isinstance(node, list):
        for child in node:
            changed |= _normalize_node(child)
    return changed


def normalize(directory: Path) -> None:
    """Rewrites the work copies in place (never the checkout) so oasdiff neither crashes nor
    reports something that is not a wire change:
     - the `headers` of multipart `encoding` entries: oasdiff 1.34.0 and 1.34.1 crash with "base
       schema value is nil" on `$ref`'d ones. The rest of `encoding` is kept.
     - custom string `format`s, see CUSTOM_FORMAT.
    The round-trip loader follows YAML 1.2 (an unquoted `yes` stays a string, as for oasdiff) and
    keeps comments, quoting and most of the layout, so a rewritten file stays close to the original.
    """
    yaml = YAML()
    yaml.preserve_quotes = True
    yaml.width = 1_000_000
    yaml.indent(mapping=2, sequence=4, offset=2)
    for file in yaml_files(directory):
        doc = yaml.load(file)
        if _normalize_node(doc):
            with file.open("w", encoding="utf-8") as out:
                yaml.dump(doc, out)


def oasdiff(binary: str, baseline_tree: Path, tree: Path, label: str) -> list[dict[str, Any]]:
    """Runs oasdiff in default mode, never --flatten-allof, which silently drops `nullable: true`
    placed next to `allOf`."""
    step(f"diffing baseline → {label}")
    result = subprocess.run(
        [
            binary, "breaking", str(spec_entry(baseline_tree)), str(spec_entry(tree)),
            "--severity-levels", str(LEVELS_FILE), "-f", "json",
        ],
        check=True,
        stdout=subprocess.PIPE,
        text=True,
        encoding="utf-8",
    )
    return json.loads(result.stdout)


def promotable_ids(binary: str) -> frozenset[str]:
    """Checks oasdiff treats as `error` by default, minus the ones our severity-levels.txt decides."""
    overridden = {line.split()[0] for line in LEVELS_FILE.read_text(encoding="utf-8").splitlines() if line.strip()}
    step("reading oasdiff's default check levels")
    result = subprocess.run(
        [binary, "checks", "changelog", "-f", "json"],
        check=True,
        stdout=subprocess.PIPE,
        text=True,
        encoding="utf-8",
    )
    defaults = json.loads(result.stdout)
    return frozenset(c["id"] for c in defaults if c["level"] == "error") - overridden


def is_error(finding: dict[str, Any], promotable: frozenset[str]) -> bool:
    """Our spec wraps nearly every `$ref` property in a one-item `allOf`, and oasdiff downgrades a
    default-error check inside an `allOf` to a warning (disclaimer `all-of-not-flattened`), which
    would hide real breaks. Count those as errors. That includes the composition checks: an added
    `allOf` branch can carry new constraints (e.g. a `maxLength` from a shared id type) that oasdiff
    does not report separately, so governance decides whether the change is safe."""
    return finding.get("level") == ERR or (
        "all-of-not-flattened" in finding.get("disclaimers", [])
        and finding.get("id") in promotable
    )


def relative_source(file: str) -> str:
    """`<out-dir>/work/<tree>/zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml` -> `v2/jobs.yaml`."""
    _, found, relative = Path(file).as_posix().partition(f"/{PROTO_DIR.as_posix()}/")
    return relative if found else file


def location(finding: dict[str, Any]) -> dict[str, Any] | None:
    return finding.get("revisionSource") or finding.get("baseSource")


def group(findings: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """One group per root cause: a shared-schema change fans out to one finding per operation
    using it, all pointing at the same spec location."""
    causes: dict[tuple[Any, ...], list[dict[str, Any]]] = {}
    for finding in findings:
        where = location(finding)
        cause = (finding.get("id"), where["file"], where["line"], where.get("column")) if where else (finding.get("id"), finding.get("fingerprint"))
        causes.setdefault(cause, []).append(finding)
    groups = []
    for members in causes.values():
        first = members[0]
        where = location(first)
        # A grouped finding spans several operations and status codes, so the status code oasdiff
        # names for one of them would be misleading.
        text = TRAILING_STATUS.sub("", first["text"]) if len(members) > 1 else first["text"]
        groups.append(
            {
                "id": first.get("id"),
                "source": relative_source(where["file"]) if where else "",
                "text": text,
                "operations": sorted({f"{m.get('operation')} {m.get('path')}" for m in members}),
            }
        )
    groups.sort(key=lambda g: (-len(g["operations"]), g["id"], g["source"], g["text"]))
    return groups


def render_report(result: Result, with_operations: bool = True, shown_groups: int | None = None) -> str:
    icon = "❌ " if result.status == "breaking" else ""
    lines = [MARKER, f"### {icon}OC REST API breaking-change check", ""]
    if result.status == "none":
        lines.append(f"No new breaking changes relative to `{result.baseline}`.")
    else:
        lines += [f"**{len(result.groups)} breaking change(s)** relative to `{result.baseline}`", ""]
        shown = result.groups if shown_groups is None else result.groups[:shown_groups]
        for entry in shown:
            source = f" ({entry['source']})" if entry["source"] else ""
            lines.append(f"- `{entry['id']}` — {entry['text']}{source}")
            if with_operations:
                for operation in entry["operations"][:MAX_OPERATIONS_SHOWN]:
                    lines.append(f"  - `{operation}`")
                hidden = len(entry["operations"]) - MAX_OPERATIONS_SHOWN
                if hidden > 0:
                    lines.append(f"  - …and {hidden} more")
        if len(shown) < len(result.groups):
            lines.append(f"- …and {len(result.groups) - len(shown)} more breaking changes")
        if not with_operations:
            lines += ["", TRUNCATED_LINE]
    if result.status == "breaking":
        lines += ["", *GOVERNANCE_FOOTER]
    lines += ["", REPORTING_ONLY_LINE]
    return "\n".join(lines)


def fit_report(result: Result) -> str:
    """The report as posted: the full one if it fits GitHub's comment limit, else without the
    operation lists, else with only the largest prefix of the groups that fits."""
    report = render_report(result)
    if len(report) <= MAX_REPORT_LENGTH:
        return report
    report = render_report(result, with_operations=False)
    if len(report) <= MAX_REPORT_LENGTH:
        return report
    for shown in range(len(result.groups) - 1, -1, -1):
        report = render_report(result, with_operations=False, shown_groups=shown)
        if len(report) <= MAX_REPORT_LENGTH:
            break
    return report


def check(base_ref: str, binary: str, out_dir: Path) -> Result:
    work = out_dir / "work"
    shutil.rmtree(work, ignore_errors=True)
    result = Result()
    baseline = resolve_baseline(base_ref)
    result.baseline = baseline
    step(f"fetching the baseline tag {baseline}")
    subprocess.run(
        ["git", "fetch", "--depth=1", "--no-tags", "origin", f"refs/tags/{baseline}:refs/tags/{baseline}"],
        check=True,
    )

    trees: dict[str, Path] = {}
    for name, ref in [("baseline", f"refs/tags/{baseline}"), ("base", "HEAD^1"), ("head", "HEAD")]:
        trees[name] = work / name
        export_spec(ref, trees[name])
        normalize(trees[name])

    promotable = promotable_ids(binary)
    base = oasdiff(binary, trees["baseline"], trees["base"], "base")
    head = oasdiff(binary, trees["baseline"], trees["head"], "head")
    existing = {finding.get("fingerprint") for finding in base}
    # A fingerprint present at any level in the base diff already existed: the PR did not
    # introduce it, whatever its severity there.
    added = [
        finding
        for finding in head
        if is_error(finding, promotable) and finding.get("fingerprint") not in existing
    ]
    result.groups = group(added)
    result.status = "breaking" if added else "none"
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--oasdiff", required=True)
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()

    out_dir = Path(args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    result = check(args.base_ref, args.oasdiff, out_dir)
    # The posted report may be truncated, so the full one goes to the job log.
    print("::group::OC REST API breaking-change report")
    print(render_report(result))
    print("::endgroup::")
    report = fit_report(result)
    (out_dir / "report.md").write_text(f"{report}\n", encoding="utf-8")
    (out_dir / "status").write_text(f"{result.status}\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
