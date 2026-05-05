# Read DB Versions

Parses `.ci/db-versions.yml` and exposes the configured database versions as
named outputs that downstream jobs can consume in service container image tags
or environment variables.

## Purpose

Centralises all database version pins in a single YAML file (`.ci/db-versions.yml`)
so that a version bump is a one-line change rather than a grep-and-replace across
multiple workflow files.

## Outputs

| Output          | Description                                                  |
|-----------------|--------------------------------------------------------------|
| `elasticsearch-8` | Latest supported ES 8 minor version (last entry in `es8` list) |
| `opensearch-2`  | Latest supported OpenSearch 2 minor version                  |
| `saas`          | Elasticsearch version currently deployed to SaaS environments |

## Example usage

```yaml
jobs:
  read-versions:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    outputs:
      elasticsearch-8: ${{ steps.db-versions.outputs.elasticsearch-8 }}
    steps:
      - uses: actions/checkout@v4
      - name: Read DB versions
        id: db-versions
        uses: ./.github/actions/read-db-versions

  test:
    needs: [read-versions]
    services:
      elasticsearch:
        image: docker.elastic.co/elasticsearch/elasticsearch:${{ needs.read-versions.outputs.elasticsearch-8 }}
```

## How to update a version

Edit `.ci/db-versions.yml`. The YAML anchor on the SaaS entry (`&saas`) ensures
`saas` always references a version that is also present in the `es8` test list —
update the anchor value when promoting SaaS to a new minor.

## Owner

@camunda/data-layer
