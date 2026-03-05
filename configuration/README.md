# Unified Configuration System

This Spring module implements the component used by the Camunda Orchestration Cluster to manage and consume the configuration.

# Context

Starting with Camunda 8.8, the webapps that were previously standalone and independent, Operate, Tasklist and Identity, have been merged into a single application, called Orchestration Cluster.

In spite of these apps being part of the single application, after the merge, they would still consume their configurations independently. This would be unintuitive and misleading or even dangerous in some more critical cases. One of the biggest examples of this negative potential was the configuration for the database endpoint, since, in the case of Elasticsearch, it needed to be configured multiple times, once for each component:
* Operate:
```
camunda.operate.elasticsearch.url
```
* Tasklist:
```
camunda.tasklist.elasticsearch.url
```
* Exporter(s):
```
zeebe.broker.exporters.{exporterName}.args.connect.url
```
* Zeebe:
```
camunda.database.url
```
and even more. Such repeated configuration is not easy to explain, within the context of a single application, as it supposedly needs to be configured only once, and with the example above could be even considered relatively dangerous, as a User could think that by configuring `camunda.database.url` would be enough, while Tasklist and Operate would potentially use another database, given that the properties had default values.

While the situation exemplified above was the main concern of the legacy configuration system, other aspects of the configuration needed to be curated for a better User experience, such as:
* properties names referencing functional aspects of the application, rather than specific webapps
* legacy properties names were typically referred using lowerCamelCase style, as opposed to the standard kebab-case style
* poor documentation

In addition to a poor User experience, the legacy system had also problems at development time:
* lack of a central configuration system sometimes resulted in difficulties when Engineers needed the name of a given property

### Extras

When developing the Unified Configuration System, we also doubled down on coherence and consistency across the various properties:
* when a property expresses time, the Unified Configuration System supports its declaration as Duration (e.g., '10m' or '5s'), as opposed to the several instances, in the previous system, where legacy property names such as `zeebe.broker.exporting.distributionInterval` would not intuitively communicate to the User whether the time should be expressed in minutes, seconds, milliseconds or the likes
* when a property expresses size, the Unified Configuration System supports its declaration as DataSize (e.g., '10MB' or '2GB'), as opposed to the several instances, in the previous system, where legacy properties names such as `zeebe.broker.exporters.camundaexporter.args.bulk.memoryLimit` would not intuitively communicate to the User whether the size should be expressed in MB, GB, or the likes.

### Backwards Compatibility