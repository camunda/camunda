# Camunda Spring SDK

## Spring SDK usage in Camunda Saas staging environments

To use a staging environment, the `camunda.client.cloud.base-url` can be updated to match the base url of the desired cloud environment.

Example:

```yaml
camunda:
  client:
    cloud:
      base-url: ultrawombat.com
```

By default, the client will always point to the production Saas instance:

```yaml
camunda:
  client:
    cloud:
      base-url: camunda.io
```

