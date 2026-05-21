# Monorepo DevOps Team Processes

## Code Reviews

By default, we follow the 4-eye principle—another person needs to review your pull request (PR) before it is merged.

**Exception:** For low-risk PRs, another person's review is not required if an AI review has already been conducted. "Low-risk" PRs include:
- Linear fixes to CI jobs and release processes
- Configuration changes
- Documentation updates
- Internal tooling tweaks
- Fixes of errors and typos

This exception allows for efficient handling of PRs that do not require deeper context or risk assessment. All other changes, especially those affecting production or business logic, must follow the 4-eye policy.
