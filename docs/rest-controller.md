# REST API controllers

This document outlines the main steps to consider when building REST controllers in the Camunda 8
REST API.

It covers the whole end-to-end view from endpoint definition and implementation to client
considerations and testing, touching on the following steps:

- [ ] Define the endpoint you want to create.
- [ ] Define your endpoint and any required data models in the [OpenAPI description](../zeebe/gateway-protocol/src/main/proto/rest-api.yaml) of the C8 REST API.
- [ ] Implement your controller(s) in the `zeebe/gateway-rest` module.
- [ ] Implement or extend the respective `Services` your controller invokes.
- [ ] Extend the Camunda Client with the new command.
- [ ] Optional: create integration tests for your new command.
- [ ] Generate public reference documentation from the OpenAPI description.

## Endpoint definition

Define the endpoint you want to create.

1. Consider the (🔒 currently internal) [REST API guidelines](https://docs.google.com/document/d/1G9AmmNac-4QLGZ0LXQXa3FyeCrSJdIboaPt3-R6dWNw/).
2. Consider the [existing endpoints](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/camunda-8-rest-api/) to create consistent endpoints.
3. Share and validate your endpoint design with peers, for example in the [#prj-c8-rest-api](https://camunda.slack.com/archives/C06UKS51QV9).

## OpenAPI extension

Define your endpoint and any required data models in the [OpenAPI description](../zeebe/gateway-protocol/src/main/proto/rest-api.yaml) of the C8 REST API.

1. Consider the [OpenAPI specification](https://spec.openapis.org/oas/v3.0.3) and [guide](https://learn.openapis.org/) for detailed guidance.
2. Reuse existing data models as much as possible to avoid duplication and foster streamlined models. Consider the following aspects:
   1. Key attributes relating to record keys in the engine must be of type `string` and not a number. This allows for uniform consumption on all platforms.
   2. Response codes should be as consistent as possible across endpoints. Align with response codes of similar endpoints and reuse existing response components if possible.
3. The OpenAPI description will be used directly to generate public reference documentation later. For optimal documentation, follow these rules:
   1. Add a `summary` property to every API path.
   2. Add a `description` property to every API path and every schema property.
   3. Follow the [Camunda style guide](https://confluence.camunda.com/display/HAN/Camunda+style+guide) in all descriptive text.
   4. Follow these rules for casing:
      1. Use "Sentence case" (with no period) for path `tags` properties.
      2. Use "Sentence case" (with no period) for path `summary` properties.
      3. Use "Sentence case." (with a period) for all other descriptive text.
      4. Use "lower case" to reference API resources in descriptive text.
         - Example: use "The decision definition search query failed." instead of "The Decision Definition Search Query failed."
   5. For multi-line path `description` properties, use a complete sentence with no line breaks for the first line.
      - Reason: The documentation generator uses only the first line as the `meta description` on the endpoint's page. Incomplete sentences on the first line create a confusing `meta description`.
4. The OpenAPI spec is owned by the @camunda/docs-api-reviewers team, so please await a review from them before merging your changes. The team will be assigned for review automatically.

## REST controller implementation

Implement your controller(s) in the `zeebe/gateway-rest` module next to the [other controllers](../zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller).
You can extend an existing controller if there is one for your resource, e.g. the `UserTaskController` for the user task resource.

1. (optional) Generate the data models locally before implementing controllers by running `mvn clean install -Dquickly` on the `zeebe/gateway-rest` module.
2. Consider the existing controllers for best practices around structuring your controller, e.g. using the `RequestMapper` and `ResponseMapper` for input conversion and collecting REST error messages in `ErrorMessages`.
3. The controllers are Spring `RestController`s, marked as such by adding the appropriate Camunda annotation (refer to the other controllers).
4. Controllers should only take care of the following tasks:
   1. Mapping and potentially validating user input, e.g. using the `RequestMapper` and `RequestValidator`.
   2. Invoking the respective `Services` method to execute the desired action, e.g. `UserTaskServices::completeUserTask` or `UserTaskServices::search`. The `RequestMapper` also provides helpers for invoking service methods.
   3. Mapping the result back to either a success or failure response, e.g. using the helper methods of the `ResponseMapper` and `RestErrorMapper`.
5. Provide REST API-level unit tests, mocking the interaction with the service layer, validating that input and output are mapped as expected, and ensuring that service exceptions are handled correctly.

## Service layer extension

Implement or extend the respective `Services` your controller invokes in the `service` module.

1. Services build the integration layer between REST controllers and Zeebe Brokers or Search Clients.
2. Services extend the `SearchQueryService` if they need to provide ElasticSearch/OpenSearch search access or extend the general `ApiServices` if they don't.
3. Services map actions to broker commands or search requests, depending on the invoked method.
4. If your service wants to provide new search capabilities for a resource, provide the following:
   1. Create an implementation of the `TypedSearchQuery` and related classes for your resource like the `ProcessInstanceQuery` and the related `ProcessInstanceFilter` and `ProcessInstanceSort`.
   2. Create a Java `record` for the expected entity response like the [ProcessInstanceEntity](../search/search-domain/src/main/java/io/camunda/search/entities/ProcessInstanceEntity.java).
   3. Use the query and entity classes as the generics when extending the `SearchQueryService`.
5. If your service wants to issue Zeebe broker requests, provide the following:
   1. Reuse the [existing requests](../zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/impl/broker/request) if possible or create new ones consistent with them.
   2. Send the request to the Zeebe brokers using the `sendBrokerRequest` method provided in your service implementation inherited from the `ApiServices` class.
   3. If your request is a new capability in the Zeebe brokers, implement and test the functionality there if not done yet.
6. Provide service-level unit tests, mocking the interaction with the Zeebe brokers and search clients and validating that input and output are handled as expected, e.g. input validation and broker exceptions are created accordingly.

## Camunda Client extension

Extend the Camunda Client with the new command you added to the REST API.

1. In the [CamundaClient](../clients/java/src/main/java/io/camunda/client/CamundaClient.java), add a new command method for your purpose.
2. If you provide new search capabilities for a resource, implement the `TypedSearchQueryRequest` for your resource. This is similar to the interface you provided for the REST gateway part.
3. If you provide new Zeebe broker commands, consider providing multiple steps guiding the user from required input to optional attributes step by step. The command chain ends in a `FinalCommandStep`.
4. Implement the command chain or query interface accordingly, like the `ProcessInstanceQueryImpl` or `CompleteUserTaskCommandImpl` do.
5. Provide client-level unit tests, mocking the interaction with the REST API and validating that the client input and API output are validated and transformed correctly.

## Integration testing

Create integration tests (ITs) for your new command. This is optional given not every use case requires this. The following types of ITs exist:

- If you write new endpoints that either create new Zeebe broker functionality or make existing functionality available via a new protocol (REST, for example),
  consider adding [engine integration test cases](../zeebe/qa/integration-tests) using the Camunda Client.
- [End-to-end integration tests](../qa/integration-tests) are a great way to test your feature works on all supported data layers alike.

Refer to existing integration tests for setup.

## Documentation generation

[The public reference documentation](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/camunda-8-rest-api/) needs to be synchronized with [your specification changes](#openapi-extension).

### Automatic synchronization

The documentation project is configured to execute [a workflow](https://github.com/camunda/camunda-docs/actions/workflows/sync-rest-api-docs.yaml) weekly to synchronize the "next" version of the REST API docs. The workflow pulls the specification from `main` in this project, and re-generates the documentation for the "next" version based on that spec. If your changes only need to be applied to version "next," and a documentation update is not urgent, you should wait for that workflow to incorporate your changes.

### Manual synchronization

#### For changes applied to the `main` branch

1. Trigger a manual run of the [synchronization workflow](https://github.com/camunda/camunda-docs/actions/workflows/sync-rest-api-docs.yaml).

#### For each other version of your changes

1. Consider the [documentation guide](https://github.com/camunda/camunda-docs/blob/main/howtos/interactive-api-explorers.md) explaining how to generate the REST API explorer.
2. If you encounter any adjustments to improve the generated documentation, feed it back into the OpenAPI file and regenerate until the docs are in the desired state.
3. Adjust and commit the OpenAPI file back to the [Camunda repository](../zeebe/gateway-protocol/src/main/proto/rest-api.yaml) to check in the changes to the source of truth.
   Otherwise, subsequent generations of the documentation will override your manual changes by copying the source of truth OpenAPI from this repository to the Camunda documentation repository.
4. Create a documentation PR and follow the [documentation team's guidelines](https://github.com/camunda/camunda-docs/blob/main/CONTRIBUTING.MD).

