# Contributing

- [Issues and PRs](#issues-and-prs)
- [Backporting](#backporting)
- [Submitting a pull request](#submitting-a-pull-request)
  - [Helm version](#helm-version)
  - [Best Practices](#best-practices)
  - [Commit Guidelines](#commit-guidelines)
  - [Tests](#tests)
  - [Documentation](#documentation)
- [CI](#ci)
- [Integration Testing](#integration-testing)
  - [Overview](#overview)
  - [Scenarios](#scenarios)
  - [The deploy-camunda CLI](#the-deploy-camunda-cli)
  - [Requirements](#requirements)
  - [Running E2E Tests After Deployment](#running-e2e-tests-after-deployment)
  - [Troubleshooting](#troubleshooting)
- [Resources](#resources)

[fork]: /fork
[pr]: /compare
[CODE_OF_CONDUCT]: CODE_OF_CONDUCT.md

We're thrilled that you'd like to contribute to this project. Your help is essential for keeping it great.

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/camunda/camunda-platform-helm/blob/main/CODE_OF_CONDUCT.md).
By participating in this project you agree to abide by its terms.

## Issues and PRs

If you have suggestions for how this project could be improved, or want to report a bug, open an issue! We'd love all and any contributions.
If you have questions, too, we'd love to hear them.

We'd also love PRs. If you're thinking of a large PR, we advise opening up an issue first to talk about it, though!
Look at the links below if you're not sure how to open a PR.

## Backporting

Camunda enterprise covers the last three supported minor versions. Hence all fixes should be also backported to the supported versions.
We are using a directory-based structure which means all supported will be under the [charts directory](./../charts/).

In the charts directory, the latest supported chart doesn't have a version in its directory name like `camunda-platform`.
The previous releases have the Camunda version in their directory name e.g. `camunda-platform-8.4`.

Please note that the version mentioned here is the Camunda app version (in the Chart.yaml file represented by the key `version`),
not the chart version (in the Chart.yaml file represented by the key `version`).

## Submitting a pull request

Please feel free to fork this repository and open a pull request to fix an issue or add a new feature.

We have the following expectations on the PR's:

- They follow the [best practices](#best-practices)
- They contain new [tests](#tests) on a bug fix or on adding a new feature
- They follow the [commit guidelines](#commit-guidelines)
- The [documentation](#documentation) has been updated, if necessary.


### Helm version

To have a smooth contribution experience, before working on a new PR make sure to use the exact Helm version
that's currently used in the repo.

Helm version is set in the [.tool-versions](./.tool-versions) file, so you can use the [asdf version manager](https://github.com/asdf-vm/asdf)
to install Helm locally or just install the same version manually.

To install the Helm version that's used in this repo using `asdf`, in the repo root, run:

```
make tools.asdf-install
```

### Best Practices

Make sure you're familiar with some Helm best practices like:

- https://helm.sh/docs/chart_best_practices/
- https://codersociety.com/blog/articles/helm-best-practices

### Commit Guidelines

Commit messages should follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) format.

For example:

```
fix: set correct port

Previously no metrics have been exposed because the wrong port was used. This commit fixes the post and set it to 9600.
```

Available commit types:

- `feat` - enhancements, new features
- `fix` - bug fixes
- `refactor` - non-behavior changes
- `test` - only changes in tests
- `docs` - changes in the documentation, readme, etc.
- `style` - apply code styles
- `build` - changes to the build (e.g. to Maven's `Chart.yaml`)
- `ci` - changes to the CI (e.g. to GitHub-related configs)

### Tests

> [!NOTE]
>
> For more details about Helm chart testing read the following blog post:
> [Advanced Test Practices For Helm Charts](https://medium.com/@zelldon91/advanced-test-practices-for-helm-charts-587caeeb4cb).

In order to make sure that the Helm charts work properly and that further development doesn't break anything we introduced tests for the Helm charts.
The tests are written in Golang, and we use the [Terratest framework](https://terratest.gruntwork.io/) to write them.

We separate our tests into two parts, with different targets and goals.

1. **Template tests** (unit tests), verify the general structure. Is it YAML conform, has it the right value/structure if set, do the default values not change or are set at all?
2. **Integration tests**, verify whether I can install the charts and use them. This means, are the manifests accepted by the K8 API and does it work? (it can be valid YAML but not accepted by K8s). Can the services reach each other and are they working?

**For new contributions it is expected to write new unit tests, but no integration tests.** We keep the count of integration tests to a minimum, and the knowledge for writing them is not expected for contributors.

Tests can be found in the chart directory under `test/`. For each component, we have a sub-directory
in the `test/` directory.

To run the tests, execute `make go.test` on the root repository level.

#### Unit Tests

As mentioned earlier, we expect unit tests on new contributions. The unit tests (template tests) are divided into two parts,
golden file tests and explicit property tests. In this section, we want to explain when which test type should be used.

##### Golden Files

We write new golden file tests, for default values, where we can compare a complete manifest with his properties.
Most of the golden file tests are part of the `goldenfiles_test.go` to the corresponding sub-chart testing directory.
For an example see `/test/zeebe/goldenfiles_test.go`.

If the complete manifest can be enabled by a toggle, we also write a golden file test. This test is part of `<manifestFileName>_test.go` file. The `<manifestFileName>` corresponds to the template filename we have in the sub-chart `templates` dir. For example, the Prometheus `templates/service-monitor.yaml` can be enabled by a toggle. This means we write a golden file test in `test/servicemonitor_test.go`.

To generate the golden files run `go.test-golden-updated` on the root level of the repository.
This will add a new golden file in a `golden` sub-dir and run the corresponding test. The golden files should also be named related to the manifest.

##### Properties Test

For things that are not per default enabled or set we write a property test.

Here we directly set the specific property/variable and verify that the Helm chart can be rendered and the property is set correctly on the object. This kind of test should be part of a `<manifestFileName>_test.go` file. The `<manifestFileName>` corresponds to the template filename we have in the sub-chart `templates` dir. For example, for the Zeebe statefulset manifest we have the test `test/zeebe/statefulset_test.go` under the `zeebe` sub-dir.

It is always helpful to check already existing tests to get a better understanding of how to write new tests, so do not hesitant to read and copy them.

#### Test License Headers

Make sure that new go tests contain the Apache license headers, otherwise, the CI license check will fail. For adding and checking the license we use [addlicense](https://github.com/google/addlicense). In order to install it locally, simply run `make go.addlicense-install`. Afterward, you can run `make go.addlicense-run` to add the missing license header to a new go file.

### Documentation

The `values.yaml` file follows Helm's best practices https://helm.sh/docs/chart_best_practices/values/

This means:

- Variable names should begin with a lowercase letter, and words should be separated with a camelcase.
- Every defined property in values.yaml should be documented. The documentation string should begin with the name of the property that it describes,
  and then give at least a one-sentence description

We are using [bitnami/readme-generator-for-helm](https://github.com/bitnami/readme-generator-for-helm)
to generate the Helm chart values docs from the values file. Ensure to follow the same convention of the tool.

## CI

CI is performed via GitHub Actions [workflow](.github/workflows).

## Integration Testing

Integration tests verify that Helm charts can be deployed to Kubernetes and that services work correctly together. Unlike unit tests (which are expected for all contributions), **integration tests are primarily maintained by the Camunda team** and require access to Kubernetes infrastructure.

> [!NOTE]
>
> **For community contributors:** You are not expected to run integration tests. The CI pipeline handles this automatically. This section is provided for transparency and for those who want to understand or contribute to the testing infrastructure.

### Overview

Integration tests deploy the Camunda Platform to a real Kubernetes cluster using predefined **scenarios**. Each scenario is a set of Helm values that configure a specific deployment topology (e.g., with Keycloak authentication, Elasticsearch, OpenSearch, multi-tenancy, etc.).

The `deploy-camunda` CLI tool automates the deployment process, handling:
- Helm values file preparation and merging
- Unique identifier generation (Keycloak realms, Elasticsearch index prefixes)
- Secret management and credential injection
- Parallel deployment of multiple scenarios

### Scenarios

Scenarios are YAML values files located in each chart's test directory:

```
charts/<version>/test/integration/scenarios/chart-full-setup/
```

Files follow the naming convention: `values-integration-test-ingress-<scenario-name>.yaml`

**Available scenarios include:**

| Scenario | Description |
|----------|-------------|
| `keycloak-original` | Full deployment with Keycloak authentication and external elasticsearch |
| `elasticsearch` | Deployment using Elasticsearch for data storage |
| `opensearch` | Deployment using OpenSearch for data storage |
| `multitenancy` | Multi-tenant configuration |
| `qa-elasticsearch` | QA-specific configuration with custom image tags |
| `qa-opensearch` | QA-specific OpenSearch configuration |

Scenario files can reference environment variables (e.g., `$CAMUNDA_HOSTNAME`, `$E2E_TESTS_ZEEBE_IMAGE_TAG`) that are substituted at deployment time.

### The deploy-camunda CLI (alpha)

The `deploy-camunda` CLI is located at `scripts/deploy-camunda/` and provides a streamlined way to deploy Camunda Platform for integration testing.

#### Installation

From the repository root:

```bash
cd scripts/deploy-camunda
go build -o deploy-camunda .
```

Or with make:
```bash
make install.dx-tooling
```

Or run directly:

```bash
go run scripts/deploy-camunda/main.go [flags]
```

#### Basic Usage

```bash
# Deploy a single scenario
deploy-camunda --chart-path ./charts/camunda-platform-8.6 \
  --namespace my-test-ns \
  --release integration \
  --scenario keycloak

# Deploy multiple scenarios in parallel
deploy-camunda --chart-path ./charts/camunda-platform-8.6 \
  --namespace my-test-ns \
  --release integration \
  --scenario keycloak,elasticsearch
```

#### Key Flags

| Flag | Short | Description |
|------|-------|-------------|
| `--chart-path` | | Path to the Camunda chart directory |
| `--chart` | `-c` | Chart name (for remote charts) |
| `--version` | `-v` | Chart version (requires `--chart`) |
| `--namespace` | `-n` | Kubernetes namespace |
| `--release` | `-r` | Helm release name |
| `--scenario` | `-s` | Scenario name(s), comma-separated for parallel deployment |
| `--scenario-path` | | Custom path to scenario files |
| `--auth` | | Auth scenario (default: `keycloak`) |
| `--platform` | | Target platform: `gke`, `rosa`, `eks` (default: `gke`) |
| `--timeout` | | Helm deployment timeout in minutes (default: 5) |
| `--delete-namespace` | | Delete namespace before deploying |
| `--auto-generate-secrets` | | Generate random test secrets |
| `--render-templates` | | Render manifests without installing |
| `--extra-values` | | Additional values files to apply |
| `--config` | `-F` | Path to config file |

#### Configuration File

Instead of passing flags every time, you can create a configuration file at `.camunda-deploy.yaml` (project-level) or `~/.config/camunda/deploy.yaml` (global).

**Example configuration:**

```yaml
# Global defaults
repoRoot: /path/to/camunda-platform-helm
platform: gke
logLevel: info

# Keycloak settings (for Camunda internal infrastructure)
keycloak:
  host: keycloak.example.com
  protocol: https

# Named deployment profiles
deployments:
  local-test:
    chartPath: ./charts/camunda-platform-8.6
    namespace: camunda-test
    release: integration
    scenario: keycloak
    
  qa-full:
    chartPath: ./charts/camunda-platform-8.6
    namespace: qa-integration
    release: integration
    scenario: qa-elasticsearch
    valuesPreset: enterprise

# Set the active deployment
current: local-test
```

**Using deployment profiles:**

```bash
# List configured deployments
deploy-camunda config list

# Switch active deployment
deploy-camunda config use qa-full

# Show merged configuration
deploy-camunda config show

# Run with active deployment settings
deploy-camunda
```

#### Environment Variables

The CLI supports environment variable overrides with the `CAMUNDA_` prefix:

| Variable | Description |
|----------|-------------|
| `CAMUNDA_CURRENT` | Active deployment profile |
| `CAMUNDA_REPO_ROOT` | Repository root path |
| `CAMUNDA_PLATFORM` | Target platform |
| `CAMUNDA_HOSTNAME` | Ingress hostname |
| `CAMUNDA_KEYCLOAK_HOST` | External Keycloak host |
| `CAMUNDA_KEYCLOAK_REALM` | Keycloak realm name |

You can also use a `.env` file (loaded automatically or via `--env-file`):

```bash
CAMUNDA_HOSTNAME=my-cluster.example.com
DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD=secretpassword
```

### Requirements

#### For Camunda Employees

Internal integration tests use shared infrastructure:

- **Kubernetes clusters**: GKE, EKS, or ROSA with appropriate permissions
- **External Keycloak**: Shared Keycloak instance for authentication
- **External Elasticsearch**: Shared Elasticsearch cluster
- **Vault secrets**: Credentials managed via HashiCorp Vault
- **Docker registry access**: Enterprise image pull secrets

Contact the Platform team for access to shared infrastructure and required credentials.

#### For Community Contributors

To run integration tests independently, you need:

1. **Kubernetes cluster**: Any Kubernetes cluster (minikube, kind, cloud provider)
2. **Helm 3.x**: Same version as specified in `.tool-versions`
3. **kubectl**: Configured to access your cluster
4. **Docker registry secrets**: For pulling Camunda images (if using enterprise features)

**Minimal local setup:**

```bash
# Create a kind cluster
kind create cluster --name camunda-test

# Deploy with auto-generated secrets (no external dependencies)
go run scripts/deploy-camunda/main.go \
  --chart-path ./charts/camunda-platform-8.6 \
  --namespace camunda \
  --release integration \
  --scenario elasticsearch \
  --auto-generate-secrets \
  --skip-dependency-update=false
```

### Running E2E Tests After Deployment

Once deployed, E2E tests can be run using the provided script:

```bash
./scripts/run-e2e-tests.sh \
  --absolute-chart-path /path/to/charts/camunda-platform-8.6 \
  --namespace my-test-ns
```

Additional flags:
- `--opensearch`: Run OpenSearch-specific tests
- `--mt`: Run multi-tenancy tests
- `--rba`: Run role-based access tests
- `--run-smoke-tests`: Run smoke tests only
- `--verbose`: Show detailed output

### Troubleshooting

**Scenario not found:**

The CLI provides helpful error messages listing available scenarios:

```
Scenario "invalid-name" not found

Searched in: charts/camunda-platform-8.6/test/integration/scenarios/chart-full-setup
Expected file: values-integration-test-ingress-invalid-name.yaml

Available scenarios (10 found):
  - keycloak
  - elasticsearch
  - opensearch
  ...
```

**Helm timeout:**

Increase the timeout for slower clusters:

```bash
deploy-camunda --timeout 15 ...
```

**Viewing rendered manifests:**

Use `--render-templates` to inspect what would be deployed:

```bash
deploy-camunda --render-templates --render-output-dir ./rendered ...
```

## Resources

- [How to Contribute to Open Source](https://opensource.guide/how-to-contribute/)
- [Using Pull Requests](https://help.github.com/articles/about-pull-requests/)
- [GitHub Help](https://help.github.com)
- [CODE_OF_CONDUCT](https://github.com/camunda/camunda-platform-helm/blob/main/CODE_OF_CONDUCT.md)
