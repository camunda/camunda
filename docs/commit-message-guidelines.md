# Commit message guidelines

Commit messages use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) format.

```
<header>
<BLANK LINE> (optional - mandatory with body)
<body> (optional)
<BLANK LINE> (optional - mandatory with footer)
<footer> (optional)
```

Camunda uses a GitHub Actions workflow to check your commit messages when a pull request is submitted. Please make sure to address any hints from the bot.

## Commit message header

Examples:

* `docs: add start event to bpmn symbol support matrix`
* `perf: reduce latency in backpressure`
* `feat: allow more than 9000 jobs in a single call`

The commit header should match the following pattern:

```
%{type}: %{description}
```

The commit header should be kept short, preferably under 72 chars but we allow a max of 120 chars.

- `type` should be one of:
  - `build`: Changes that affect the build system (e.g. Maven, Docker, etc)
  - `ci`: Changes to our CI configuration files and scripts (e.g. GitHub Actions, etc)
  - `deps`: A change to the external dependencies (was already used by Dependabot)
  - `docs`:  A change to the documentation
  - `feat`: A new feature (both internal or user-facing)
  - `fix`: A bug fix (both internal or user-facing)
  - `perf`: A code change that improves performance
  - `refactor`: A code change that does not change the behavior
  - `style`: A change to align the code with our style guide
  - `test`: Adding missing tests or correcting existing tests
- `description`: short description of the change in present tense

## Commit message body

Should describe the motivation for the change. This is optional but encouraged. Good commit messages explain what changed AND why you changed it. See [I've written a clear changelist description](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews#ive-written-a-clear-changelist-description).

