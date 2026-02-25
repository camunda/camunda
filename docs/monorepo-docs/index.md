# Camunda Platform Developer Documentation

Welcome to the developer documentation for the Camunda platform and orchestration cluster.

## Documentation for developers of Camunda 8

This site contains comprehensive documentation for developers of Camunda 8's development, CI, and release process:

:::tip
If you're looking for product documentation about Camunda 8, check out https://docs.camunda.io.
There you'll find anything to get started using Camunda 8, best practices, and reference documentation.
:::

### Collaboration

**[Collaboration guidelines](./collaboration-guidelines.md)** - Guidelines for collaborating on CI/CD, releases, and monorepo infrastructure. All teams are welcome to contribute and improve these processes:

- Core collaboration principles and ownership boundaries
- When and how to reach out to the Monorepo DevOps team for support
- Heads-Up Pattern for scoping and requests
- Code review and integration expectations
- How all teams can independently own and maintain their workflows

### CI & Automation

**[CI & Automation](./ci.md)** - Complete guide to continuous integration and automation processes including:

- Git branch strategy and SNAPSHOT artifacts
- Issue tracking and prioritization
- GitHub Merge Queue configuration
- Unified CI implementation and workflow inclusion criteria
- Test file ownership and naming conventions
- Renovate dependency automation
- CI health metrics and monitoring
- Security best practices and secret management
- Self-hosted runners and caching strategies
- Preview environments and backporting guidelines
- ChatOps commands and flaky test handling
- Comprehensive troubleshooting guide

### CI Runbooks

**[CI Runbooks](./ci-runbooks.md)** - Runbooks for responding to CI incidents and alerts including:

- Incident runbooks: checking status pages, disabling flaky tests, bypassing the merge queue
- Alert runbooks for merge queue failures, high job runtimes, self-hosted runner disconnects
- Snapshot artifact staleness and missing artifact alerts
- Helm chart integration test failures and preview environment smoke test failures

### Infrastructure Services

**[Infrastructure Services](./infrastructure-services.md)** - Overview of the infrastructure services
used by the monorepo, owned by the Infrastructure team (`#ask-infra`):

- Secret management with Hashicorp Vault
- Self-hosted GitHub Actions runners (GCP/AWS, autoscaled)
- Dependency management with Renovate
- CI analytics monitoring with BigQuery, Prometheus, and Grafana
- Infra Global GitHub Actions reusable workflows and composite actions

### Renovate PR Handling

**[Renovate PR Handling](./renovate-pr-handling.md)** - Process and responsibilities for handling
Renovate dependency update PRs that require manual intervention:

- How DRIs are assigned to open Renovate PRs based on expertise
- DRI responsibilities: resolving breaking changes, follow-up tickets, Renovate config improvements
- Assignment automation via GitHub Actions (in progress)

### Release Process

**[Release Process](./release.md)** - Complete software release procedures and guidelines including:

- Release types (Minor, Alpha, Patch) and artifacts
- BPMN-based release process implementation
- GitHub Action workflows and automation
- Benchmark tests and testing clusters
- Change management and DRI responsibilities
- Minor release considerations and learnings
- Incident process and troubleshooting procedures
- FAQ covering common release scenarios

## Platform Components

The Camunda orchestration cluster includes:

- **Zeebe** - Cloud-native process engine
- **Tasklist** - Human task management
- **Operate** - Process monitoring and management
- **Identity** - Authentication and access control
- **Optimize** - Process analytics and optimization

## Getting Help

- [GitHub Issues](https://github.com/camunda/camunda/issues) - Report bugs and request features
- [User Forum](https://forum.camunda.io) - Community support and discussions
- [Documentation Home](https://docs.camunda.io) - Official documentation portal

---

*This documentation is continuously updated. If you find issues or have suggestions, please contribute via GitHub.*
