This module contains:
* ELS and Opensearch schema configuration
* Entity classes
* Data migration logic

## Changing the schema

There are two ways to change Operate schema: with and without data migration.

### Changing the schema without data migration

This is the preferred way to adjust the schema, as data migration requires a lot of resources.

The only allowed way to change the schema is **adding new fields**. You need to adjust index or
template descriptors in `/resources/schema/elasticsearch/create`
and `/resources/schema/opensearch/create` folders.

Since old data won't be migrated, the code that uses new fields must be ready to get empty (absent)
fields in old documents. This fact must also be addressed when planning the new features and
discussed with Product manager when needed.

There are potentially some field mapping parameters that may be dynamically changed, but
currently Operate does not support that. If you want to use this [Elasticsearch (and Opensearch)
feature](https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-put-mapping.html#updating-field-mappings),
you would need to implement this in Operate SchemaManager first.

#### Implementation details

On Operate startup:

* current indices mappings will be validated against those declared in `/resources/schema/` files.
  This happens in `IndexSchemaValidator#validateIndexMappings` method. Method returns new fields
  that must be created.
* Schema is updated in `SchemaManager#updateSchema` method, new fields are added.

Index versions are **NOT** changed.

Log output will look similar to this:

```text
i.c.o.s.SchemaStartup                    : SchemaStartup: update index mappings.
i.c.o.s.e.ElasticsearchSchemaManager     : Update template: operate-event-8.3.0_template
i.c.o.s.e.ElasticsearchSchemaManager     : Index alias: operate-event-8.3.0_alias. New fields will be added: [IndexMappingProperty{name='positionProcessMessageSubscription', typeDefinition={type=long, index=false}}, IndexMappingProperty{name='positionIncident', typeDefinition={type=long, index=false}}, IndexMappingProperty{name='positionJob', typeDefinition={type=long, index=false}}, IndexMappingProperty{name='position', typeDefinition={type=long}}]
```

## DMN

DMN entities:

![dmn_classes](https://user-images.githubusercontent.com/17064290/156787529-1c9696af-4585-46a2-a43d-fc295a788399.png)

_Source: [docs/dmn_classes.puml](docs/dmn_classes.puml)_
