# pm-tasks Summary

**Timestamp:** 2026-03-20T13:56:54.089Z

## Decomposed Tasks

1. Update protocol: extend GlobalListenerType enum with EXECUTION; add elementTypes/categories to GlobalListenerRecordValue
2. Implement core engine logic: extend DbGlobalListenersState to manage execution listeners; add execution listener matching in BpmnStreamProcessor (depends on: 1)
3. Implement configuration file support: add camunda.listener.execution schema; wire listener factory to Spring config loader (depends on: 1, 2)
4. Implement version pinning: add element instance config pinning logic to lock listener config at activation; add GC on completion (depends on: 2)
5. Update Elasticsearch exporter: add elementTypes/categories to index template; update GlobalListener entity mapping (depends on: 1)
6. Update OpenSearch exporter: add elementTypes/categories to index template; update GlobalListener entity mapping (depends on: 1)
7. Update RDBMS exporter: add ELEMENT_TYPES/CATEGORIES columns; update GlobalListener table schema (depends on: 1)
8. Implement REST API endpoints: POST/GET/PUT/DELETE/search for global execution listeners; add request validation (event-element compatibility) (depends on: 1, 2, 3)
9. Implement authorization: add MANAGE_GLOBAL_EXECUTION_LISTENERS permission checks to API endpoints (depends on: 8)
10. [FRONTEND] Restructure Identity navigation: add expandable Listeners parent item with Task/Execution sub-elements; redirect /global-task-listeners to /listeners/tasks
11. [FRONTEND] Create Execution Listeners list page: table with ID/Type/Events/ElementScope/Priority/Source columns; Edit/Delete actions (API-sourced only); empty state with docs link (depends on: 8, 10)
12. [FRONTEND] Create Add/Edit modal: ID/Type/EventTypes/ElementTypes/Categories fields; CollapsibleAccordion for executionOrder/priority; bidirectional all/individual selection sync; performance warning on categories:[all]; server-side event-element validation (depends on: 8, 10)
13. [FRONTEND] Add Source column to Operate Listeners tab: display 'Global' or 'Model' tag; support filtering by source
14. [FRONTEND] Add Source column to Operate Incidents tab: display 'Global' or 'Model' tag for listener incidents; support filtering by source
15. Write unit tests: protocol serialization, DbGlobalListenersState, listener matching logic, config loading, authorization checks (depends on: 1, 2, 3, 4, 9)
16. Write integration tests: end-to-end listener job creation, element activation flow, version pinning behavior, API CRUD operations, config file loading (depends on: 1, 2, 3, 4, 8, 9)
17. Write API documentation: endpoint specs, request/response examples, validation rules, event-element compatibility matrix, ordering semantics (depends on: 8)
18. Write C7 migration guide: execution listeners vs process engine plugins, configuration examples, common use cases (audit, replication, observability) (depends on: 3, 8, 17)
