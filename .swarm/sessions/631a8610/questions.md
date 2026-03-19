# Plan Questions

> Session: 631a8610
> Request: # Global Execution Listeners  ## Value Proposition Statement  For developers and operations teams wh...
> Generated: 2026-03-19T11:58:36.643Z

<!-- Fill in your answers below each question. Leave blank to let the agent decide. -->
<!-- When done, run: swarm plan --resume -->

## PM / Requirements (`plan-clarify`)

### Q1
> Cancel execution listeners (#2768) are not implemented — is #2768 a hard blocker for this feature, or can global ELs ship with only start/end support and add cancel later?

**Answer:**


### Q2
> The spec mentions the @camunda/camunda-composite-components upgrade to v0.21.5 for subElements navbar support. Is that upgrade already merged/available, or does it need to be coordinated?

**Answer:**


### Q3
> Can this feature be phased incrementally — e.g., Phase 1: process-level listeners only, Phase 2: element-level scoping, Phase 3: Admin UI + Operate enhancements — or must all pieces ship together in 8.10?

**Answer:**


### Q4
> The spec lists multiInstanceBody as supporting start and end, but the engine's ExecutionListenersValidator excludes MULTI_INSTANCE_BODY. Which is the source of truth — should global ELs fire on multi-instance bodies, or should the spec be corrected?

**Answer:**


### Q5
> Similarly, the spec lists eventSubprocess as supporting start and end, but the engine's validator excludes EVENT_SUB_PROCESS. Should the validator be changed, or should the spec be corrected?

**Answer:**


### Q6
> The engine currently allows execution listeners on AD_HOC_SUB_PROCESS and MANUAL_TASK, but the spec's element type matrix omits both. Should they be included in the global EL scope?

**Answer:**


### Q7
> The spec says endEvent does not support end and notes "Error end events do not support end." Does this mean all end events don't support end, or only error-typed end events?

**Answer:**


### Q8
> The spec says intermediateThrowEvent supports both start and end but notes "Interrupting escalation throw does not support end." How should the engine validate this — at configuration time or at runtime?

**Answer:**


### Q9
> The spec says boundaryEvent does not support start, and startEvent does not support end. Today's engine does NOT enforce these restrictions for BPMN-level execution listeners. Should global ELs introduce stricter validation than what BPMN-level ELs allow?

**Answer:**


### Q10
> The spec proposes endpoints like POST /v2/global-execution-listeners. The existing global task listener API uses POST /v2/global-task-listeners. Should these be separate endpoint paths or a unified /v2/global-listeners endpoint with a listenerType discriminator?

**Answer:**


### Q11
> The spec says categories and elementTypes are both optional, and omitting both is equivalent to categories: [all]. What happens if a user explicitly provides categories: [all] AND elementTypes: [serviceTask]? Is the elementTypes redundant or should the API reject the combination?

**Answer:**


### Q12
> When categories and elementTypes produce a union, should the API response include the resolved element types, or only the original categories/elementTypes as submitted?

**Answer:**


### Q13
> The spec shows eventTypes as an array per listener. The validation matrix means not all eventTypes are valid for all resolved element types. What is the validation behavior when eventTypes: [start, end] is combined with categories: [gateways]? Should the API reject, silently ignore, or accept and fire only where supported?

**Answer:**


### Q14
> Can a single global execution listener have overlapping coverage with another? For example, Listener A targets categories: [all] with eventTypes: [start], and Listener B targets elementTypes: [serviceTask] with eventTypes: [start]. Should serviceTask start events fire both listeners?

**Answer:**


### Q15
> The spec says "Config wins on ID collision" and "Config applies full replacement semantics for its own listeners while preserving API listeners." What is the proposed config key path for execution listeners — camunda.listener or camunda.cluster.global-listeners.execution?

**Answer:**


### Q16
> The spec's YAML examples don't include an id field on config-file listeners. How are config-file execution listeners identified for update/delete tracking across restarts?

**Answer:**


### Q17
> What happens during a rolling upgrade where some brokers have the global EL feature and some don't?

**Answer:**


### Q18
> The spec says "global listeners run before BPMN-level listeners by default." Where exactly in the listener list should global listeners be inserted?

**Answer:**


### Q19
> If a global start listener job fails and creates an incident, does the element remain in ACTIVATING state indefinitely, or should global EL incidents have a different severity model?

**Answer:**


### Q20
> The spec says "Variable access: Start listeners can read process variables and set local variables. End listeners can read local + output mapping variables and set parent-scope variables." Could a global end listener setting parent-scope variables break process logic by introducing unexpected variables?

**Answer:**


### Q21
> When a process instance is canceled, does a global cancel listener fire on the process element only, or on every element being terminated in the cascade?

**Answer:**


### Q22
> If multiple global listeners match the same element+event, and one fails while the other succeeds — does the element proceed or block?

**Answer:**


### Q23
> The priority field determines ordering among global listeners. What is the behavior when two global listeners have the same priority — is the order deterministic or undefined?

**Answer:**


### Q24
> The spec mentions filtering by "Source: Global" in the Operate listeners tab. The current job search API has no field for global vs model-level. Does Operate need a new filter field in the jobs search API, or should filtering happen client-side?

**Answer:**


### Q25
> The spec says "Distinguish global listener incidents from BPMN-level listener incidents in Operate." Currently, ErrorType.EXECUTION_LISTENER_NO_RETRIES is used for all execution listener incidents regardless of source. Should a new ErrorType be added?

**Answer:**


### Q26
> The spec proposes restructuring Identity navigation from a flat "Global user task listeners" item to a "Listeners" dropdown with "Task" and "Execution" sub-elements. Is this URL change acceptable for the 8.10 release?

**Answer:**


### Q27
> The spec says config-sourced listeners should appear in Admin UI marked as "open decision." What is the decided behavior — read-only rows, hidden, or shown with an edit that creates an API override?

**Answer:**


### Q28
> The Admin UI spec mentions "preset templates for common use cases." What specific templates should be offered?

**Answer:**


### Q29
> The spec says "Resource-based permissions for API access" but doesn't specify the resource type or permission model. What is the authorization resource — a new GLOBAL_EXECUTION_LISTENER resource type or reuse of GLOBAL_LISTENER?

**Answer:**


### Q30
> The spec says "Listener configuration is not per-tenant. Jobs carry tenantId for downstream routing." Is there any concern about a listener registered by one tenant's admin affecting another tenant's processes?

**Answer:**


### Q31
> The spec includes a volume impact table but doesn't define any hard limits. Is there a maximum number of global execution listeners allowed per cluster?

**Answer:**


### Q32
> Should the API or Admin UI include warnings when a user configures broad scoping?

**Answer:**


### Q33
> Has there been any benchmarking of global task listeners at scale that would inform performance expectations?

**Answer:**


### Q34
> The spec mentions a "migration guide (C7 process engine plugins → global ELs)." What level of detail is expected?

**Answer:**


### Q35
> The spec says global ELs "restore the global listener capability lost in the C7→C8 migration." However, C7 BpmnParseListener was non-blocking, while global ELs are blocking job-based. Should the migration guide call out this behavioral difference?

**Answer:**


## Engineering (`plan-eng-clarify`)

### Q1
> ZeebeExecutionListenerEventType currently has only start and end — there is no cancel value. Is cancel EL (#2768) being implemented as part of this epic, as a prerequisite by another team, or should we ship global ELs initially without cancel?

**Answer:**


### Q2
> If we ship without cancel initially, should the API and Admin UI still expose cancel as an event type (rejected at validation time) or omit it entirely from the schema/UI?

**Answer:**


### Q3
> The current GlobalListenerRecord has no categories or elementTypes properties. Adding them is a protocol-level change affecting GlobalListenerRecordValue, golden files, exporters, search indices, RDBMS schema. Is this the intended approach, or should category/elementType expansion happen at a different layer?

**Answer:**


### Q4
> If categories and elementTypes are stored on the record, should they be stored as-is or expanded/normalized to individual element types before persisting?

**Answer:**


### Q5
> Should the engine interpret an empty/absent elementTypes + categories as categories: [all] implicitly, or should it be required to be explicit?

**Answer:**


### Q6
> At what point should execution listener config be pinned — at process instance creation, at element activation, or not at all?

**Answer:**


### Q7
> If pinning is needed, should execution listener pinning share the same config version as task listeners or use a separate pinning mechanism?

**Answer:**


### Q8
> Should execution listeners get completely separate OpenAPI spec, controller, and mapper (parallel to task listeners), or extend the existing global-listeners.yaml with a listenerType discriminator?

**Answer:**


### Q9
> What value should be added to the GlobalListenerType enum — EXECUTION_LISTENER or something else?

**Answer:**


### Q10
> Should elementTypes strings match BpmnElementType enum values exactly (camelCase: SERVICE_TASK, USER_TASK) or use BPMN XML names (serviceTask, userTask)?

**Answer:**


### Q11
> Current permissions are CREATE_TASK_LISTENER, READ_TASK_LISTENER, UPDATE_TASK_LISTENER, DELETE_TASK_LISTENER on GLOBAL_LISTENER resource type. Should execution listeners reuse these same permissions or get new permissions like CREATE_EXECUTION_LISTENER?

**Answer:**


### Q12
> Should global execution listeners be merged with BPMN-level listeners at the point where BpmnStreamProcessor currently retrieves BPMN-level execution listeners?

**Answer:**


### Q13
> If global listeners are prepended/appended to the listener list, should the index be stored as-is (position in merged list) or should global and BPMN-level listeners have separate index tracking?

**Answer:**


### Q14
> Where should the element type × event type validation matrix be the single source of truth — should we create a shared utility class (e.g., GlobalExecutionListenerValidation)?

**Answer:**


### Q15
> Adding a "Source" column to Operate requires adding a new field (e.g., listenerSource or isGlobalListener) to JobRecordValue, JobRecord, JobEntity, search index, all exporters, and Operate frontend. Is this cross-cutting protocol change in scope?

**Answer:**


### Q16
> Should the Operate Source column also apply to global task listener jobs (which also don't have this field today), or only to execution listeners?

**Answer:**


### Q17
> Should execution listeners be added to the config as a sibling field (e.g., camunda.cluster.global-listeners.execution) or should we restructure to camunda.listener?

**Answer:**


### Q18
> Should the GlobalListenersInitializer emit a single CONFIGURE command containing both task and execution listeners, or separate commands per listener type?

**Answer:**


### Q19
> Should category/elementType expansion happen at config-load time so the engine only sees resolved element types?

**Answer:**


### Q20
> Is the URL change from /global-task-listeners → /listeners/tasks a breaking change for bookmarks/deep links?

**Answer:**


### Q21
> Should the Admin UI present element types grouped by the 4 categories or as a flat list with a search filter?

**Answer:**


### Q22
> Should invalid combinations be prevented at selection time (dynamic form validation) or allowed in the form but rejected on submit?

**Answer:**


### Q23
> During a rolling upgrade where some brokers have EXECUTION_LISTENER type and others don't, how should we handle compatibility?

**Answer:**


### Q24
> Is the GLOBAL_LISTENER_VERSIONED_CONFIG column family PARTITION_LOCAL correct for execution listeners too, or should execution listener config be GLOBAL?

**Answer:**


### Q25
> Should global EL jobs include additional metadata (e.g., globalListenerId, globalListenerPriority) in the job headers to help job workers distinguish global from model-level listeners?

**Answer:**


### Q26
> Does the variable scoping for global execution listeners apply identically to BPMN-level listeners, or are there differences?

**Answer:**


### Q27
> Should global execution listeners have a headers field in a future iteration, or remain out of scope?

**Answer:**


### Q28
> Should global execution listeners fire for sequence flows (SEQUENCE_FLOW BpmnElementType)?

**Answer:**


### Q29
> Should global execution listener tests follow the exact same test structure as global task listener tests or can we share test infrastructure?

**Answer:**


### Q30
> Should we include performance/load tests as part of this epic?

**Answer:**


### Q31
> Do execution listeners use the same GlobalListenerEntity/index as task listeners (differentiated by listenerType) or get a separate index?

**Answer:**


### Q32
> If we add categories and elementTypes fields, do we need new collection tables or should these be stored as comma-separated strings?

**Answer:**


## Design (`plan-design-clarify`)

### Q1
> Is the "Listeners" parent nav item itself clickable (navigates somewhere), or is it only a dropdown trigger with no destination?

**Answer:**


### Q2
> When the user is on /listeners/execution, should both the parent "Listeners" item and the "Execution" sub-element show an active/selected visual state simultaneously, or only the child?

**Answer:**


### Q3
> Should the redirect from /global-task-listeners → /listeners/tasks be a permanent client-side redirect or should the old route simply render the same component at the new path?

**Answer:**


### Q4
> Should "Listeners" with its sub-elements always appear in the nav, or should it be conditional on a feature flag?

**Answer:**


### Q5
> Should the nav dropdown arrow/chevron indicator on "Listeners" follow an existing dropdown pattern in Camunda composite components, or is this the first instance of subElements in production?

**Answer:**


### Q6
> Should we build a custom hierarchical multi-select component, use Carbon's TreeView inside a dropdown popover, use a flat FilterableMultiSelect with visual group headers, or use something else?

**Answer:**


### Q7
> When a category checkbox is partially selected, should it display an indeterminate (dash) checkbox state?

**Answer:**


### Q8
> Should category groups be collapsible/expandable within the dropdown, or always fully expanded?

**Answer:**


### Q9
> How should selected items render in the collapsed/trigger state — comma-separated list, category-level summary, count only, or something else?

**Answer:**


### Q10
> When "All" category is selected, should individual element types still be visible and interactable or should the list collapse?

**Answer:**


### Q11
> Should the element type list within the dropdown be searchable/filterable (type-ahead)?

**Answer:**


### Q12
> What is the visual grouping order of categories, and where do ungrouped element types appear?

**Answer:**


### Q13
> Should process be visually separated or highlighted since it's the most common starting point?

**Answer:**


### Q14
> When element scope changes and a previously selected event type becomes invalid, should the system auto-deselect, show an error, show a warning, or prevent the change?

**Answer:**


### Q15
> Should unsupported event types be hidden entirely, shown but disabled, or shown with a tooltip?

**Answer:**


### Q16
> If element types have mixed support for an event, should validation reject the combination, allow it with a warning, or something else?

**Answer:**


### Q17
> If cancel is not yet available at launch, should it be hidden, disabled with a tooltip, or omitted from the schema?

**Answer:**


### Q18
> Should event type selection come before or after element scope in the form?

**Answer:**


### Q19
> Should the form remain a modal or become a dedicated full-page form given the increased complexity?

**Answer:**


### Q20
> What is the maximum modal height before scrolling, and should the form use collapsible sections?

**Answer:**


### Q21
> Where should a "Performance warning" inline notification appear — below the element scope selector, at the top, or as a banner?

**Answer:**


### Q22
> Should the performance warning be an InlineNotification, Callout, helper text, or confirmation dialog?

**Answer:**


### Q23
> At what threshold does the performance warning trigger — only when all category is selected, when resolved element types exceed N, or when start+end are both selected?

**Answer:**


### Q24
> Should the source field appear as a dedicated column, a Carbon Tag badge inline, both, or only in the detail view?

**Answer:**


### Q25
> Should configuration-sourced listeners be fully read-only, editable with a warning, hidden, or editable with API override creation?

**Answer:**


### Q26
> If config-sourced listeners are read-only, should the overflow menu be hidden entirely or shown but disabled?

**Answer:**


### Q27
> What visual treatment distinguishes CONFIGURATION vs API source types?

**Answer:**


### Q28
> Should all 8 columns display in the execution listener EntityList, or should some be hidden by default with column toggle?

**Answer:**


### Q29
> How should "Element scope" render in a narrow table cell — category names, count, truncated list, or expandable chips?

**Answer:**


### Q30
> How should the "Event types" cell render when multiple events are selected?

**Answer:**


### Q31
> Should the list support filtering beyond search-by-ID (e.g., by element scope, event type, source)?

**Answer:**


### Q32
> Should the list support bulk delete?

**Answer:**


### Q33
> Should execution listener search also cover jobType?

**Answer:**


### Q34
> Should the execution listener ID be immutable after creation, like task listeners?

**Answer:**


### Q35
> Can element scope be changed on an existing listener, or should it be locked after creation?

**Answer:**


### Q36
> When editing a configuration-sourced listener, should source be displayed as read-only context information?

**Answer:**


### Q37
> Should the empty state for Execution listeners include an explanation, quick-start guidance, migration docs link, or just a standard message?

**Answer:**


### Q38
> Should the empty state acknowledge that Task listeners exist if Execution listeners are empty?

**Answer:**


### Q39
> What documentation URL should the empty state link to?

**Answer:**


### Q40
> Should "Scope" replace an existing column, or should column widths be redistributed?

**Answer:**


### Q41
> Should the Operate ListenersTab dropdown filter add scope-based filtering options?

**Answer:**


### Q42
> What visual treatment should the Scope column use in Operate — plain text, colored Tag/Badge, or icon+text?

**Answer:**


### Q43
> Should clicking a "Global" listener row in Operate deep-link to the corresponding listener in Identity Admin UI?

**Answer:**


### Q44
> Should incidents from global listeners show a different error type, or the same error type with additional metadata?

**Answer:**


### Q45
> Does the Operate backend (JobEntity / JobRecordValue) currently have a field to distinguish global vs model-level listeners?

**Answer:**


### Q46
> What ARIA role should the hierarchical element scope selector have — tree or listbox?

**Answer:**


### Q47
> When event type checkboxes dynamically become disabled, should a screen reader announcement notify the user?

**Answer:**


### Q48
> Should the performance warning InlineNotification be announced immediately or only when focused?

**Answer:**


### Q49
> What is the expected keyboard navigation order through the form?

**Answer:**


### Q50
> Does Identity Admin UI support responsive/mobile layouts, and how should the hierarchical selector render on narrow viewports?

**Answer:**


### Q51
> Should modal form fields stack vertically on all viewports or use two-column layout on wider viewports?

**Answer:**


### Q52
> Should BPMN element type names be localized or displayed as canonical identifiers?

**Answer:**


### Q53
> Should category names be localized, and what are the translation keys?

**Answer:**


### Q54
> What is the maximum expected label width for long element type names?

**Answer:**


### Q55
> Should element scope + event type cross-validation happen client-side only, server-side only, or both?

**Answer:**


### Q56
> What error message format should be used for unsupported event type combinations?

**Answer:**


### Q57
> Should the UI enforce or warn at a limit on the maximum number of listeners?

**Answer:**


### Q58
> Should the Task and Execution tabs share any UI state or be fully independent?

**Answer:**


### Q59
> If a user creates a listener on the wrong tab, should there be a way to convert/move it?

**Answer:**


### Q60
> Should the list page titles be "Global task listeners" / "Global execution listeners" or just "Task listeners" / "Execution listeners"?

**Answer:**

