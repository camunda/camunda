# Compose Action

## Intro

The action is used ease the use of docker-compose for running dependencies like elasticsearch for tests. Based on the provided compose file it launches its contents and blocks GHA until the containers are considered healthy.

It also allows running multiple instances of elasticsearch by defining a project_name.

Using the `env` directive allows configuring variables within the docker-compose file.

## Usage

### Inputs

|    Input     |                        Description                         | Required | Default |
|--------------|------------------------------------------------------------|----------|---------|
| compose_file | Full path to the compose file                              | true     |         |
| project_name | Project name to allow running the same file multiple times | true     |         |

## Example of using the action

```yaml
steps:
- uses: actions/checkout@v3
- name: Start Elastic
  uses: ./.github/actions/compose
  with:
    compose_file: .github/actions/compose/docker-compose.elasticsearch.yml
    project_name: elasticsearch
  env:
    ELASTIC_VERSION: 8.13.0
    ELASTIC_JVM_MEMORY: 1
    ELASTIC_HTTP_PORT: 9200
```

