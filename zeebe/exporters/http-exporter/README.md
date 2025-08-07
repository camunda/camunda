# HTTP Exporter

This exporter allows you to send Zeebe events to an HTTP endpoint. It can be used to integrate with external systems or services that accept HTTP requests.

The exporter uses rules to determine which events to send. The rules can be configured as part of
the exporter configuration file.

## Configuration

Example configuration for the HTTP exporter:

```json
{
  "batchSize": 1,
  "batchInterval": 500,
  "jsonFilter": "valueType,value",
  "filters": [{
    "valueType": "INCIDENT",
    "intents": ["CREATED"]
  }],
  "rules": [
    {"value": {"bpmnProcessId" : ["testProcess"]}}
  ]
}
```

This configuration specifies that the exporter will send events to `http://localhost:8080/events` in batches of 10, with a batch interval of 500 milliseconds.

The `jsonFilter` is set to `valueType,value`, which means that the exporter will only include the `valueType` and `value` fields in the JSON payload of the HTTP request.
Please refer to the [Squiggly project](https://github.com/bohnman/squiggly?tab=readme-ov-file#top-level-filters) documentation for more details on how to configure JSON filters.

The `filters` specify that only events of type `INCIDENT` with the intent `CREATED` will be sent.

The `rules` section allows you to filter events based on specific criteria. In this example, only events related to the process with BPMN process ID `testProcess` will be sent.
Please refer to the [Event Ruler documentation](https://github.com/aws/event-ruler?tab=readme-ov-file#ruler-by-example) for more details on rule configuration and usage.

Other example configurations be found [here](src/test/resources).


## HTTP Endpoint

The exporter sends events to the specified HTTP endpoint using a POST request. The body of the request contains the event data in JSON format.

The client used by the exporter supports the HTTP keep-alive feature, which allows for persistent connections to the server, improving performance by reducing the overhead of establishing new connections for each request.

The endpoint is required to return a keep-alive response header to maintain the connection.
