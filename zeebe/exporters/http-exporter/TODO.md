# HTTP Exporter

TODOs for the HTTP Exporter in Zeebe

## Testing

- [ ] Implement unit tests for the HTTP exporter.
- [ ] Implement integration tests to verify the exporter works e2e with Zeebe and an HTTP endpoint.
- [x] Verify that the exporter is behaving correctly in case of errors, such as HTTP 4xx and 5xx responses.
- [ ] Verify thread safety of the exporter, especially in the context of concurrent event flushing from background thread.

## HTTP Requests

- [ ] Implement support for custom HTTP headers in requests.
- [ ] Implement request signature generation for secure HTTP requests.
- [x] Support a flag that allow requests to fail without blocking/retrying the exporter.
- [x] Implement a retry mechanism for failed HTTP requests, with configurable retry intervals and maximum retries.

## Filtering

- [x] Support filtering based on event types and intents.

## JSON Views

- [x] Implement customizable mapping of Zeebe event types to HTTP JSON request payloads (JSON views).

## Metrics

- [ ] Implement metrics for the HTTP exporter to track success and failure rates of HTTP requests.

