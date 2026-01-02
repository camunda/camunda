# Docs: Tests

In this repo, we have the following tests:
1. Unit tests under `test/unit` dir.
2. Integration tests under `test/integration` dir.

## Unit Tests

For our unit testing we combine golden files and [Terratest](https://terratest.gruntwork.io/docs/). 

Golden files store the expected output of a certain command or response for a specific request. In our case, the golden files contain the rendered manifest, which are outputted after you run helm template. This allows you to verify that the default values are set and changed only in a controlled manner, this reduces the burden of writing many tests.

If we want to verify specific properties (or conditions), we can use the direct property tests with Terratest.

## Integration Tests

With the integration tests we want to test for two things:

1. Whether the charts can be deployed to Kubernetes, and are accepted by the K8s API.
2. Whether the services are running and can work with each other.

Other things, like broken templates, incorrectly set values, etc., are caught by the tests above. Also, the integration test are categorized into `test/integration/scenarios`. These scenarios are developed using the [Taskfile](https://taskfile.dev/) tool. 
