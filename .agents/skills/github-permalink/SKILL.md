---
name: github-permalink
description: "Use when writing GitHub text (issue, PR, review comment) that points at repo code lines."
---

# GitHub Permalink

Turns a repo file and line range into a permalink pinned to a commit SHA. GitHub renders the linked
lines inline, and the SHA keeps the link correct after the file changes. A bare `path:line` is only
fine in local, throwaway context, never in something a teammate reads on GitHub.

Derive the link locally. Do not call the API or `gh`.

```bash
slug=$(git remote get-url origin | sed -E 's#^(git@github\.com:|https?://github\.com/)##; s#\.git$##')
sha=$(git log -1 --format=%H -- "<path>")   # commit that last touched the file
echo "https://github.com/$slug/blob/$sha/<path>#L<start>-L<end>"   # single line: #L<start>
```

## SHA choice

- Default to the file's last-touching commit (`git log -1 --format=%H -- <path>`). The file content
  at that commit equals the current committed content, so the line numbers are valid. For existing
  code that commit is already on the remote.
- Use `git rev-parse HEAD` when linking code from the current PR branch.
- Always use the full 40-char SHA. A branch name or short SHA is less stable and can 404.

## Format

`https://github.com/<slug>/blob/<sha>/<path>#L<start>` for one line, `#L<start>-L<end>` for a range.
`<path>` is repo-root-relative.

## The SHA must be pushed

An unpushed SHA gives a 404. Verify with `git branch -r --contains <sha>` (non-empty).

The file's last-touching commit passes once the code is merged. If the file has local edits that are
not pushed, do not link an unpushed SHA. Pick a SHA already on `main` (e.g.
`git rev-parse origin/main`) and select the line range against that version of the file.
