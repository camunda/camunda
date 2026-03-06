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

@camunda/monorepo-devops-team
