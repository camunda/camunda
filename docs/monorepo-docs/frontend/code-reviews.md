# Code reviews

Our baseline is the Camunda-wide review guide in
[CONTRIBUTING.md](https://github.com/camunda/camunda/blob/main/CONTRIBUTING.md#reviewing-a-pull-request)
and the
[Pull Requests and Code Reviews wiki](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews).
This page adds frontend-specific expectations.

## Reviewer responsibility

You share ownership of the code you approve. Approving a PR means you
are confident it meets our standards and does what the PR and issue
description say it does.

## What to check

- Code follows project conventions: data loading, routing, modules,
  forms, and testing patterns from the sibling docs in this section.
- The PR does what the issue and PR description describe. The PR might contain small unrelated improvements but the reviewer should ensure they are minor and do not overwhelm the PR.
- No leftover AI artifacts: redundant comments, unnecessary
  abstractions, dead code.
- Tests cover the change meaningfully.

## Manual testing

Before approving, run the feature locally:

- Check the happy path end to end.
- Try 1-2 error cases (network failure, invalid input, 403, empty
  state).

## Emoji code

We use the emoji code documented in
[CONTRIBUTING.md](https://github.com/camunda/camunda/blob/main/CONTRIBUTING.md#review-emoji-code)
to express the intent of review comments (required change, suggestion,
question, etc.).
