"""Unit tests for new_surface.py.

The tests build a throwaway git repository with a real merge commit: HEAD^1 is the base branch tip,
HEAD^2 the pull request head, as in the workflow.

Run with:
    pytest .github/scripts/oc-api-design-review/test_new_surface.py
"""

import importlib.util
import os
import subprocess
import sys
import textwrap

import pytest
from ruamel.yaml.error import YAMLError

# Load the module by file path, so the tests do not depend on pytest's import mode.
_SCRIPT = os.path.join(os.path.dirname(__file__), "new_surface.py")
_spec = importlib.util.spec_from_file_location("new_surface", _SCRIPT)
ns = importlib.util.module_from_spec(_spec)
sys.modules["new_surface"] = ns
_spec.loader.exec_module(ns)

SPEC = ns.SPEC_DIR
X = f"{SPEC}/x.yaml"


def _yaml(text):
    return textwrap.dedent(text).lstrip("\n")


def _indent(text, columns):
    return textwrap.indent(_yaml(text), " " * columns)


def _line(text, needle, nth=1):
    """The 1-based number of the nth line of text that is the needle, ignoring indentation."""
    matches = [i for i, line in enumerate(text.splitlines(), 1) if line.strip() == needle]
    return matches[nth - 1]


def _git(repo, *args):
    env = {
        **os.environ,
        "GIT_CONFIG_GLOBAL": os.devnull,
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_AUTHOR_NAME": "Test",
        "GIT_AUTHOR_EMAIL": "test@example.com",
        "GIT_COMMITTER_NAME": "Test",
        "GIT_COMMITTER_EMAIL": "test@example.com",
    }
    subprocess.run(
        ["git", "-C", str(repo), "-c", "commit.gpgsign=false", *args],
        check=True, capture_output=True, encoding="utf-8", env=env,
    )


def _write(repo, files):
    for name, content in files.items():
        target = repo / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")


def _merge(tmp_path, base, head, base_tip=None):
    """A repository whose HEAD is the merge of a pull request branch into main.

    base holds the files at the merge base, head the files the pull request branch changes and
    base_tip the files main changes after the pull request branch was cut. Keys are paths relative
    to the repository root."""
    repo = tmp_path / "merge"
    repo.mkdir()
    _git(repo, "init", "-q", "-b", "main")
    _write(repo, base)
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "base")
    _git(repo, "switch", "-q", "-c", "pr")
    _write(repo, head)
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "--allow-empty", "-m", "pr")
    _git(repo, "switch", "-q", "main")
    if base_tip:
        _write(repo, base_tip)
        _git(repo, "add", "-A")
        _git(repo, "commit", "-q", "-m", "main moves on")
    _git(repo, "merge", "-q", "--no-ff", "-m", "merge", "pr")
    return repo


def _surface(tmp_path, repo):
    """Runs the script and returns the bullets of each section, without the leading dash."""
    out = tmp_path / "new-surface.md"
    ns.main(["--worktree", str(repo), "--out", str(out)])
    sections, current = {}, None
    for line in out.read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            current = line[3:].rsplit(" (", 1)[0]
            sections[current] = []
        elif current and line.startswith("- "):
            sections[current].append(line[2:])
    return sections


def _bullets(*entries, path=X):
    """The bullets for entries given as (line of the head file, label)."""
    return {f"{path}:{line}: {label}" for line, label in entries}


WIDGET = _yaml("""
    openapi: 3.0.3
    components:
      schemas:
        Widget:
          type: object
          properties:
            id:
              type: string
    """)


