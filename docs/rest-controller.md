# REST API controllers

This document outlines the main steps to consider when building REST controllers in the Camunda 8
REST API.
It covers the whole end-to-end view from endpoint definition and implementation to client
considerations and testing.

## Main steps

- [ ] Define the endpoint you want to create.
- [ ] Define your endpoint and any required data models in the [OpenAPI description](../zeebe/gateway-protocol/src/main/proto/rest-api.yaml) of the C8 REST API.
- [ ] Implement your controller(s) in the `zeebe/gateway-rest` module.
- [ ] Implement or extend the respective `Services` your controller invokes.
- [ ] Extend the Camunda Client with the new command.
- [ ] Optional: create integration tests for your new command.
- [ ] Generate public reference documentation from the OpenAPI description.

### Endpoint definition

Define the endpoint you want to create.

1. Consider the (🔒 currently internal) [REST API guidelines](https://docs.google.com/document/d/1G9AmmNac-4QLGZ0LXQXa3FyeCrSJdIboaPt3-R6dWNw/).
2. Consider the [existing endpoints](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/camunda-8-rest-api/) to create consistent endpoints.
3. Share and validate your endpoint design with peers.

### OpenAPI extension

Define your endpoint and any required data models in the [OpenAPI description](../zeebe/gateway-protocol/src/main/proto/rest-api.yaml) of the C8 REST API.

1. Consider the [OpenAPI specification](https://spec.openapis.org/oas/v3.0.3) and [guide](https://learn.openapis.org/) for detailed guidance.
2. Reuse existing data models as much as possible to avoid duplication and foster streamlined models.
3. Add `description` and `summary` attributes where applicable. The OpenAPI description will be used directly to generate public reference documentation later.

### REST controller implementation

Implement your controller(s) in the `zeebe/gateway-rest` module next to the [other controllers](../zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller).
You can extend an existing controller if there is one for your resource, e.g. the `UserTaskController` for the user task resource.

1. (optional) Generate the data models locally before implementing controllers by running `mvn clean install -Dquickly` on the `zeebe/gateway-rest` module.
2. Consider the existing controllers for best practices around structuring your controller.
3. The controllers are Spring `RestController`s, marked as such by adding the appropriate Camunda annotation (refer to the other controllers). There is a separate Camunda annotation for Query endpoint controllers as used by the `ProcessInstanceQueryController`.
4. Controllers should only take care of
   1. Mapping and potentially validating user input.
   2. Invoking the respective `Services` method to execute the desired action, e.g. `UserTaskServices::completeUserTask´ or `UserTaskServices::search`.
   3. Mapping the result back to either a success or failure response.
5. Controllers use utility classes for mapping input and output, e.g. the `RequestMapper` and `ResponseMapper`. Expected errors can be conveniently mapped to REST responses using the `RestErrorMapper`.
6. Provide REST API-level unit tests, mocking the interaction with the service layer and validating that input and output are mapped as expected.

### Service layer extension

Implement or extend the respective `Services` your controller invokes in the `service` module.

1. Services build the integration layer between REST controllers and Zeebe Brokers or Search Clients.
2. Services extend the `SearchQueryService` if they need to provide ElasticSearch/OpenSearch search access or extend the general `ApiServices` if they don't.
3. Services map actions to broker commands or search requests, depending on the invoked method.
4. If your service wants to provide new search capabilities for a resource, provide the following:
   1. Create an implementation of the `TypedSearchQuery` and related classes for your resource like the `ProcessInstanceQuery` and the related `ProcessInstanceFilter` and `ProcessInstanceSort`.
   2. Create a Java `record` for the expected entity response like the [ProcessInstanceEntity](../service/src/main/java/io/camunda/service/entities/ProcessInstanceEntity.java).
   3. Use the query and entity classes as the generics when extending the `SearchQueryService`.
5. If your service wants to issue Zeebe broker requests, provide the following:
   1. Reuse the [existing requests](../zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/impl/broker/request) if possible or create new ones consistent with them.
   2. Send the request to the Zeebe brokers using the `sendBrokerRequest` method provided in your service implementation inherited from the `ApiServices` class.
   3. If your request is a new capability in the Zeebe brokers, implement and test the functionality there if not done yet.
6. Provide service-level unit tests, mocking the interaction with the Zeebe brokers and search clients and validating that input and output are handled as expected, e.g. input validation and broker exceptions are created accordingly.

### Camunda Client extension

Extend the Camunda Client with the new command you added to the REST API.

1. In the [ZeebeClient](../clients/java/src/main/java/io/camunda/client/ZeebeClient.java), add a new command method for your purpose.
2. If you provide new search capabilities for a resource, implement the `TypedSearchQueryRequest` for your resource. This is similar to the interface you provided for the REST gateway part.
3. If you provide new Zeebe broker commands, consider providing multiple steps guiding the user from required input to optional attributes step by step. The command chain ends in a `FinalCommandStep`.
4. Implement the command chain or query interface accordingly, like the `ProcessInstanceQueryImpl` or `CompleteUserTaskCommandImpl` do.
5. Provide client-level unit tests, mocking the interaction with the REST API and validating that the client input and API output are validated and transformed correctly.

### Integration testing

Create integration tests for your new command. This is optional since not every use case requires this.
If you write new endpoints that either create new Zeebe broker functionality or make existing functionality available via a new protocol (e.g. REST),
consider adding [integration test cases](../zeebe/qa/integration-tests) using the Camunda Client. Refer to existing integration tests for setup.

### Documentation generation

Adjust the [public reference documentation](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/camunda-8-rest-api/) with the OpenAPI description [you extended earlier](#openapi-extension).

1. Consider the [documentation guide](https://github.com/camunda/camunda-docs/blob/main/howtos/interactive-api-explorers.md) explaining how to generate the REST API explorer.
2. If you encounter any adjustments to improve the generated documentation, feed it back into the OpenAPI file and regenerate until the docs are in the desired state.
3. Adjust and commit the OpenAPI file back to the [Camunda repository](../zeebe/gateway-protocol/src/main/proto/rest-api.yaml) to check in the changes to the source of truth.
   Otherwise, subsequent generations of the documentation will override your manual changes by copying the source of truth OpenAPI from this repository to the Camunda documentation repository.
4. Create a documentation PR and follow the [documentation team's guidelines](https://github.com/camunda/camunda-docs/blob/main/CONTRIBUTING.MD).

