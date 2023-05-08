# Compose Action

## Intro

The action is used ease the use of docker-compose for running depdencies like cambpm or elasticsearch for tests. Based on the a provided compose file it launches its contents and blocks GHA till the containers are considered healthy.

It also allows running multiple instances of elasticsearch by defining a project_name.

Using the `env` directive allows configuring variables within the docker-compose file.

## Usage

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| compose_file | Full path to the compose file | true | |
| project_name | Project name to allow running the same file multiple times | true | |

## Example of using the action

```yaml
steps:
- uses: actions/checkout@v3
- name: Start Cambpm
  uses: ./.github/actions/compose
  with:
    compose_file: ${{ github.workspace }}/.github/actions/compose/docker-compose.cambpm.yml
    project_name: cambpm
  env:
    CAMBPM_VERSION: 7.18.0
    CAMBPM_JVM_MEMORY: 1
- name: Start Elastic - Old
  uses: ./.github/actions/compose
  with:
    compose_file: ${{ github.workspace }}/.github/actions/compose/docker-compose.elasticsearch.yml
    project_name: elasticsearch-old
  env:
    ELASTIC_VERSION: 7.10.0
    ELASTIC_JVM_MEMORY: 1
    ELASTIC_HTTP_PORT: 9250
- name: Start Elastic - New
  uses: ./.github/actions/compose
  with:
    compose_file: ${{ github.workspace }}/.github/actions/compose/docker-compose.elasticsearch.yml
    project_name: elasticsearch-new
  env:
    ELASTIC_VERSION: 7.10.0
    ELASTIC_JVM_MEMORY: 1
    ELASTIC_HTTP_PORT: 9200
```
