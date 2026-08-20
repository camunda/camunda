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

Scope either target to one setup version with `PATTERN=`, e.g. when porting a fix to a
single stable branch's golden files:

```bash
make update-golden PATTERN=stable-89
```

After `make update-golden`, always review `git diff golden/` before committing.
Never hand-edit golden `.yaml` files — only `make update-golden` applies the
correct normalization.

## Adding a stable version

Versions under test are listed explicitly in the `versions` slice in
`golden_test.go`. To add a new version:

1. Add its directory name (e.g. `"stable-90"`) to the `versions` slice.
2. `make update-golden PATTERN=stable-90`.
3. Commit the generated golden files. No other code change is needed.

## Narrowing a scenario to a subset of files (`PathFilter`)

Some scenarios exist only to verify one narrow area — e.g. a scenario added
specifically to catch a regression in one template. Committing (and diffing)
the full rendered tree for those isn't worth the review noise when only a
handful of files are actually relevant.

Set `PathFilter` on a `scenario` in `golden_test.go` to a list of path
prefixes, relative to each chart's own output tree:

```go
{Name: "elasticsearch-no-optimize", Storage: "elasticsearch", Optimize: false,
	PathFilter: []string{"templates/orchestration"}},
```

Only rendered manifests under those prefixes are compared or committed for
that scenario — everything else is skipped, for every chart the scenario
renders. A non-empty `PathFilter` must match at least one rendered manifest for
each chart it renders; otherwise the test fails. That makes typos or filters
scoped to the wrong chart visible instead of silently removing coverage. Leave
`PathFilter` unset (the default) to keep comparing every rendered file, exactly
like today.

## Why golden diffs are collapsed in PRs

Golden files under `golden/` are generated render snapshots, not hand-authored
source. The repository marks them as generated in `.gitattributes`:

```
load-tests/setup/test/golden/** linguist-generated=true
```

GitHub therefore collapses these files in PR diffs by default and excludes them
from language statistics. This keeps reviews focused on the chart, value, or
Makefile change that produced the snapshot update while still allowing
reviewers to expand the generated files when they need to inspect the rendered
manifest changes.

`PathFilter` is still useful when a scenario genuinely needs to verify only one
narrow area: it reduces the committed snapshot tree and the test comparison
scope. Do not add a `PathFilter` only to hide noisy diffs; the generated-file
attribute already handles that.
