# Orchestration Cluster Project

The Orchestration Cluster is a distributed systems application with a Java backend and a React,
Carbon based frontend. Its purpose is to provide a robust, scalable process automation platform
using BPMN (Business Process Model and Notation) for process definition and execution.

## Build & Commands

- Build quickly: `./mvnw package -Dquickly -T1C`
- Install quickly: `./mvnw install -Dquickly -T1C`
- Lint: `./mvnw verify -DskipTests -T1C -Dquickly -DskipChecks=false`
- Auto format: `./mvnw process-sources -PautoFormat -T1C -Dquickly -Dspotless.apply.skip=false`
- Run unit tests: `./mvnw verify -DskipChecks -DskipITs -T1C -Dquickly`
- Run integration tests: `./mvnw verify -DskipChecks -DskipUTs -T1C -Dquickly`
- Run tests: `./mvnw verify -DskipChecks -T1C -Dquickly`
- Build the docker image: `./mvnw package -DskipTests -T1C -Dquickly && docker build -f camunda.Dockerfile --target app -t "camunda/camunda:current-test" --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .`

### Development Environment

- REST API: http://localhost:8080
- gRPC API: http://localhost:26500
- Management API: http://localhost:9600/actuator

## Code Style and Conventions

- Defined via the Maven Spotless plugin, run `./mvnw verify -DskipTests -T1C -Dquickly -Dspotless.checks.skip=false`
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

