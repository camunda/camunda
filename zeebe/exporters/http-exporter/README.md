# HTTP Exporter

This exporter allows you to send Zeebe events to an HTTP endpoint. It can be used to integrate with external systems or services that accept HTTP requests.

The exporter uses rules to determine which events to send. The rules can be configured as part of
the exporter configuration file.

## Configuration

Example configuration for the HTTP exporter:

```json
{
  "url": "http://localhost:8080/events",
  "batchSize": 10,
  "batchInterval": 500,
  "rules": [
    {
      "valueType": ["INCIDENT"],
      "intent": ["CREATED"]
    }
  ]
}
```

This configuration specifies that the exporter will send events to `http://localhost:8080/events` in batches of 10, with a batch interval of 500 milliseconds. The rules indicate that all value types are included.

Another example configuration be found [here](src/test/resources/export-subscription-config.json).

Please refer to the [Event Ruler documentation](https://github.com/aws/event-ruler?tab=readme-ov-file#ruler-by-example) for more details on rule configuration and usage.

## HTTP Endpoint

The exporter sends events to the specified HTTP endpoint using a POST request. The body of the request contains the event data in JSON format.

The client used by the exporter supports the HTTP keep-alive feature, which allows for persistent connections to the server, improving performance by reducing the overhead of establishing new connections for each request.

The endpoint is required to return a keep-alive response header to maintain the connection.
