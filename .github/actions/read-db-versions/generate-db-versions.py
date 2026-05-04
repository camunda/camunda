"""
Reads .ci/db-versions.yml and writes every database version as a key=value
line to stdout, ready to be appended to $GITHUB_OUTPUT.

Run manually for testing:
    python3 .github/actions/read-db-versions/generate-db-versions.py
"""

import json
import sys
import yaml


def set_output(name, value):
    print(f"{name}={value}")


with open(".ci/db-versions.yml") as f:
    versions = yaml.safe_load(f)

# ── Search database versions ──────────────────────────────────────────────────
es8_versions = versions["elasticsearch"]["es8"]
es9_versions = versions["elasticsearch"]["es9"]
os2_versions = versions["opensearch"]["os2"]
os3_versions = versions["opensearch"]["os3"]

# Single-version outputs: last entry = newest supported minor version.
set_output("elasticsearch-8", es8_versions[-1])
set_output("elasticsearch-9", es9_versions[-1])
set_output("opensearch-2",    os2_versions[-1])
set_output("opensearch-3",    os3_versions[-1])
set_output("saas",            versions["elasticsearch"]["saas"])


# ── ES/OS matrix (zeebe-search-integration-tests.yml) ────────────────────────
# Min and max of each major series — oldest supported minor to catch regressions,
# newest to validate against the latest release. Intermediate versions are skipped
# to keep CI costs proportional to the number of supported minors.
# database-type encodes both the DB family and the version for test filtering.
def version_slug(v):
    return v.replace(".", "_").replace("-", "_")


def min_max(versions):
    """Return [first, last], deduplicated (single-entry lists return one item)."""
    return list(dict.fromkeys([versions[0], versions[-1]]))


es_os_matrix = {"include": []}

for v in min_max(es8_versions):
    es_os_matrix["include"].append({
        "database-name":          f"Elasticsearch {v}",
        "database-type":          f"elasticsearch8_{version_slug(v)}",
        "database-container":     "elasticsearch",
        "database-image-version": v,
        "it-database-type":       "es",
    })

for v in min_max(es9_versions):
    es_os_matrix["include"].append({
        "database-name":          f"Elasticsearch {v}",
        "database-type":          f"elasticsearch9_{version_slug(v)}",
        "database-container":     "elasticsearch",
        "database-image-version": v,
        "it-database-type":       "es",
    })

for v in min_max(os2_versions):
    es_os_matrix["include"].append({
        "database-name":          f"OpenSearch {v}",
        "database-type":          f"opensearch2_{version_slug(v)}",
        "database-container":     "opensearch",
        "database-image-version": v,
        "it-database-type":       "os",
    })

for v in min_max(os3_versions):
    es_os_matrix["include"].append({
        "database-name":          f"OpenSearch {v}",
        "database-type":          f"opensearch3_{version_slug(v)}",
        "database-container":     "opensearch",
        "database-image-version": v,
        "it-database-type":       "os",
    })

set_output("es-os-matrix", json.dumps(es_os_matrix))

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
        "name":                   "Elasticsearch 9",
        "database-type":          "elasticsearch9",
        "database-container":     "elasticsearch",
        "database-image-version": es9_versions[-1],
        "database-name":          "Elasticsearch 9",
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
        "name":                   "Opensearch 3",
        "database-type":          "opensearch3",
        "database-container":     "opensearch",
        "database-image-version": os3_versions[-1],
        "database-name":          "OpenSearch 3",
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

# ── RDBMS matrix (zeebe-rdbms-integration-tests.yml) ─────────────────────────
# Maps the first segment of an Oracle image tag to its display name and
# database-type. Oracle versions use non-numeric suffixes (21c, 23ai) that
# cannot be derived mechanically from the version number alone.
oracle_meta = {
    "23": {"name": "Oracle 23ai", "type": "oracle"},
    "21": {"name": "Oracle 21c",  "type": "oracle-21"},
}

rdbms_matrix = {"include": []}

for v in versions["postgresql"]:
    major = v.split("-")[0]
    rdbms_matrix["include"].append({
        "database-name":          f"Postgres {major}",
        "database-type":          "postgres",
        "database-image-version": v,
        "it-database-type":       "rdbms_postgres",
    })

for v in versions["mysql"]:
    rdbms_matrix["include"].append({
        "database-name":          f"MySQL {v}",
        "database-type":          "mysql",
        "database-image-version": v,
        "it-database-type":       "rdbms_mysql",
    })

for v in versions["mariadb"]:
    rdbms_matrix["include"].append({
        "database-name":          f"MariaDB {v}",
        "database-type":          "mariadb",
        "database-image-version": v,
        "it-database-type":       "rdbms_mariadb",
    })

for v in versions["mssql"]:
    year = v.split("-")[0]
    rdbms_matrix["include"].append({
        "database-name":          f"MSSQL {year}",
        "database-type":          "mssql",
        "database-image-version": v,
        "it-database-type":       "rdbms_mssql",
    })

for v in versions["oracle"]:
    major = v.split("-")[0]
    meta  = oracle_meta.get(major, {"name": f"Oracle {major}", "type": "oracle"})
    rdbms_matrix["include"].append({
        "database-name":          meta["name"],
        "database-type":          meta["type"],
        "database-image-version": v,
        "it-database-type":       "rdbms_oracle",
    })

set_output("rdbms-matrix", json.dumps(rdbms_matrix))
