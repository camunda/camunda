---
name: session-state
description: Persist and resume Claude Code session progress across restarts, --resume/--continue, and context compaction. Use when asked to persist/save/output session state, or when starting a session and checking for prior progress to resume from.
---

# Session State

Session-continuity files let a session survive context loss (a fresh terminal, `--resume`,
`--continue`, or compaction losing detail) by writing progress to disk, keyed by session id so
concurrent sessions in the same working directory never clobber each other's state.

## Persisting

If the user prompts you to persist/save/output session state:

- Write to `.agent-sessions/STATE.<session-id>.md` at repo root, creating the `.agent-sessions/`
  directory first if it doesn't exist. Key the filename by a stable per-session identifier (e.g.
  `$CLAUDE_CODE_SESSION_ID`, falling back to a random suffix generated once and reused for every
  write in the session if no such identifier is available).
- Start the file with a `_Last updated: <ISO 8601 timestamp>_` line, refreshed on every write, then
  record discoveries and remaining work under
  `## Goal / Instructions / Discoveries / Accomplished / Not Yet Done / Relevant Files` — keep it
  useful to a fresh session.

## Resuming

At session start:

- Check `.agent-sessions/STATE*.md` files: delete any whose `Last updated` timestamp is older than
  14 days, since that file's session is abandoned.
- Resume only from `.agent-sessions/STATE.<session-id>.md` matching the current session's own
  identifier, if it exists (that file persists across `--resume`/`--continue` and context
  compaction). A genuinely new session id has no matching file and starts fresh — this is not a
  mechanism for a new, unrelated session to discover another session's prior work.

## Cleanup

Delete the session's own `.agent-sessions/STATE.<session-id>.md` once its goal is fully done,
rather than waiting for it to age out.
