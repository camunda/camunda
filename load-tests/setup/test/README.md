# Load test setup — golden file tests

Snapshot tests for the rendered Kubernetes manifests of the load test setup.

Each test scaffolds a setup version with `newLoadTest.sh` and renders the
platform, load-tester, and umbrella Helm charts through the **scaffolded
`Makefile`'s own `template*` targets** — the same value files and `--set` flags
that `make install` / `install-stable` use — then compares the output against
committed golden files. Rendering through the real Makefile (rather than
reimplementing the value composition) is deliberate: it keeps the test honest to
what actually gets deployed. The targets write via `helm template --output-dir`,
and the golden files mirror that output tree directly.

## Layout

Golden files mirror helm's `--output-dir` output — one file per chart template:

```
golden/<version>/<scenario>/<chart>/templates/<file>.yaml
golden/<version>/<scenario>/<chart>/charts/<subchart>/templates/<file>.yaml
```

- `<version>` — setup directory: `main`, `stable-89`, …
- `<scenario>` — storage/optimize/stable combos (`elasticsearch`, `opensearch`,
  `rdbms`, `rdbms-optimize`, `none`, and the `-stable` variants) plus the workload
  profiles `max` and `realistic`
- `<chart>` — `platform`, `load-tester`, `load-test-setup`

Workload scenarios (`max`, `realistic`) only change load-tester flags, so they
render just the `load-tester` chart on a fixed elasticsearch backend. `realistic`
pulls a values file from the `camunda-load-tests-helm` repo at render time — the
same live-latest policy as the load-tester chart, so regenerate its golden when
that file changes.

## About the credentials in these files

The `load-test-setup` chart's `Secret` manifests (`camunda-credentials.yaml`,
`load-test-credentials.yaml`) contain password-looking values. **These are not
real or sensitive credentials** — they are deterministic test fixtures, safe to
commit:

- They are produced by the chart at render time via Helm's `derivePassword`
  function, seeded only on the (fixed) test namespace and a key label. That makes
  them fully reproducible, which is the only reason they can be a golden snapshot
  at all.
- They exist **only in rendered test manifests**. This suite renders charts and
  diffs the output; it never deploys anything, so these values are never applied
  to any cluster and protect no real resource.

In short: the secrets under `golden/` are reproducible fixtures for verifying
chart rendering — nothing else.

## Commands

Run from this directory (requires `go`, `helm`, `git`, and network access):

```bash
make test            # verify: fails if rendered manifests differ from golden/
make update-golden   # regenerate golden files after an intentional change
make clean           # remove leftover scaffolded namespace directories
```

After `make update-golden`, always review `git diff golden/` before committing.
Never hand-edit golden `.yaml` files — only `make update-golden` applies the
correct normalization.

## Adding a stable version

Versions under test are listed explicitly in `golden_test.go`. Only `main` is
active; the stable entries are commented out. To enable one:

1. Uncomment its line in the `versions` slice.
2. `make update-golden` (or scope it: `go test -update-golden -run 'TestGoldenFiles/stable-89' ./...`).
3. Commit the generated golden files. No other code change is needed.

## If golden diffs become noisy in PRs

These files are generated, so a chart or values change can touch many of them at
once and dominate a PR's diff. If that becomes a review burden, mark the golden
tree as generated so GitHub collapses it in diffs (and excludes it from language
stats) via a `.gitattributes` entry, e.g.:

```
load-tests/setup/test/golden/** linguist-generated=true
```

See GitHub's [customizing how changed files appear](https://docs.github.com/en/repositories/working-with-files/managing-files/customizing-how-changed-files-appear-on-github)
and [Managing generated files in GitHub](https://medium.com/@clarkbw/managing-generated-files-in-github-1f1989c09dfd).
We have not applied this yet — the diffs are reviewed normally for now.
