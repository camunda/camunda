# Camunda 8 Helm

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://opensource.org/licenses/Apache-2.0)
[![Artifact Hub](https://img.shields.io/badge/Artifact%20Hub-Camunda-417598?logo=artifacthub&logoColor=fff&style=flat-square)](https://artifacthub.io/packages/helm/camunda/camunda-platform)
[![Helm Chart Version Matrix](https://img.shields.io/badge/Helm_Chart-Version_Matrix-0F1689?logo=helm&logoColor=fff&style=flat-square)](https://helm.camunda.io/camunda-platform/version-matrix/)

> [!CAUTION]
>
> This GitHub repository is mainly for development, don't use it directly to deploy Camunda. End users should use the [official documentation](https://docs.camunda.io/docs/self-managed/about-self-managed/) for [installation](https://docs.camunda.io/docs/self-managed/deployment/helm/install/), [upgrade](https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/), etc.

- [Overview](#overview)
- [Documentation](#documentation)
- [Versioning and Release Cycles](#versioning-and-release-cycles)
- [Installation](#installation)
- [Guides](#guides)
- [Issues](#issues)
- [Contributing](#contributing)
- [Releasing](#releasing)
- [License](#license)

## Overview

Camunda 8 Self-Managed Helm charts repo. Camunda 8 Helm chart is an umbrella chart
for different components. Some are internal components, and some are external (third-party).
The dependency management is fully automated and managed by Helm itself.

## Documentation

- Official docs (to install the platform via Helm chart): [Camunda 8 Self-Managed](https://docs.camunda.io/docs/self-managed/about-self-managed/).
- This repo docs (e.g., tests, releases, etc.): [Camunda 8 Helm chart repo docs](./docs).

## Versioning and Release Cycles

For more details about the Camunda 8 Helm chart versioning, please read the [versioning scheme](https://docs.camunda.io/docs/self-managed/setup/install/#versioning).

Also, check the [chart release cycles](./charts/README.md#release-cycles) for more details.

## Installation

Find more installation and deployment options
on the [Camunda 8 Helm chart installation](https://docs.camunda.io/docs/self-managed/setup/install/#versioning).

## Guides

Default values cannot cover every use case, so we have
[Camunda 8 deploy guides](https://docs.camunda.io/docs/self-managed/setup/guides/).
The guides have detailed examples of use cases, such as Ingress setup.

## Issues

If you find any problem with the Camunda 8 Helm charts, create a [new issue](https://github.com/camunda/camunda-platform-helm/issues).

## Contributing

We value all feedback and contributions. To start contributing to this project, please:

- **Don't create a PR without opening [an issue](https://github.com/camunda/camunda-platform-helm/issues/new/choose)
  and discussing it first.**
- Familiarize yourself with the
[contribution guide](./docs/contributing.md).
- Find more information about the Camunda 8 [Helm charts](./charts).

## Releasing

Please visit the [Camunda 8 release guide](./docs/release.md) to find out how to release the charts.

## License

Camunda 8 Self-Managed Helm charts are licensed under the open-source Apache License 2.0.
Please see [LICENSE](LICENSE) for details.

For Camunda 8 components, please visit
the [licensing information page](https://docs.camunda.io/docs/reference/licenses).
