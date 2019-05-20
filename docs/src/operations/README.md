# Operations

## Development

We recommend using Docker during development. This gives you a consistent, repeatable development environment.

## Production

In Production, we recommend using Kubernetes and container images. This provides you with predictable and consistent configuration, and the ability to manage deployment using automation tools.

## The Monitor

Zeebe provides a monitor to inspect workflow instances.
The monitor is a standalone web application which connects to a Zeebe broker and consumes all records.

The monitor can be downloaded from [github.com/zeebe-io/zeebe-simple-monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases).
