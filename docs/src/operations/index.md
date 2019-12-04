# Operations

## Development

We recommend using Docker during development. This gives you a consistent, repeatable development environment.

## Production

In Production, we recommend using Kubernetes and container images. This provides you with predictable and consistent configuration, and the ability to manage deployment using automation tools.   

## Tools For Monitoring And Managing Workflows

Operate is a tool that was built for monitoring and managing Zeebe workflows. We walk through how to install Operate in the ["Getting Started" tutorial](/getting-started/).

The current Operate release is a developer preview and is available for _non-production use only._ [You can find the Operate preview license here.](https://zeebe.io/legal/operate-evaluation-license/)

We plan to release Operate under an enterprise license for production use in the future. 

Alternatively:

* There's a community project called [Simple Monitor](https://github.com/zeebe-io/zeebe-simple-monitor) that can also be used to inspect deployed workflows and workflow instances. Simple Monitor is not intended for production use, but can be useful during development for debugging. 
* It's possible to combine [Kibana](https://www.elastic.co/products/kibana) with Zeebe's [Elasticsearch exporter](https://github.com/zeebe-io/zeebe/tree/e527f8a566cade12a8dd69d38909c55ea9594eca/exporters/elasticsearch-exporter) to create a dashboard for monitoring the state of Zeebe. 


