"""
Reads .ci/db-versions.yml and writes the configured database versions as key=value
lines to stdout, ready to be appended to $GITHUB_OUTPUT.
"""

import json
import yaml


def set_output(name, value):
    print(f"{name}={value}")


with open(".ci/db-versions.yml") as f:
    versions = yaml.safe_load(f)

es8_versions = versions["elasticsearch"]["es8"]
os2_versions = versions["opensearch"]["os2"]

set_output("elasticsearch-8", es8_versions[-1])
set_output("opensearch-2",    os2_versions[-1])
set_output("saas",            versions["elasticsearch"]["saas"])

# ── CI database integration test matrix (ci.yml) ─────────────────────────────
# One entry per major series using the latest supported minor, plus a static
# H2 entry. H2 is an embedded database with no Docker image version to track.
ci_db_matrix = {"include": [
    {
        "name":                   "Elasticsearch 8",
        "database-type":          "elasticsearch",
        "database-image-version": es8_versions[-1],
        "database-name":          "Elasticsearch 8",
        "it-database-type":       "es",
    },
    {
        "name":                   "Opensearch 2",
        "database-type":          "opensearch2",
        "database-container":     "opensearch",
        "database-image-version": os2_versions[-1],
        "database-name":          "OpenSearch 2",
        "it-database-type":       "os",
    },
    {
        "name":             "RDBMS H2",
        "database-type":    "h2",
        "database-name":    "H2",
        "it-database-type": "rdbms_h2",
    },
]}

set_output("ci-database-matrix", json.dumps(ci_db_matrix))
