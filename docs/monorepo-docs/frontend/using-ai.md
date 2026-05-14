# Using AI

Camunda follows an AI-first approach. Follow the company AI policies
and apply the guidelines below when using AI for frontend work.

## Your responsibility

AI writes code, you own the result. Your job is to steer agents toward
a good solution, not accept the first thing that compiles. Apply
critical thinking, iterate, and refine.

A PR should look as if AI was not involved. If it doesn't, you are
shifting review burden to other engineers and wasting cycles from
automated agents reviewing your PRs.

## Working with agents

- Use the frontend agent for frontend work. It is purpose-built for
  the orchestration-cluster webapp and knows our conventions. The agent
  is currently being developed and will be available soon.
- Break the problem down before prompting. Smaller, well-scoped
  prompts produce better results than "implement this feature."
- Review each response critically. Check for unnecessary abstractions,
  dead code, wrong patterns, and deviations from project conventions.
- Iterate. Push back on the agent when the solution is not good
  enough. Ask it to simplify, restructure, or try a different
  approach.
- Use the project docs (this site) as context for the agent so it
  follows our conventions.

## Before opening a PR

- Code follows project conventions (see sibling docs).
- No leftover boilerplate, redundant comments, or over-engineered
  abstractions typical of AI output.
- You can explain what your code does.
- Tests are meaningful, not just "make the build pass."
- The diff is clean and reviewable, same standard as hand-written
  code.
