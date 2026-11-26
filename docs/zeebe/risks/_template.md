---
title: <Risk name, the mistake in one line, same as the heading below>
issue: https://github.com/camunda/camunda/issues/<number>
kind: <Correctness | Backward compatibility | Performance | Test coverage>
status: <Documented | Mitigated | Resolved>
summary: >
  <Two or three sentences: the mistake, what happens if it is not handled correctly, and the rule in short.>
applies_when:
  - <A change that puts you in this risk: modules, packages, classes or files, and the kind of change. Specific enough to match against a diff.>
does_not_apply_when:
  - <A look-alike change that is safe, so readers can stop early.>
---

<!--
Copy this file to docs/zeebe/risks/<issue-number>-<short-name>.md and remove the comments.

The front matter is all an agent reads to decide whether the file concerns its change.
Everything between the two `---` lines must be enough for that: summary, applies_when, does_not_apply_when.
The body is the details, read only when the risk applies or the reader is unsure.
Do not put information that is needed for the decision in the body, and do not repeat the body in the front matter.

kind and status are the values of the fields of the same name in the Engine Governance project. Keep them equal (values: see README.md).
Write instructions, be concrete (modules, classes, tests, commands) and make the file understandable without the discussion in the issue.
See README.md.
-->

# <Risk name: the mistake, in one line>

## Where

<!--
The details of where the risk applies.
- Modules, packages, classes, files, with paths.
- Flows or features.
- Changes that trigger the risk, and changes that look similar but do not.
-->

## What can go wrong

<!--
What happens if the risk is not handled correctly.
- The consequence: data corruption, wrong behavior, broken rolling update, broken clients, slowdown, undetected regressions.
- Who is affected: users, customers, other teams, other versions of the engine.
- How bad and how likely: is it silent, can it be undone, does it show only in production or after an upgrade.
- Precedents: links to issues, pull requests and incidents where it happened.
-->

## What to do

<!--
Start with the rule in one or two sentences.
Then clear instructions to a contributor or an agent who changes this area.
- What to do, in order, and what never to do.
- The preferred alternative to the risky change.
- What a reviewer checks in the pull request.
Use short imperative sentences or a checklist.
-->

## Exceptions

<!--
When the rule in "What to do" may be broken.
- The conditions that must all hold.
- Who approves it and how to ask (where to document the case, which label, who to tell).
- What must be done afterwards: announcement, follow-up issue, test.
- Write "None" if there are no exceptions, and say who to ask if a case seems to need one.
-->

## How to detect

<!--
How a contributor, a reviewer or an agent notices the mistake before or after it is made.
- Patterns in a diff, and where to look for them (files, searches).
- Symptoms in tests, logs, metrics or user reports.
- Existing tests and checks that catch it, with their names and how to run them, and what they do not catch.
-->

## Coverage bar

<!--
The tests or checks that must exist for the risk to count as mitigated, so the mistake cannot be merged unnoticed.
Name them, even if they do not exist yet, and say what exists today.
-->

## References

<!-- Optional: related risks, policies, documentation, discussions. -->
