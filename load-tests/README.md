# Camunda Load Tests

Load tests validate the reliability and performance of Camunda 8 across releases and development branches. They can be created via automated GitHub Actions workflows or manually (via Makefiles) on a GKE cluster (`camunda-benchmark-prod`), deploying the [Camunda Platform Helm Chart](https://github.com/camunda/camunda-platform-helm) and a custom [load test Helm chart](https://github.com/camunda/camunda-load-tests-helm).

For background on goals and test variants, see the [reliability testing documentation](../docs/testing/reliability-testing.md).

## Directory Layout

|   Directory    |                                                            Description                                                            |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `load-tester/` | Java load test applications (starters and workers) ([README](load-tester/README.md))                                              |
| `docs/`        | Additional documentation: [metrics](docs/metrics.md), [scripts](docs/scripts/README.md), [past failures](docs/failures/README.md) |

> [!CAUTION]
> The `setup` folder in the branch `stable/8.10` does not exist.
> To deploy a load test for the Camunda Platform 8.10, use the `stable-810` folder from the `main` branch instead.

---

