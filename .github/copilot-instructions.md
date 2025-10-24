# Orchestration Cluster Project

The Orchestration Cluster is a distributed systems application with a Java backend and a React,
Carbon based frontend. Its purpose is to provide a robust, scalable process automation platform
using BPMN (Business Process Model and Notation) for process definition and execution.

## Build & Commands

- Clean: `./mvnw clean -T1C`
- Lint: `./mvnw license:check spotless:check -T1C`
- Auto format: `./mvnw license:format spotless:apply -T1C`
- Build quickly: `./mvnw install -Dquickly -T1C`
- Run static checks: `./mvnw verify -Dquickly -DskipChecks=false -P'!autoFormat,checkFormat,spotbugs' -T1C`
- Run unit tests: `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C`
- Run integration tests: `./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C`
- Run all tests: `./mvnw verify -T1C`
- Build the docker image: `./mvnw install -DskipChecks -DskipTests -T1C && docker build -f camunda.Dockerfile --target app -t "camunda/camunda:current-test" --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .`

### Development Environment

- REST API: http://localhost:8080
- gRPC API: http://localhost:26500
- Management API: http://localhost:9600/actuator

## Code Style and Conventions

- Defined via the Maven Spotless plugin, to auto format run `./mvnw spotless:check -T1C`
- Follow conventions in `CONTRIBUTING.md` and all Markdown files in `docs/`

## Testing

- Follow guidelines in `docs/testing.md` and all files in `docs/testing/`.

## Security

- Use appropriate data types that limit exposure of sensitive information
- Never commit secrets or API keys to repository
- Use environment variables for sensitive data
- Use HTTPS in production
- Regular dependency updates
- Follow principle of least privilege

## Git Workflow

- ALWAYS lint and format before committing
- Auto fix formatting errors before committing
- NEVER use `git push --force` on the main branch
- Use `git push --force-with-lease` for feature branches if needed
- Always verify current branch before force operations
- Respect the commit guidelines in `CONTRIBUTING.md`

## GitHub Actions CI

- Always specify a comment with `# owner: TODO`
- Do not hard code secrets
- Do not use GitHub Secrets
- Use the Hashicorp Vault action to retrieve credentials and secrets
- For common set of steps, consider creating reusable workflows or composite actions to avoid duplication
- Avoid introducing usage of new 3rd party GitHub Actions
- For Maven jobs use the `setup-maven-cache` composite action
- For NodeJS jobs relying on Yarn use the https://github.com/camunda/infra-global-github-actions/tree/main/setup-yarn-cache
- Always specify `permissions: {}` on the GHA workflow job level of GHA workflows and use least permissions possible
- Always specify `bash` as the default shell for GHA workflows to ensure proper pipefail behavior
- Always specify `timeout-minutes` on GHA workflow jobs
- Run `actionlint` on GHA workflow modifications to make sure there are no warnings or errors
- Run `conftest test --rego-version v0 -o github --policy .github` on GHA workflow modifications to make sure there are no warnings or errors
- The last step of each GHA workflow job must submit CI health metrics by using:

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
- Always create composite actions in `.github/actions` subdirectories named in kebab case
- Always create a `README.md` for composite actions describing purpose, inputs, outputs and example usage

## Finding Documentation

- Always check whether documentation exists for the module you are working on
- If documentation exists in the module, read it before making changes to the code
- Check README.md and other markdown files in the directory to find documentation
- If you work on the workflow engine, check the engine [README](/zeebe/engine/README.md)
- Further documentation and development guidelines can be found in the `docs/` directory

