# Fix: Correct metadata + popover position for Ad‑Hoc Sub‑Process inner instances

Ad‑hoc sub‑process *inner instances* don’t exist in the BPMN diagram, so we used the first child to place the popover. Problem: the popover also showed the **child’s** data instead of the inner instance’s execution scope.

## What changed

- Added `anchorFlowNodeId` to selection state so we can **anchor the popover to a child** while **showing metadata for the inner instance itself**.
- Updated both history tree implementations (v1 + v2) to use new selection logic.
- If children aren’t loaded yet, we still select the inner instance and later attach the anchor when the first child arrives (no flicker / no toggle).
- Diagram highlight now prefers `anchorFlowNodeId` when present.
- Tests added/updated to cover: immediate child, delayed child, and anchor update.

## Why it’s safe

- `anchorFlowNodeId` is optional; old code ignoring it keeps working.
- No API surface changes, no new deps.
- Full unit test suite passes (only existing warnings remain).

## How to sanity check

1. Open a process with an ad‑hoc subprocess.
2. Select the inner instance in the history tree.
3. Popover: shows the inner instance metadata (not the first task’s) and appears where the first child sits.
4. If children load after selection, anchor pops in without losing selection.

## Follow‑ups (nice to have, not required)

- Visual hint that position is proxied through a child.
- Clean up some existing React `act()` warnings (unrelated).

## TL;DR

We separated *what* you’re looking at (inner scope) from *where* we place the popover (first child). Now the data is accurate and the UX feels consistent.
