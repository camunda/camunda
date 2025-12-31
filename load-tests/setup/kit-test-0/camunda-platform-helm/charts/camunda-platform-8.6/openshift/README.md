# OpenShift Support

The Camunda 8 Helm chart can be deployed to OpenShift using extra values file that unset the `securityContext`
according to OpenShift default Security Context Constraints (SCCs).

For full details, please check the official docs:
[Camunda 8 Self-Managed Red Hat OpenShift](https://docs.camunda.io/docs/self-managed/setup/deploy/openshift/redhat-openshift/).
