# Broker Configuration Templates

This section references the default Zeebe standalone broker configuration templates, which are shipped with the distribution. They can be found inside the `config` folder and can be used to adjust Zeebe to your needs.

* [`config/application.yaml` Standalone Broker (with embedded gateway)](https://github.com/zeebe-io/zeebe/tree/{{commit}}/dist/src/main/config/config/application.yaml`) - Default configuration containing only the most common configuration settings. Use this as the basis for a single broker deployment for test or development
* [`config/broker.standalone.yaml.template` Standalone Broker (with embedded gateway)](https://github.com/zeebe-io/zeebe/tree/{{commit}}/dist/src/main/config/broker.standalone.yaml.template`) - Complete configuration template for a standalone broker with embedded gateway. Use this as the basis for a single broker deployment for test or development
* [`config/broker.yaml.template` Broker Node (without embedded gateway)](https://github.com/zeebe-io/zeebe/tree/{{commit}}/dist/src/main/config/broker.yaml.template`) - Complete configuration template for a broker node without embedded gateway. Use this as the basis for deploying multiple broker nodes as part of a cluster

## Default Standalone Broker Configuration
The default configuration contains the most common configuration options.
```yaml
{{#include ../../../dist/src/main/config/application.yaml}}
```

## Standalone Broker (with embedded Gateway)
```yaml
{{#include ../../../dist/src/main/config/broker.standalone.yaml.template}}
```

The template for the broker node (without embedded gateway) is pretty much the same. The only difference is that the embedded gateway is disabled and the corresponding configuration details are absent.
