# Orchestration Cluster Project

The Orchestration Cluster is a distributed systems application with a Java backend and a React,
Carbon based frontend. Its purpose is to provide a robust, scalable process automation platform
using BPMN (Business Process Model and Notation) for process definition and execution.

## Repository Structure

Key modules in this monorepo:

- `zeebe/` – the process automation engine (BPMN execution, gRPC gateway, REST gateway)
- `operate/` – monitoring and troubleshooting UI for processes running in Zeebe
- `tasklist/` – UI and API for managing user tasks
- `identity/` – authentication and authorization (being renamed to `admin/`)
- `dist/` – distribution and packaging of the unified Camunda 8 application
- `qa/` – acceptance and integration tests
- `clients/` – Java and other client libraries
- `search/` – search client abstractions (Elasticsearch, OpenSearch, RDBMS)
- `webapps-schema/` – shared index/schema descriptors for web apps
- `gateways/` – REST and MCP gateway modules
- `docs/` – developer documentation and guidelines
- `.github/` – CI workflows, composite actions, agent skills, and Copilot instructions

## Build & Commands

- Clean: `./mvnw clean -T1C`
- Lint: `./mvnw license:check spotless:check -T1C`
- Auto format: `./mvnw license:format spotless:apply -T1C`
- Build quickly: `./mvnw install -Dquickly -T1C`
- Run static checks: `./mvnw verify -Dquickly -DskipChecks=false -P'!autoFormat,checkFormat,spotbugs' -T1C`
- Run unit tests: `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C`
- Run integration tests: `./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C`
- Run all tests: `./mvnw verify -T1C`
- Run scoped tests for a single module: `./mvnw -pl <module> -am test -DskipITs -DskipChecks -T1C`
- Build the docker image: `./mvnw install -DskipChecks -DskipTests -T1C && docker build -f camunda.Dockerfile --target app -t "camunda/camunda:current-test" --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .`

### Development Environment

- REST API: http://localhost:8080
- gRPC API: http://localhost:26500
- Management API: http://localhost:9600/actuator

## Code Style and Conventions

- Defined via the Maven Spotless plugin; run `./mvnw spotless:apply -T1C` to auto-format before committing
- Follow conventions in `CONTRIBUTING.md` and all Markdown files in `docs/`
- For Java: follow [Google Java Format](https://github.com/google/google-java-format) and [Zeebe Code Style](https://github.com/camunda/camunda/wiki/Code-Style)

## Testing

- Follow guidelines in `docs/testing.md` and all files in `docs/testing/`
- **Prefix all test methods with `should`** (e.g. `shouldReturnEmptyListWhenNoResults`)
- **Divide each test into `// given`, `// when`, `// then` comment sections** to separate concerns
- Test one behavior at a time — avoid `And`/`Or` in test names
- Use **JUnit 5** (`@Test`, `@Nested`, `@BeforeEach`, etc.) for new tests; migrate JUnit 4 when convenient
- Use **AssertJ** for all assertions — never use JUnit or Hamcrest assertion methods directly
- Use **Awaitility** for async conditions — never use `Thread.sleep`
- Unit tests live in the same module under `src/test/java/`, named `<ClassName>Test.java`
- Integration tests are suffixed `IT` (e.g. `MyServiceIT.java`) and run with `-DskipUTs`

## Security

- Use appropriate data types that limit exposure of sensitive information
- Never commit secrets or API keys to repository
- Use environment variables for sensitive data
- Use HTTPS in production
- Regular dependency updates
- Follow principle of least privilege

## Git Workflow

- ALWAYS run `./mvnw license:format spotless:apply -T1C` before committing to fix formatting
- NEVER use `git push --force` on the main branch
- Use `git push --force-with-lease` for feature branches if needed
- Always verify current branch before force operations

## Commit Message Format

Commit messages **must** follow [Conventional Commits](https://www.conventionalcommits.org/) format.
**Scopes are not allowed** — the commit linter enforces `scope-empty`.

```
<type>: <short description in present tense>

<optional body explaining motivation>
```

Valid `type` values:

| Type       | When to use                                              |
|------------|----------------------------------------------------------|
| `feat`     | A new feature (internal or user-facing)                  |
| `fix`      | A bug fix (internal or user-facing)                      |
| `docs`     | Documentation changes only                              |
| `refactor` | Code change that does not alter behavior                 |
| `test`     | Adding or correcting tests                               |
| `ci`       | Changes to CI configuration or scripts                   |
| `build`    | Changes to the build system (Maven, Docker, etc.)        |
| `perf`     | Performance improvements                                 |
| `style`    | Code style/formatting alignment                          |
| `deps`     | Dependency updates                                       |

Examples of **valid** commit messages:

- `feat: add process instance batch cancel endpoint`
- `fix: handle null tenant id in authorization check`
- `test: add regression test for user task export`
- `ci: add timeout to integration test job`
- `docs: document scoped build command`

Examples of **invalid** commit messages (scope not allowed):

- ~~`feat(search): add filter by tenant`~~ → use `feat: add filter by tenant`
- ~~`fix(zeebe): handle NPE in gateway`~~ → use `fix: handle NPE in gateway`

## GitHub Actions CI

- Always specify a comment with `# owner: TODO`
- Do not hard code secrets; do not use GitHub Secrets (except for Vault bootstrap)
- Use the Hashicorp Vault action to retrieve credentials and secrets
- For common steps, create reusable workflows or composite actions to avoid duplication
- Avoid introducing new 3rd-party GitHub Actions; use only those approved in `docs/monorepo-docs/ci.md`
- For Maven jobs use the `.github/actions/setup-maven-cache` composite action
- For NodeJS jobs relying on Yarn use the `camunda/infra-global-github-actions/setup-yarn-cache` action
- Always specify `permissions: {}` at the job level and add only the minimum required scopes
- Always specify `bash` as the default shell for all jobs (`defaults: run: shell: bash`)
- Always specify `timeout-minutes` on every job
- After modifying workflows, run `actionlint .github/workflows/*.yml` and fix all warnings
- After modifying workflows, run `conftest test --rego-version v0 -o github --policy .github .github/workflows/*.yml` and fix all violations
- The last step of each GHA workflow job **must** submit CI health metrics:

  ```yaml
  - name: Observe build status
    if: always()
    continue-on-error: true
    uses: ./.github/actions/observe-build-status
    with:
      build_status: ${{ job.status }}
      secret_vault_address: ${{ secrets.VAULT_ADDR }}
      secret_vault_roleId: ${{ secrets.VAULT_ROLE_ID }}
      secret_vault_secretId: ${{ secrets.VAULT_SECRET_ID }}
  ```
- Create composite actions in `.github/actions/<kebab-case-name>/` subdirectories
- Always create a `README.md` for composite actions describing purpose, inputs, outputs, and example usage
- For CI agent tasks, use the **CI agent** (`.github/agents/ci.agent.md`) and the skills under `.github/skills/`

## Finding Documentation

- Always check whether documentation exists for the module you are working on
- If documentation exists in the module, read it before making changes to the code
- Check `README.md` and other Markdown files in the module directory
- If you work on the workflow engine, check the engine [README](/zeebe/engine/README.md)
- Further documentation and development guidelines can be found in the `docs/` directory
- CI documentation: `docs/monorepo-docs/ci.md`
- Testing documentation: `docs/testing.md` and `docs/testing/`

