# Camunda exporter

## Schema management
Documents will be exported so a search engine such as Elasticsearch (ELS) or
Opensearch, exporting documents will involve indices to enable specific targeting
of documents in search queries and other operations.

### Descriptors
Indices and templates must be deployable in multiple different search engines as a result
storing the full index or template in a file would result in duplicate schemas instead only the shared
mappings are stored in files and all other fields must be hard coded.
- `IndexDescriptors.getMappingsClasspathFilename()` - This must refer to a file accessible in the
thread context class loader containing a mappings block which
describes the fields of an indices or template, for example:
```json
{
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "hello": {
        "type": "text"
      },
      "world": {
        "type": "keyword"
      }
    }
  }
}
```

### Index lifecycle management
Managing indices requires execution actions, e.g. rolling over indices, after certain conditions
have been met different search engine solutions have different approaches to this problem. Currently
it is not possible to create and assign custom life cycle policies, only configuration of a singular
life cycle retention policy which applies to all indices can be changed.

Refer to `RetentionConfiguration` for the most up-to-date configuration:
- `RetentionConfiguration.enabled` - Whether life cycle management is enabled
- `RetentionConfiguration.minimumAge` - The minimum age an index should reach before deletion.
- `RetentionConfiguration.policyName` - The name of policy which will set the minimum age of indices.



