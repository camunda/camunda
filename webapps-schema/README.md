### Schema compatibility guidelines (Elasticsearch/OpenSearch)

As new features are added, the indices storing data evolve. To maintain **zero required data migrations** across versions, follow these schema guidelines.

#### Schema changes to avoid

The following **breaking changes** require data migrations and must be avoided:

- **Field removal**: Deleting existing fields from index mappings
- **Data type changes**: Changing the data type of existing fields (for example, `text` → `keyword`)
- **Required field additions**: Adding mandatory fields without default values
- **Record structure changes**: Modifying the structure of exported records in incompatible ways

#### Safe schema evolution

The following changes are considered **backwards compatible** and do **not** require data migrations:

- **Additive changes**: Adding optional fields with default values
- **New indices**: Creating new indices for new features
- **Index settings**: Updating index settings in ways that do not affect existing data

### Integration testing

Schema compatibility is verified through the [`SchemaUpdateIT`](https://github.com/camunda/camunda/blob/main/schema-manager/src/test/java/io/camunda/search/schema/SchemaUpdateIT.java) integration test.
This test runs on both **Elasticsearch** and **OpenSearch**. Any incompatible schema change causes the test to fail, preventing breaking changes from being introduced.
