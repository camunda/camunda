# GitHub Copilot Instructions

## Commit Message Format

All commits in this repository **MUST** follow the [Conventional Commits](https://www.conventionalcommits.org/) standard.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

- **feat**: A new feature
- **fix**: A bug fix
- **chore**: Changes that don't modify src or test files (e.g., updating dependencies, configurations)
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code (white-space, formatting, etc.)
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests or correcting existing tests
- **build**: Changes that affect the build system or external dependencies
- **ci**: Changes to CI configuration files and scripts

### Examples

```
feat: add support for Kubernetes 1.28
fix: correct memory allocation for optimize pod
chore: update camunda 8.9 chart to alpha1-rc1 images
docs: update installation instructions
```

## Pull Request Titles

Pull request titles **MUST** also follow the Conventional Commits format. The PR title should summarize the overall change in the PR.

### Examples

```
feat: add multitenancy support for identity
fix: resolve database connection timeout issue
chore: update all component images to 8.9.0
```

## Additional Guidelines

1. **Commit Scope**: Use scope when it helps clarify which part of the codebase is affected (e.g., `feat(identity): add OIDC support`)
2. **Breaking Changes**: If a commit introduces a breaking change, add `!` after the type/scope (e.g., `feat!: remove deprecated API`) and explain in the footer with `BREAKING CHANGE:`
3. **Multiple Changes**: If making multiple unrelated changes, create separate commits for each logical change
4. **Commit Body**: Provide additional context in the commit body when the description alone is not sufficient
5. **References**: Reference related issues in the commit footer (e.g., `Fixes #123`, `Closes #456`)

## Why Conventional Commits?

- Automatically generate CHANGELOGs
- Automatically determine semantic version bumps
- Communicate the nature of changes to teammates and stakeholders
- Trigger build and publish processes
- Make it easier for people to contribute by allowing them to explore a more structured commit history
