# Camunda Spring SDK

## Spring SDK usage in Camunda Saas staging environments

To use a staging environment, the `camunda.client.cloud.domain` can be updated to match the domain of the desired cloud environment.

Example:

```yaml
camunda:
  client:
    cloud:
      domain: ultrawombat.com
```

By default, the client will always point to the production Saas instance:

```yaml
camunda:
  client:
    cloud:
      domain: camunda.io
```

