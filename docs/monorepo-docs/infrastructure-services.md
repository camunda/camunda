# Infrastructure Services

The services below are required by the monorepo, and are owned by the Infrastructure team
([#ask-infra](https://camunda.slack.com/archives/C5AHF1D8T)).

## Secret Management: Vault

**URL:** [https://vault.int.camunda.com/](https://vault.int.camunda.com/ui/)

A self-hosted [Hashicorp Vault](https://www.hashicorp.com/en/products/vault) instance.

For the monorepo, it is used to store secrets for GHA workflows (the use of standard GitHub Actions
Secrets is discouraged).
[Example usage](https://github.com/camunda/camunda/blob/4630b0e8830b146e796c09ee298df602fb449420/.github/workflows/preview-env-build-and-deploy.yml#L130).

Relevant internal docs:

- [CI Secret Management](./ci.md#ci-secret-management)
- [Vault Secret Management](https://confluence.camunda.com/spaces/HAN/pages/92446926/Vault+Secret+Management)
- [CI Secrets Self-Service](https://confluence.camunda.com/spaces/HAN/pages/92447508/CI+Secrets+Self-Service)

## CI: Self-Hosted GitHub Actions Runners

Self-hosted runners are used to provide larger-than-standard GHA runners at lower costs than GitHub
would charge.

The provided runners are autoscaled based on demand and run in Kubernetes clusters (GCP, AWS). The
monorepo uses the prod environment of the runners.

[Example usage](https://github.com/camunda/camunda/blob/4630b0e8830b146e796c09ee298df602fb449420/.github/workflows/optimize-release-optimize-c8-only.yml#L41).

Relevant internal docs:

- [GitHub Actions self-hosted runners](https://confluence.camunda.com/spaces/HAN/pages/123308536/Github+Actions+Self-Hosted+Runners)
  — includes the list of available runners.
- [Monorepo: Self-Hosted Runners](./ci.md#ci-self-hosted-runners)

## Dependency Management: Renovate

[Renovate](https://docs.renovatebot.com/configuration-options/) is used to create/merge PRs to
bump dependencies. The SaaS version is used.

- [Example PR](https://github.com/camunda/camunda/pull/32706)

Relevant documentation:

- [Renovate](./ci.md#renovate)

## Monitoring (CI Analytics)

The monorepo uses the prod environment of the following services.

### BigQuery

The [metrics collection](./ci.md#metrics-collection) from the monorepo CI (CI Analytics) uses
[GCP BigQuery](https://console.cloud.google.com/bigquery?inv=1&invt=AbyyIA&project=ci-30-162810&ws=!1m0)
as a data sink.

### Prometheus

Metric collection system.

**URL:** [https://monitor.int.camunda.com/](https://monitor.int.camunda.com/)

- Collects metrics from various sources, e.g. self-hosted runners and GitHub merge queue.
- Alerting rules can be set up for metric violations.

### Grafana

Metric visualization system. Uses Prometheus and BigQuery as data sources.

**URL:** [https://dashboard.int.camunda.com/](https://dashboard.int.camunda.com/)

Relevant links:

- [Dashboard links](./ci.md#visualization) for CI analytics data visualization (e.g. flaky tests).

## Infra Global GitHub Actions

The monorepo CI makes use of the
[camunda/infra-global-github-actions](https://github.com/camunda/infra-global-github-actions)
reusable workflows and composite actions to avoid duplicating pipeline code and share functionality
with other parts of Engineering.
