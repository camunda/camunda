"""Unit tests for changed_lines.py.

Run with:
    pytest .github/scripts/oc-api-design-review/test_changed_lines.py
"""

import importlib.util
import os
import subprocess
import sys

# Load the module by file path, so the tests do not depend on pytest's import mode.
_SCRIPT = os.path.join(os.path.dirname(__file__), "changed_lines.py")
_spec = importlib.util.spec_from_file_location("changed_lines", _SCRIPT)
cl = importlib.util.module_from_spec(_spec)
sys.modules["changed_lines"] = cl
_spec.loader.exec_module(cl)


def _run(tmp_path, diff):
    source = tmp_path / "pr.diff"
    target = tmp_path / "changed-lines.txt"
    source.write_text(diff, encoding="utf-8")
    cl.list_changed_lines(str(source), str(target))
    return target.read_text(encoding="utf-8")


def _file_diff(path, *body):
    return "\n".join([f"diff --git a/{path} b/{path}", f"--- a/{path}", f"+++ b/{path}", *body]) + "\n"


def test_numbers_added_lines_by_the_hunk_header_of_the_head_file(tmp_path):
    # given: two hunks, with removed and context lines between the added ones
    diff = _file_diff(
        "spec/a.yaml",
        "@@ -3,5 +3,6 @@ paths:",
        " context one",
        "-removed",
        "+added one",
        " context two",
        "+added two",
        " context three",
        "@@ -20,2 +21,3 @@",
        " context four",
        "+added three",
        " context five",
    )

    # when
    result = _run(tmp_path, diff)

    # then: removed lines are not counted, context lines advance the counter
    assert result == (
        "spec/a.yaml:4: added one\n"
        "spec/a.yaml:6: added two\n"
        "spec/a.yaml:22: added three\n"
    )


def test_numbers_a_new_file_from_line_one(tmp_path):
    # given
    diff = "\n".join([
        "diff --git a/spec/new.yaml b/spec/new.yaml",
        "new file mode 100644",
        "--- /dev/null",
        "+++ b/spec/new.yaml",
        "@@ -0,0 +1,2 @@",
        "+openapi: 3.0.3",
        "+paths: {}",
    ]) + "\n"

    # when
    result = _run(tmp_path, diff)

    # then
    assert result == "spec/new.yaml:1: openapi: 3.0.3\nspec/new.yaml:2: paths: {}\n"


def test_reads_a_hunk_header_without_line_counts(tmp_path):
    # given: git omits the count when a hunk covers a single line
    diff = _file_diff("spec/a.yaml", "@@ -7 +7 @@", "-old", "+new")

    # when
    result = _run(tmp_path, diff)

    # then
    assert result == "spec/a.yaml:7: new\n"


def test_keeps_a_text_line_that_starts_with_two_pluses(tmp_path):
    # given: the added line `++ text` shows in the diff as `+++ text`, like a file header
    diff = _file_diff(
        "spec/a.yaml",
        "@@ -1,2 +1,3 @@",
        " first",
        "+++ text",
        " last",
        "+after",
    )

    # when
    result = _run(tmp_path, diff)

    # then: it is a changed line of the open hunk and not the header of another file
    assert result == "spec/a.yaml:2: ++ text\nspec/a.yaml:4: after\n"


def test_does_not_count_the_no_newline_marker(tmp_path):
    # given
    diff = _file_diff(
        "spec/a.yaml",
        "@@ -1,2 +1,2 @@",
        " first",
        "-old last",
        "\\ No newline at end of file",
        "+new last",
        "\\ No newline at end of file",
    )

    # when
    result = _run(tmp_path, diff)

    # then
    assert result == "spec/a.yaml:2: new last\n"


def test_lists_nothing_for_a_deleted_file_and_goes_on_with_the_next_file(tmp_path):
    # given
    diff = "\n".join([
        "diff --git a/spec/gone.yaml b/spec/gone.yaml",
        "deleted file mode 100644",
        "--- a/spec/gone.yaml",
        "+++ /dev/null",
        "@@ -1,2 +0,0 @@",
        "-openapi: 3.0.3",
        "-paths: {}",
    ]) + "\n" + _file_diff("spec/kept.yaml", "@@ -1 +1,2 @@", " first", "+second")

    # when
    result = _run(tmp_path, diff)

    # then
    assert result == "spec/kept.yaml:2: second\n"


def test_lists_nothing_for_a_binary_file(tmp_path):
    # given
    diff = "\n".join([
        "diff --git a/docs/logo.png b/docs/logo.png",
        "index 1111111..2222222 100644",
        "Binary files a/docs/logo.png and b/docs/logo.png differ",
    ]) + "\n"

    # when
    result = _run(tmp_path, diff)

    # then
    assert result == ""


def test_starts_a_new_file_at_its_own_hunk_header(tmp_path):
    # given: the first file ends mid-way, the second starts at another line
    diff = (
        _file_diff("spec/a.yaml", "@@ -1,1 +1,2 @@", " keep", "+a-added")
        + _file_diff("spec/b.yaml", "@@ -9,1 +9,2 @@", " keep", "+b-added")
    )

    # when
    result = _run(tmp_path, diff)

    # then
    assert result == "spec/a.yaml:2: a-added\nspec/b.yaml:10: b-added\n"


def test_gives_an_empty_file_for_an_empty_diff(tmp_path):
    # given: GitHub serves no diff for very large pull requests

    # when
    result = _run(tmp_path, "")

    # then
    assert result == ""


def test_replaces_bytes_that_are_not_valid_text(tmp_path):
    # given
    source = tmp_path / "pr.diff"
    target = tmp_path / "changed-lines.txt"
    source.write_bytes(
        b"diff --git a/spec/a.yaml b/spec/a.yaml\n"
        b"--- a/spec/a.yaml\n+++ b/spec/a.yaml\n@@ -1 +1,2 @@\n keep\n+bad \xff byte\n"
    )

    # when
    cl.list_changed_lines(str(source), str(target))

    # then
    assert target.read_text(encoding="utf-8") == "spec/a.yaml:2: bad � byte\n"


def test_command_line_reads_the_diff_and_writes_the_file(tmp_path):
    # given
    source = tmp_path / "pr.diff"
    target = tmp_path / "changed-lines.txt"
    source.write_text(_file_diff("spec/a.yaml", "@@ -1 +1,2 @@", " keep", "+added"), encoding="utf-8")

    # when
    subprocess.run(
        [sys.executable, _SCRIPT, "--diff", str(source), "--out", str(target)], check=True
    )

    # then
    assert target.read_text(encoding="utf-8") == "spec/a.yaml:2: added\n"
