# adjust-pom-for-license-analysis

Adjusts `pom.xml` files so that Maven analysis tools (FOSSA, depgraph, etc.) can correctly resolve
the full module hierarchy from `bom/pom.xml` as the root.

## Purpose

The monorepo's Maven structure uses `bom/pom.xml` and `parent/pom.xml` as separate top-level
modules. Analysis tools that expect a single root POM need these files adjusted so that:

- `parent/pom.xml` includes the repo root (`./..`) as a module
- `bom/pom.xml` includes `parent` (`"./../parent"`) as a module
- The root `pom.xml` no longer lists `bom` and `parent` as modules (to avoid circular references)
- `optimize/pom.xml` excludes the `qa` module (FOSSA workaround)

It also strips `test`, `provided` and `system` scoped dependencies from every `pom.xml`
(via `strip-analysis-scopes.py`). FOSSA runs `depgraph:aggregate` with
`-DmergeScopes -DrepeatTransitiveDependenciesInTextGraph=true`, which repeats those dependencies
under every path in the graph; on a monorepo this size the generated text graph can exceed the
JVM's maximum array length (~2 GiB) and crash the analyzer with an `OutOfMemoryError`. These
scopes are not part of any shipped artifact and are already excluded from FOSSA's results via
`maven.scope-exclude` in `.fossa.yml`, so removing them up front keeps the graph small and
deterministic without changing the reported licenses.

## Inputs

None.

## Outputs

None.

## Example usage

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/adjust-pom-for-license-analysis
```

## Owner

@camunda/engineering-operations