def test_lists_nested_properties_array_items_and_additional_properties(tmp_path):
    # given
    head = WIDGET + _indent("""
        details:
          type: object
          properties:
            owner:
              type: object
              properties:
                name:
                  type: string
        tags:
          type: array
          items:
            type: object
            properties:
              label:
                type: string
        attributes:
          type: object
          additionalProperties:
            type: object
            properties:
              value:
                type: string
        """, 8)
    repo = _merge(tmp_path, {X: WIDGET}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then: the new properties at any depth, and not the existing `id`
    assert set(result["Properties"]) == _bullets(
        (_line(head, "details:"), "`Widget.details`"),
        (_line(head, "owner:"), "`Widget.details.owner`"),
        (_line(head, "name:"), "`Widget.details.owner.name`"),
        (_line(head, "tags:"), "`Widget.tags`"),
        (_line(head, "label:"), "`Widget.tags[].label`"),
        (_line(head, "attributes:"), "`Widget.attributes`"),
        (_line(head, "value:"), "`Widget.attributes.*.value`"),
    )
    assert result["Schemas"] == []


def test_lists_a_property_added_to_one_composition_member_even_if_another_member_has_it(tmp_path):
    # given: `token` exists in the first oneOf member and is added to the second one, and the same
    # for `alpha` in anyOf
    base = _yaml("""
        openapi: 3.0.3
        components:
          schemas:
            Request:
              oneOf:
                - type: object
                  properties:
                    token:
                      type: string
                - type: object
                  properties:
                    other:
                      type: string
              anyOf:
                - type: object
                  properties:
                    alpha:
                      type: string
                - type: object
                  properties:
                    beta:
                      type: string
        """)
    head = _yaml("""
        openapi: 3.0.3
        components:
          schemas:
            Request:
              oneOf:
                - type: object
                  properties:
                    token:
                      type: string
                - type: object
                  properties:
                    other:
                      type: string
                    token:
                      type: string
              anyOf:
                - type: object
                  properties:
                    alpha:
                      type: string
                - type: object
                  properties:
                    beta:
                      type: string
                    alpha:
                      type: string
        """)
    repo = _merge(tmp_path, {X: base}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then: the member is part of the identity as <keyword>[<index>]
    assert set(result["Properties"]) == _bullets(
        (_line(head, "token:", nth=2), "`Request.oneOf[1].token`"),
        (_line(head, "alpha:", nth=2), "`Request.anyOf[1].alpha`"),
    )


def test_lists_a_property_of_a_new_allof_member(tmp_path):
    # given
    base = _yaml("""
        openapi: 3.0.3
        components:
          schemas:
            Combined:
              allOf:
                - type: object
                  properties:
                    id:
                      type: string
        """)
    head = base + _indent("""
        - type: object
          properties:
            added:
              type: string
        """, 8)
    repo = _merge(tmp_path, {X: base}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then
    assert result["Properties"] == [f"{X}:{_line(head, 'added:')}: `Combined.allOf[1].added`"]


THINGS = _yaml("""
    openapi: 3.0.3
    paths:
      /things:
        get:
          operationId: listThings
          responses:
            "200":
              description: ok
    """)


def test_lists_new_operations_parameters_response_codes_and_their_inline_schemas(tmp_path):
    # given: the pull request adds a parameter and a response to GET and a whole POST operation
    head = _yaml("""
        openapi: 3.0.3
        paths:
          /things:
            get:
              operationId: listThings
              parameters:
                - name: filter
                  in: query
                  schema:
                    type: object
                    properties:
                      status:
                        type: string
              responses:
                "200":
                  description: ok
                  content:
                    application/json:
                      schema:
                        type: object
                        properties:
                          items:
                            type: array
                            items:
                              type: object
                              properties:
                                id:
                                  type: string
                "404":
                  description: not found
            post:
              operationId: createThing
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      properties:
                        name:
                          type: string
              responses:
                "201":
                  description: created
        """)
    repo = _merge(tmp_path, {X: THINGS}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then
    assert result["Operations"] == [f"{X}:{_line(head, 'post:')}: `POST /things` (createThing)"]
    assert result["Parameters"] == [
        f"{X}:{_line(head, '- name: filter')}: `filter (query)` on `GET /things`"
    ]
    assert set(result["Response codes"]) == _bullets(
        (_line(head, '"404":'), "`404` on `GET /things`"),
        (_line(head, '"201":'), "`201` on `POST /things`"),
    )
    assert set(result["Properties"]) == _bullets(
        (_line(head, "status:"), "`GET /things parameters.filter.status`"),
        (_line(head, "items:"), "`GET /things responses.200.items`"),
        (_line(head, "id:"), "`GET /things responses.200.items[].id`"),
        (_line(head, "name:"), "`POST /things requestBody.name`"),
    )
    assert result["Schemas"] == []


def test_lists_a_path_level_parameter_for_each_operation(tmp_path):
    # given
    base = _yaml("""
        openapi: 3.0.3
        paths:
          /things:
            get:
              operationId: listThings
              responses:
                "200":
                  description: ok
            post:
              operationId: createThing
              responses:
                "201":
                  description: created
        """)
    head = _yaml("""
        openapi: 3.0.3
        paths:
          /things:
            parameters:
              - name: tenantId
                in: query
                schema:
                  type: string
            get:
              operationId: listThings
              responses:
                "200":
                  description: ok
            post:
              operationId: createThing
              responses:
                "201":
                  description: created
        """)
    repo = _merge(tmp_path, {X: base}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then
    line = _line(head, "- name: tenantId")
    assert result["Parameters"] == [
        f"{X}:{line}: `tenantId (query)` on `GET /things`",
        f"{X}:{line}: `tenantId (query)` on `POST /things`",
    ]
    assert result["Operations"] == []


def test_lists_reusable_parameters_responses_and_request_bodies_where_they_are_defined(tmp_path):
    # given: an operation that only references the reusable parameter
    head = _yaml("""
        openapi: 3.0.3
        paths:
          /things:
            get:
              operationId: listThings
              parameters:
                - $ref: '#/components/parameters/Limit'
              responses:
                "200":
                  description: ok
        components:
          parameters:
            Limit:
              name: limit
              in: query
              schema:
                type: object
                properties:
                  max:
                    type: integer
          responses:
            NotFound:
              description: not found
              content:
                application/problem+json:
                  schema:
                    type: object
                    properties:
                      detail:
                        type: string
          requestBodies:
            CreateThing:
              content:
                application/json:
                  schema:
                    type: object
                    properties:
                      name:
                        type: string
        """)
    repo = _merge(tmp_path, {X: THINGS}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then
    assert set(result["Parameters"]) == _bullets(
        (_line(head, "- $ref: '#/components/parameters/Limit'"),
         "`$ref #/components/parameters/Limit` on `GET /things`"),
        (_line(head, "Limit:"), "`Limit: limit (query)` on `components.parameters`"),
    )
    assert set(result["Schemas"]) == _bullets(
        (_line(head, "NotFound:"), "`responses.NotFound`"),
        (_line(head, "CreateThing:"), "`requestBodies.CreateThing`"),
    )
    assert set(result["Properties"]) == _bullets(
        (_line(head, "max:"), "`parameters.Limit.schema.max`"),
        (_line(head, "detail:"), "`responses.NotFound.detail`"),
        (_line(head, "name:"), "`requestBodies.CreateThing.name`"),
    )
    assert result["Operations"] == []


def test_lists_only_the_new_enum_values_of_an_existing_enum_and_all_of_a_new_one(tmp_path):
    # given
    base = _yaml("""
        openapi: 3.0.3
        paths:
          /things:
            get:
              operationId: listThings
              responses:
                "200":
                  description: ok
        components:
          schemas:
            Widget:
              type: object
              properties:
                state:
                  type: string
                  enum:
                    - CREATED
                    - ACTIVE
        """)
    head = _yaml("""
        openapi: 3.0.3
        paths:
          /things:
            get:
              operationId: listThings
              parameters:
                - name: mode
                  in: query
                  schema:
                    type: string
                    enum:
                      - FAST
              responses:
                "200":
                  description: ok
        components:
          schemas:
            Widget:
              type: object
              properties:
                state:
                  type: string
                  enum:
                    - CREATED
                    - ACTIVE
                    - ARCHIVED
                kind:
                  type: string
                  enum:
                    - ROUND
                    - SQUARE
        """)
    repo = _merge(tmp_path, {X: base}, {X: head})

    # when
    result = _surface(tmp_path, repo)

    # then
    assert set(result["Enum values"]) == _bullets(
        (_line(head, "- ARCHIVED"), "`ARCHIVED` on `Widget.state`"),
        (_line(head, "- ROUND"), "`ROUND` on `Widget.kind`"),
        (_line(head, "- SQUARE"), "`SQUARE` on `Widget.kind`"),
        (_line(head, "- FAST"), "`FAST` on `GET /things.parameters[mode].schema`"),
    )


def test_lists_nothing_for_an_identical_spec(tmp_path):
    # given
    spec = WIDGET + _indent("name:\n  type: string\n", 8)
    repo = _merge(tmp_path, {X: spec}, {X: spec})

    # when
    result = _surface(tmp_path, repo)

    # then
    assert result == {
        "Operations": [], "Schemas": [], "Properties": [], "Parameters": [],
        "Response codes": [], "Enum values": [],
    }


def test_lists_nothing_for_what_only_the_base_branch_added(tmp_path):
    # given: the pull request does not touch the spec, the base branch added a schema since
    base_tip = WIDGET + _indent("""
        Other:
          type: object
          properties:
            name:
              type: string
        """, 4)
    repo = _merge(tmp_path, {X: WIDGET}, {"README.md": "pull request\n"}, {X: base_tip})

    # when
    result = _surface(tmp_path, repo)

    # then
    assert all(entries == [] for entries in result.values())


def test_reports_the_line_in_the_head_file_when_the_base_branch_added_lines_above(tmp_path):
    # given: main inserts a schema above Widget while the pull request appends one below it
    base = WIDGET + _indent("""
        created:
          type: string
        updated:
          type: string
        deleted:
          type: string
        """, 8)
    inserted = base.replace(
        "    Widget:\n",
        "    Inserted:\n      type: object\n      properties:\n        a:\n          type: string\n"
        "    Widget:\n",
    )
    head = base + _indent("""
        Added:
          type: object
          properties:
            field:
              type: string
        """, 4)
    repo = _merge(tmp_path, {X: base}, {X: head}, {X: inserted})

    # when
    result = _surface(tmp_path, repo)

    # then: the lines of the pull request head file, which the diff and review comments use, and
    # not those of the merged file, which are 5 lines further down
    merged = (repo / X).read_text(encoding="utf-8")
    assert _line(merged, "Added:") == _line(head, "Added:") + 5
    assert result["Schemas"] == [f"{X}:{_line(head, 'Added:')}: `Added`"]
    assert result["Properties"] == [f"{X}:{_line(head, 'field:')}: `Added.field`"]


def test_reads_every_yaml_file_of_the_spec_directory_and_nothing_else(tmp_path):
    # given: a new spec file, a markdown file, a yaml file that is a list and a yaml file elsewhere
    gadgets = _yaml("""
        openapi: 3.0.3
        components:
          schemas:
            Gadget:
              type: object
        """)
    repo = _merge(
        tmp_path,
        {X: WIDGET},
        {
            f"{SPEC}/gadgets.yaml": gadgets,
            f"{SPEC}/notes.md": "# Gadget\n",
            f"{SPEC}/list.yaml": "- one\n- two\n",
            "elsewhere/other.yaml": gadgets.replace("Gadget", "Elsewhere"),
        },
    )

    # when
    result = _surface(tmp_path, repo)

    # then
    assert result["Schemas"] == [f"{SPEC}/gadgets.yaml:{_line(gadgets, 'Gadget:')}: `Gadget`"]


def test_counts_each_kind_in_the_header(tmp_path):
    # given
    head = WIDGET + _indent("""
        extra:
          type: string
          enum:
            - ONE
            - TWO
        """, 8)
    repo = _merge(tmp_path, {X: WIDGET}, {X: head})
    out = tmp_path / "new-surface.md"

    # when
    ns.main(["--worktree", str(repo), "--out", str(out)])

    # then
    text = out.read_text(encoding="utf-8")
    assert (
        "Counts: operations 0, schemas 0, properties 1, parameters 0, response codes 0, "
        "enum values 2.\n"
    ) in text
    assert "## Operations (0)\n\nNone.\n" in text
    assert "## Enum values (2)\n\n- " in text


def test_writes_a_note_when_the_worktree_is_missing(tmp_path):
    # given
    out = tmp_path / "new-surface.md"

    # when
    ns.main(["--worktree", str(tmp_path / "missing"), "--out", str(out)])

    # then
    assert out.read_text(encoding="utf-8") == (
        "No list of added elements: the merge commit checkout is not available.\n"
    )


def test_writes_no_file_when_a_spec_file_does_not_parse(tmp_path):
    # given
    repo = _merge(tmp_path, {X: WIDGET}, {X: "components: [unclosed\n"})
    out = tmp_path / "new-surface.md"

    # when / then: the step fails and the prompt falls back to the diff
    with pytest.raises(YAMLError):
        ns.main(["--worktree", str(repo), "--out", str(out)])
    assert not out.exists()


def test_command_line_writes_the_list(tmp_path):
    # given
    repo = _merge(tmp_path, {X: WIDGET}, {X: WIDGET + "    Added:\n      type: object\n"})
    out = tmp_path / "new-surface.md"

    # when
    subprocess.run(
        [sys.executable, _SCRIPT, "--worktree", str(repo), "--out", str(out)], check=True
    )

    # then
    assert "`Added`" in out.read_text(encoding="utf-8")
