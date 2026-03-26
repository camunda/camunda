# Directory Structure History

The load test infrastructure was restructured in **8.9**, moving from `zeebe/benchmarks/` to a top-level `load-tests/` directory.

Understanding the directory layout per version is important when backporting changes to maintenance branches.

## Directory mapping

| Component | stable/8.6, 8.7, 8.8 | stable/8.9+ / main |
|---|---|---|
| Helm values files | `zeebe/benchmarks/` | `load-tests/` |
| Load tester Java code | `zeebe/benchmarks/project/` | `load-tests/load-tester/` |
| Setup scripts | `zeebe/benchmarks/setup/` | `load-tests/setup/` |
| Documentation | `zeebe/benchmarks/docs/` | `load-tests/docs/` |

## Other differences across versions

| Feature | stable/8.6-8.7 | stable/8.8 | stable/8.9+ / main |
|---|---|---|---|
| Docker image build job name in CI | `build-zeebe-image` | `build-camunda-image` | `build-camunda-image` |
| Identity, Optimize, Keycloak in base values | disabled | disabled | enabled |
| Number of Helm values file variants | 2-3 | 4 | 6+ |
| `camunda-pr-load-test.yaml` workflow | not available | not available | available |
| `zeebe-benchmark.yml` workflow | available | available | removed |
| `zeebe-pr-benchmark.yaml` workflow | available | available | removed |
| Cloud load test support in setup scripts | not available | not available | available |

## Backporting

Cherry-picks from `main` to stable/8.6-8.8 will produce modify/delete conflicts because the files were renamed. Always resolve by applying the intended change to the correct path on the target branch (`zeebe/benchmarks/` instead of `load-tests/`).

For more details on backporting conventions, see the [reliability testing documentation](../../docs/testing/reliability-testing.md).
