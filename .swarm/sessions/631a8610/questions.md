# Plan Questions

> Session: 631a8610
> Request: # Global Execution Listeners  ## Value Proposition Statement  For developers and operations teams wh...
> Generated: 2026-03-19T11:58:36.643Z

<!-- Fill in your answers below each question. Leave blank to let the agent decide. -->
<!-- When done, run: swarm plan --resume -->

## PM / Requirements (`plan-clarify`)

### Q1 ✔️
> Cancel execution listeners (#2768) are not implemented — is #2768 a hard blocker for this feature, or can global ELs ship with only start/end support and add cancel later?

**Answer:**
Not a hard blocker. Ship global ELs with `start` and `end` support only. Add `cancel` when #2768 lands. The epic already lists #2768 as a dependency, not a prerequisite. The core value (audit, compliance, C7 migration) is delivered with start/end.

### Q2 ✔️
> The spec mentions the @camunda/camunda-composite-components upgrade to v0.21.5 for subElements navbar support. Is that upgrade already merged/available, or does it need to be coordinated?

**Answer:**
This is an implementation detail - handle this as you want. 

### Q3 ✔️
> Can this feature be phased incrementally — e.g., Phase 1: process-level listeners only, Phase 2: element-level scoping, Phase 3: Admin UI + Operate enhancements — or must all pieces ship together in 8.10?

**Answer:**
All pieces should ship together in 8.10. The core value proposition requires the full loop: configure (API + config + Admin UI) → execute (engine) → observe (Operate). Without Operate visibility, operators cannot troubleshoot. Without Admin UI, SaaS customers cannot manage listeners. Phasing would deliver a half-baked experience. The one acceptable cut is omitting `cancel` event type (see Q1).

### Q4 🆘
> The spec lists multiInstanceBody as supporting start and end, but the engine's ExecutionListenersValidator excludes MULTI_INSTANCE_BODY. Which is the source of truth — should global ELs fire on multi-instance bodies, or should the spec be corrected?

**Answer:**
the spec be corrected.

### Q5 🆘
> Similarly, the spec lists eventSubprocess as supporting start and end, but the engine's validator excludes EVENT_SUB_PROCESS. Should the validator be changed, or should the spec be corrected?

**Answer:**
Event subprocesses DO fire start/end lifecycle events in the engine at runtime.

### Q6
> The engine currently allows execution listeners on AD_HOC_SUB_PROCESS and MANUAL_TASK, but the spec's element type matrix omits both. Should they be included in the global EL scope?

**Answer:**
Yes, include both. Update the spec to add `adHocSubProcess` (start, end) and `manualTask` (start, end) to the element type matrix. These are supported by the existing BPMN-level validator, so it's an inconsistency to omit them from global ELs. Add `manualTask` to the `tasks` category.

### Q7
> The spec says endEvent does not support end and notes "Error end events do not support end." Does this mean all end events don't support end, or only error-typed end events?

**Answer:**
The spec should be corrected. Regular end events (none, message, signal, terminate, escalation, compensation) DO support both `start` and `end`. Only error end events do not support `end` because they throw rather than complete normally. Update the matrix: `endEvent` → start=yes, end=yes, with a note "Except error end events which do not support end."

### Q8 🆘
> The spec says intermediateThrowEvent supports both start and end but notes "Interrupting escalation throw does not support end." How should the engine validate this — at configuration time or at runtime?

**Answer:**
Runtime. The global EL configuration targets element TYPES, not specific instances or subtypes. At configuration time, `intermediateThrowEvent` is a valid target for both start and end. At runtime, the engine should silently skip the end listener for interrupting escalation throws. Don't reject the entire configuration over a subtype edge case.

### Q9 🆘
> The spec says boundaryEvent does not support start, and startEvent does not support end. Today's engine does NOT enforce these restrictions for BPMN-level execution listeners. Should global ELs introduce stricter validation than what BPMN-level ELs allow?

**Answer:**
No, correect the spec

### Q10 ✔️
> The spec proposes endpoints like POST /v2/global-execution-listeners. The existing global task listener API uses POST /v2/global-task-listeners. Should these be separate endpoint paths or a unified /v2/global-listeners endpoint with a listenerType discriminator?

**Answer:**
Separate endpoint paths. Follow the existing pattern: `POST /v2/global-execution-listeners` parallel to `POST /v2/global-task-listeners`. Separate paths are simpler to implement, clearer in API docs, and avoid breaking existing task listener API consumers.

### Q11
> The spec says categories and elementTypes are both optional, and omitting both is equivalent to categories: [all]. What happens if a user explicitly provides categories: [all] AND elementTypes: [serviceTask]? Is the elementTypes redundant or should the API reject the combination?

**Answer:**
Accept it. The API resolves the union, which equals `all`. The `elementTypes` is redundant but not an error. Don't reject valid-but-redundant configurations - it creates unnecessary friction. The resolved scope is the union of both fields.

### Q12
> When categories and elementTypes produce a union, should the API response include the resolved element types, or only the original categories/elementTypes as submitted?

**Answer:**
Return the original `categories` and `elementTypes` as submitted. Additionally, include a read-only computed field `resolvedElementTypes` in GET/Search responses so consumers can see exactly which element types will match. This provides transparency without losing the user's original intent.

### Q13 ⚠️
> The spec shows eventTypes as an array per listener. The validation matrix means not all eventTypes are valid for all resolved element types. What is the validation behavior when eventTypes: [start, end] is combined with categories: [gateways]? Should the API reject, silently ignore, or accept and fire only where supported?

**Answer:**
It was defined in the implementation notes section:
> Not all element types support all event types. The API and Admin UI must validate configurations and reject unsupported combinations (eventTypes entries vs resolved element types from categories/elementTypes).

Reject with a validation error. The API must validate every combination of `eventTypes` × resolved element types. Since gateways only support `start`, `end` is invalid for all gateway types. Error message: "Event type 'end' is not supported for element types in category 'gateways' (exclusiveGateway, parallelGateway, inclusiveGateway, eventBasedGateway). Supported events: [start]." This prevents runtime confusion about why some listeners don't fire.

### Q14 ✔️
> Can a single global execution listener have overlapping coverage with another? For example, Listener A targets categories: [all] with eventTypes: [start], and Listener B targets elementTypes: [serviceTask] with eventTypes: [start]. Should serviceTask start events fire both listeners?

**Answer:**
Yes, both listeners fire. Each global listener is independent. If Listener A (categories: [all], start) and Listener B (elementTypes: [serviceTask], start) both match a serviceTask start event, both create separate jobs. This is consistent with BPMN-level behavior where multiple listeners on the same element all fire. Document the multiplicative volume impact.

### Q15 
> The spec says "Config wins on ID collision" and "Config applies full replacement semantics for its own listeners while preserving API listeners." What is the proposed config key path for execution listeners — camunda.listener or camunda.cluster.global-listeners.execution?

**Answer:**
🆘

### Q16 🆘
> The spec's YAML examples don't include an id field on config-file listeners. How are config-file execution listeners identified for update/delete tracking across restarts?

**Answer:**
Follow the same pattern as global task listeners for config-file identification.

### Q17 🆘
> What happens during a rolling upgrade where some brokers have the global EL feature and some don't?

**Answer:**
Follow the same approach as global task listeners.

### Q18
> The spec says "global listeners run before BPMN-level listeners by default." Where exactly in the listener list should global listeners be inserted?

**Answer:**
Prepended before BPMN-level listeners by default. Execution order: global listeners (sorted by priority, highest first) → BPMN-level listeners. When `afterNonGlobal: true`, that specific global listener is appended after all BPMN-level listeners (but still ordered by priority relative to other afterNonGlobal listeners).

### Q19
> If a global start listener job fails and creates an incident, does the element remain in ACTIVATING state indefinitely, or should global EL incidents have a different severity model?

**Answer:**
Yes. Same behavior as BPMN-level execution listener incidents. The element remains in ACTIVATING state until the incident is resolved (retry or manual resolution). No different severity model. This is expected behavior - global listeners are blocking by design, and incidents are the standard mechanism for handling failures.

### Q20
> The spec says "Variable access: Start listeners can read process variables and set local variables. End listeners can read local + output mapping variables and set parent-scope variables." Could a global end listener setting parent-scope variables break process logic by introducing unexpected variables?

**Answer:**
Yes, this is theoretically possible - same risk as BPMN-level end listeners. Consistency is paramount: global ELs use identical variable scoping rules as BPMN-level ELs. Document this behavior prominently in the documentation and migration guide.

### Q21
> When a process instance is canceled, does a global cancel listener fire on the process element only, or on every element being terminated in the cascade?

**Answer:**
Process element only. The spec explicitly states that `cancel` is only supported on the `process` element type. The cancel listener fires once on the process instance cancellation, not on each terminated element in the cascade.

### Q22
> If multiple global listeners match the same element+event, and one fails while the other succeeds — does the element proceed or block?

**Answer:**
Block. ALL matching listeners must complete successfully for the element to proceed. A failing listener creates an incident that blocks the element. This is consistent with BPMN-level execution listener behavior. The failing listener must be resolved (retry or manual resolution) before execution continues.

### Q23
> The priority field determines ordering among global listeners. What is the behavior when two global listeners have the same priority — is the order deterministic or undefined?

**Answer:**
Deterministic ordering by listener ID (lexicographic). Document this. Follow whatever pattern global task listeners use for tie-breaking. The order should be predictable and reproducible.

### Q24
> The spec mentions filtering by "Source: Global" in the Operate listeners tab. The current job search API has no field for global vs model-level. Does Operate need a new filter field in the jobs search API, or should filtering happen client-side?

**Answer:**
Yes. A new field is needed on the job record to distinguish global from model-level listeners. This is a cross-cutting protocol change (see Engineering Q15) but it IS in scope per the epic's explicit requirement for Operate differentiation. Without this field, the Source column cannot work.

### Q25
> The spec says "Distinguish global listener incidents from BPMN-level listener incidents in Operate." Currently, ErrorType.EXECUTION_LISTENER_NO_RETRIES is used for all execution listener incidents regardless of source. Should a new ErrorType be added?

**Answer:**
No. Use the existing `EXECUTION_LISTENER_NO_RETRIES` error type. The Source column metadata provides differentiation between global and model-level. Adding a new error type would fragment incident handling and searching without meaningful benefit.

### Q26
> The spec proposes restructuring Identity navigation from a flat "Global user task listeners" item to a "Listeners" dropdown with "Task" and "Execution" sub-elements. Is this URL change acceptable for the 8.10 release?

**Answer:**
Yes. Admin UI URLs are internal navigation, not public API contracts. Add a client-side redirect from `/global-task-listeners` → `/listeners/tasks` for bookmark compatibility. The restructuring is justified by the introduction of a second listener type.

### Q27
> The spec says config-sourced listeners should appear in Admin UI marked as "open decision." What is the decided behavior — read-only rows, hidden, or shown with an edit that creates an API override?

**Answer:**
Show as read-only rows with a "CONFIGURATION" source badge. Users can view configuration details but cannot edit or delete. The overflow menu (edit/delete actions) should be hidden entirely for config-sourced listeners. This is the most transparent approach - users see what's configured and understand they need to modify the config file to change it.

### Q28
> The Admin UI spec mentions "preset templates for common use cases." What specific templates should be offered?

**Answer:**
Out of scope for initial launch - this should be covered only in documentation. Ship with a clean creation form without preset templates. Templates can be added in a future iteration based on actual usage patterns. The empty state should link to documentation with common configuration examples instead.

### Q29
> The spec says "Resource-based permissions for API access" but doesn't specify the resource type or permission model. What is the authorization resource — a new GLOBAL_EXECUTION_LISTENER resource type or reuse of GLOBAL_LISTENER?

**Answer:**
Reuse the existing `GLOBAL_LISTENER` resource type. Add new permission keys: `CREATE_EXECUTION_LISTENER`, `READ_EXECUTION_LISTENER`, `UPDATE_EXECUTION_LISTENER`, `DELETE_EXECUTION_LISTENER`. This follows the same pattern as task listener permissions (`CREATE_TASK_LISTENER`, etc.) on the shared resource type.

### Q30
> The spec says "Listener configuration is not per-tenant. Jobs carry tenantId for downstream routing." Is there any concern about a listener registered by one tenant's admin affecting another tenant's processes?

**Answer:**
No concern. Listeners are cluster-wide by design, consistent with global task listeners. Jobs carry `tenantId` for downstream routing. The job worker decides what to do based on `tenantId`. This is an intentional design choice - cluster administrators manage cluster-wide behavior.

### Q31
> The spec includes a volume impact table but doesn't define any hard limits. Is there a maximum number of global execution listeners allowed per cluster?

**Answer:**
No hard limit enforced. The performance documentation should include guidance on volume implications. The Admin UI should show an inline warning when broad scoping is configured (see Q32). In practice, clusters should rarely need more than 10-15 global execution listeners.

### Q32
> Should the API or Admin UI include warnings when a user configures broad scoping?

**Answer:**
Yes, in the Admin UI only. Show an InlineNotification warning when the user selects `categories: [all]` or selects a large number of element types with both start+end events. Reference the volume impact table from the spec. The API should NOT reject or warn - it should accept valid configurations silently.

### Q33
> Has there been any benchmarking of global task listeners at scale that would inform performance expectations?

**Answer:**
Global task listeners provide a baseline, but element-level scoping (unique to execution listeners) creates significantly more load due to the multiplicative effect. Include basic performance/load testing as part of this epic to validate the volume impact table.

### Q34
> The spec mentions a "migration guide (C7 process engine plugins → global ELs)." What level of detail is expected?

**Answer:**
High-level conceptual mapping. 3-5 common C7 BpmnParseListener patterns mapped to equivalent global EL configurations. Include: (1) audit/logging listeners, (2) variable enrichment listeners, (3) external system notification listeners. Don't aim for exhaustive coverage - focus on the most common patterns with working configuration examples. This is only documentation.

### Q35
> The spec says global ELs "restore the global listener capability lost in the C7→C8 migration." However, C7 BpmnParseListener was non-blocking, while global ELs are blocking job-based. Should the migration guide call out this behavioral difference?

**Answer:**
YES - this is critical. The migration guide MUST prominently call out that C7 `BpmnParseListener` was synchronous but non-blocking (callback-based, in-process), while C8 global ELs are blocking job-based (external worker, network round-trip). This affects latency, error handling, and architecture decisions. Recommend using fast, lightweight job workers for latency-sensitive use cases.

## Engineering (`plan-eng-clarify`)

### Q1
> ZeebeExecutionListenerEventType currently has only start and end — there is no cancel value. Is cancel EL (#2768) being implemented as part of this epic, as a prerequisite by another team, or should we ship global ELs initially without cancel?

**Answer:**
Ship without cancel initially. #2768 is a separate epic. Global ELs should work with the event types available in the engine. When #2768 adds `cancel` to `ZeebeExecutionListenerEventType`, global ELs will automatically support it via the same mechanism.

### Q2
> If we ship without cancel initially, should the API and Admin UI still expose cancel as an event type (rejected at validation time) or omit it entirely from the schema/UI?

**Answer:**
Omit cancel entirely from the schema and UI. Don't expose it as a disabled or rejected option - it creates confusion. When #2768 ships, add `cancel` to the API schema and Admin UI in a follow-up. Clean and simple.

### Q3
> The current GlobalListenerRecord has no categories or elementTypes properties. Adding them is a protocol-level change affecting GlobalListenerRecordValue, golden files, exporters, search indices, RDBMS schema. Is this the intended approach, or should category/elementType expansion happen at a different layer?

**Answer:**
🆘

### Q4
> If categories and elementTypes are stored on the record, should they be stored as-is or expanded/normalized to individual element types before persisting?

**Answer:**


### Q5
> Should the engine interpret an empty/absent elementTypes + categories as categories: [all] implicitly, or should it be required to be explicit?

**Answer:**
Yes. Empty/absent elementTypes + empty/absent categories = implicit `categories: [all]`. This matches the spec behavior and simplifies configuration for the "fire on everything" use case.

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
Yes, it breaks existing bookmarks/deep links. Add a permanent client-side redirect from the old URL to the new URL. Since this is Admin UI (internal navigation, not public API), it's acceptable. Document the URL change in the 8.10 release notes.

### Q21
> Should the Admin UI present element types grouped by the 4 categories or as a flat list with a search filter?

**Answer:**
Grouped by categories. This makes the form more scannable and helps users understand the element type taxonomy. Groups: Processes & Subprocesses (process, subprocess, eventSubprocess, callActivity, adHocSubProcess, multiInstanceBody) → Tasks (serviceTask, userTask, sendTask, receiveTask, scriptTask, businessRuleTask, manualTask) → Gateways (exclusiveGateway, parallelGateway, inclusiveGateway, eventBasedGateway) → Events (startEvent, endEvent, intermediateCatchEvent, intermediateThrowEvent, boundaryEvent).

### Q22
> Should invalid combinations be prevented at selection time (dynamic form validation) or allowed in the form but rejected on submit?

**Answer:**
Dynamic form validation (prevent at selection time). When element scope changes, immediately update which event types are valid. This provides better UX by preventing invalid combinations before submission.

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
Out of scope for this epic, as stated in the spec under "Out of scope." The headers field can be considered in a future iteration if there's demand, but global ELs intentionally omit custom headers to keep the configuration model simple.

### Q28
> Should global execution listeners fire for sequence flows (SEQUENCE_FLOW BpmnElementType)?

**Answer:**
No. Sequence flows are transitions between elements, not elements with meaningful lifecycle events. They don't have start/end semantics in the BPMN execution model. Exclude `SEQUENCE_FLOW` from global EL scope.

### Q29
> Should global execution listener tests follow the exact same test structure as global task listener tests or can we share test infrastructure?

**Answer:**


### Q30
> Should we include performance/load tests as part of this epic?

**Answer:**
es. Include basic performance tests to validate the volume impact table from the spec. At minimum: (1) benchmark `elementTypes: [process]` only (low-volume baseline), (2) benchmark `categories: [all]` with start+end (high-volume scenario), (3) measure latency impact of blocking listeners at scale. This data validates the performance guidance in the documentation.

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
Dropdown trigger only, not clickable to a destination. It expands to show "Task" and "Execution" sub-elements. No landing page for the parent item.

### Q2
> When the user is on /listeners/execution, should both the parent "Listeners" item and the "Execution" sub-element show an active/selected visual state simultaneously, or only the child?

**Answer:**
Yes, both. When on `/listeners/execution`, both "Listeners" parent and "Execution" child should show active/selected visual state. This is standard hierarchical navigation behavior.

### Q3
> Should the redirect from /global-task-listeners → /listeners/tasks be a permanent client-side redirect or should the old route simply render the same component at the new path?

**Answer:**
Permanent client-side redirect from `/global-task-listeners` → `/listeners/tasks`. Clean URL migration. Users hitting the old URL are transparently redirected.

### Q4
> Should "Listeners" with its sub-elements always appear in the nav, or should it be conditional on a feature flag?

**Answer:**
Always visible. No feature flag needed. Permissions gate access to the content, not navigation visibility. The pages are functional for all deployment modes.

### Q5
> Should the nav dropdown arrow/chevron indicator on "Listeners" follow an existing dropdown pattern in Camunda composite components, or is this the first instance of subElements in production?

**Answer:**
Follow whatever pattern `camunda-composite-components` v0.21.5 supports natively.

### Q6
> Should we build a custom hierarchical multi-select component, use Carbon's TreeView inside a dropdown popover, use a flat FilterableMultiSelect with visual group headers, or use something else?

**Answer:**
Carbon's `FilterableMultiSelect` with visual group headers (categories as non-selectable section headers). This uses existing Carbon components, avoids custom component development, and provides search/filter capability. Category-level "select all" checkboxes above each group enable bulk selection.

### Q7
> When a category checkbox is partially selected, should it display an indeterminate (dash) checkbox state?

**Answer:**
Yes. Standard checkbox behavior: unchecked (none selected), indeterminate/dash (some selected), checked (all selected). This provides clear visual feedback about partial category selection.

### Q8
> Should category groups be collapsible/expandable within the dropdown, or always fully expanded?

**Answer:**
Always fully expanded. There are only 4 categories with ~20 total element types. The list is short enough that collapsing adds unnecessary interaction without saving meaningful space.

### Q9
> How should selected items render in the collapsed/trigger state — comma-separated list, category-level summary, count only, or something else?

**Answer:**
Category-level summary when all types in a category are selected (e.g., "All Tasks, All Gateways"). For partial selections, show a count with expandable detail (e.g., "5 element types selected"). Clicking the trigger opens the full dropdown.

### Q10
> When "All" category is selected, should individual element types still be visible and interactable or should the list collapse?

**Answer:**
Yes. When "All" is selected, show all individual types as checked and interactable. Users can deselect individual types from "All" - doing so automatically unchecks the "All" category checkbox and changes it to partial selection of the remaining types.

### Q11
> Should the element type list within the dropdown be searchable/filterable (type-ahead)?

**Answer:**
Yes. Include type-ahead search filtering within the dropdown. With ~20 element types, search helps users quickly find specific types like "businessRuleTask" without scrolling.

### Q12
> What is the visual grouping order of categories, and where do ungrouped element types appear?

**Answer:**
All (top, special category) → Processes & Subprocesses (process, subprocess, eventSubprocess, callActivity, adHocSubProcess, multiInstanceBody) → Tasks (serviceTask, userTask, sendTask, receiveTask, scriptTask, businessRuleTask, manualTask) → Gateways (exclusiveGateway, parallelGateway, inclusiveGateway, eventBasedGateway) → Events (startEvent, endEvent, intermediateCatchEvent, intermediateThrowEvent, boundaryEvent).

### Q13
> Should process be visually separated or highlighted since it's the most common starting point?

**Answer:**
No special visual treatment. Process is one element type among others within the "Processes & Subprocesses" group. The documentation and empty-state guidance can recommend starting with process-level listeners, but the UI should treat all types consistently.

### Q14
> When element scope changes and a previously selected event type becomes invalid, should the system auto-deselect, show an error, show a warning, or prevent the change?

**Answer:**
Auto-deselect the invalid event type with an inline notification explaining why. Example: "Event type 'end' was deselected because it is not supported for gateways." This is the smoothest UX - no modal interruption, clear feedback.

### Q15
> Should unsupported event types be hidden entirely, shown but disabled, or shown with a tooltip?

**Answer:**
Shown but disabled with a tooltip explaining why. Example tooltip: "Not supported for gateways - gateways complete immediately without an end lifecycle." This educates users about the event model without hiding information.

### Q16
> If element types have mixed support for an event, should validation reject the combination, allow it with a warning, or something else?

**Answer:**
Reject the combination with a clear validation error. The API enforces that all eventTypes must be valid for ALL resolved element types. If the user wants mixed support, they need separate listeners for different element type groups. The form should show which specific element types don't support the selected event type.

### Q17
> If cancel is not yet available at launch, should it be hidden, disabled with a tooltip, or omitted from the schema?

**Answer:**
Omit cancel entirely. Don't show it disabled, hidden with tooltip, or in the schema. When #2768 ships, add cancel to both schema and UI in a follow-up. Clean and avoids confusion.

### Q18
> Should event type selection come before or after element scope in the form?

**Answer:**
Element scope first, then event types. The valid event types depend on the element scope selection, so element scope must be set first to enable context-aware event type validation (showing only supported events for the selected element types).

### Q19
> Should the form remain a modal or become a dedicated full-page form given the increased complexity?

**Answer:**
Keep as modal. Consistent with global task listener creation. The form complexity is manageable within a modal - the main additions are element scope (multi-select dropdown) and event types (2-3 checkboxes). If the modal feels crowded in implementation, switch to a full-page form, but start with modal.

### Q20
> What is the maximum modal height before scrolling, and should the form use collapsible sections?

**Answer:**
Max height ~80vh with internal scrolling. No collapsible sections needed. The form has: ID, Job Type, Element Scope dropdown, Event Types checkboxes, Retries, and optional Advanced (priority, afterNonGlobal). This fits comfortably without collapsing.

### Q21
> Where should a "Performance warning" inline notification appear — below the element scope selector, at the top, or as a banner?

**Answer:**
Below the element scope selector. This is where the performance impact becomes clear based on the scope selection. The warning appears/disappears dynamically as the scope selection changes.

### Q22
> Should the performance warning be an InlineNotification, Callout, helper text, or confirmation dialog?

**Answer:**
Carbon `InlineNotification` (kind: "warning"). Standard pattern for contextual warnings within forms. No confirmation dialog - that would block the flow unnecessarily.

### Q23
> At what threshold does the performance warning trigger — only when all category is selected, when resolved element types exceed N, or when start+end are both selected?

**Answer:**
When `categories: [all]` is selected, OR when more than 10 element types are resolved with both `start` and `end` event types selected. The warning text should reference the volume impact guidance: "Broad scoping generates additional blocking jobs for every matching element in every process instance. Start with process-level listeners and add element types as needed."

### Q24
> Should the source field appear as a dedicated column, a Carbon Tag badge inline, both, or only in the detail view?

**Answer:**
Dedicated column in the list view. Use a Carbon Tag/Badge component within the column cell for visual differentiation (e.g., Tag with "API" or "CONFIGURATION" label).

### Q25
> Should configuration-sourced listeners be fully read-only, editable with a warning, hidden, or editable with API override creation?

**Answer:**
Fully read-only. Config-sourced listeners cannot be modified through the UI. Users must modify the configuration file and redeploy.

### Q26
> If config-sourced listeners are read-only, should the overflow menu be hidden entirely or shown but disabled?

**Answer:**
Hidden entirely. Cleaner UX than showing disabled menu items. The read-only nature is already communicated by the "CONFIGURATION" source badge.

### Q27
> What visual treatment distinguishes CONFIGURATION vs API source types?

**Answer:**
Carbon Tag component in the Source column. `API` = teal/blue tag. `CONFIGURATION` = gray/cool-gray tag. The color distinction provides at-a-glance differentiation.

### Q28
> Should all 8 columns display in the execution listener EntityList, or should some be hidden by default with column toggle?

**Answer:**
Show all columns by default. The expected column count (ID, Job Type, Element Scope, Event Types, Retries, Priority, Source, Actions) is manageable. No column toggle needed - the list will typically have few rows (< 20 listeners).

### Q29
> How should "Element scope" render in a narrow table cell — category names, count, truncated list, or expandable chips?

**Answer:**
Category names when all types in a category are selected (e.g., "All Tasks"). For partial/custom selections, show a count (e.g., "3 element types"). Hover tooltip or click to expand shows the full list of resolved element types.

### Q30
> How should the "Event types" cell render when multiple events are selected?

**Answer:**
Carbon Tag chips for each event type. E.g., two small tags: `start` `end`. With only 2-3 possible values, chips are compact and readable.

### Q31
> Should the list support filtering beyond search-by-ID (e.g., by element scope, event type, source)?

**Answer:**
Filter by Source (API/CONFIGURATION) in the initial release. It's the most useful filter for managing mixed config/API listeners. Additional filters (element scope, event type) can be added in future iterations based on demand.

### Q32
> Should the list support bulk delete?

**Answer:**
Not in initial release. Single delete via overflow menu is sufficient for the expected number of listeners (< 20). Bulk operations can be added later if needed.

### Q33
> Should execution listener search also cover jobType?

**Answer:**
Yes. If the list has a search bar, it should search across both ID and jobType. Multiple listeners often share the same jobType, so searching by jobType helps users find related listeners.

### Q34
> Should the execution listener ID be immutable after creation, like task listeners?

**Answer:**
Yes. Consistent with task listeners. ID is set at creation and cannot be changed. Users must delete and recreate to change the ID.

### Q35
> Can element scope be changed on an existing listener, or should it be locked after creation?

**Answer:**
Yes. Element scope can be changed via `PUT` (update). This provides flexibility for configuration adjustments without requiring delete+recreate. Event type validation re-runs on update.

### Q36
> When editing a configuration-sourced listener, should source be displayed as read-only context information?

**Answer:**
Yes. Display the source as read-only context information in the view/detail panel. For config-sourced listeners, this reinforces that the listener originates from configuration and cannot be edited through the UI.

### Q37
> Should the empty state for Execution listeners include an explanation, quick-start guidance, migration docs link, or just a standard message?

**Answer:**
Include: (1) brief explanation: "No global execution listeners configured. Global execution listeners react to process and element lifecycle events across all processes without modifying BPMN models." (2) "Create Listener" primary action button (3) link to documentation. This matches the User Journey in the epic spec.

### Q38
> Should the empty state acknowledge that Task listeners exist if Execution listeners are empty?

**Answer:**
No. Keep the empty states independent. Each listener tab manages its own state. Cross-referencing adds confusion without value.

### Q39
> What documentation URL should the empty state link to?

**Answer:**
Link to the global execution listeners documentation page (to be created as part of this epic's documentation scope). Placeholder URL: `docs.camunda.io/docs/components/concepts/execution-listeners/#global-execution-listeners`

### Q40
> Should "Scope" replace an existing column, or should column widths be redistributed?

**Answer:**
"Scope" is a new column (it doesn't exist for task listeners). Add it to the execution listener list view and redistribute column widths. Don't replace any existing column.

### Q41
> Should the Operate ListenersTab dropdown filter add scope-based filtering options?

**Answer:**
Not in initial release. Operate should add the Source column (Global/BPMN) for filtering. Scope-based filtering (by element type) in Operate is a future enhancement. The Source filter is the critical differentiator for operators.

### Q42
> What visual treatment should the Scope column use in Operate — plain text, colored Tag/Badge, or icon+text?

**Answer:**
Plain text. Simple and readable. E.g., "Global" or "BPMN Model" in the Source column. Tags or badges are overkill for Operate's existing table patterns.

### Q43
> Should clicking a "Global" listener row in Operate deep-link to the corresponding listener in Identity Admin UI?

**Answer:**
Not in initial release. Nice-to-have for a future iteration. Cross-application deep-linking adds complexity. In the initial release, show the listener ID and job type in Operate so users can manually navigate to Admin UI.

### Q44
> Should incidents from global listeners show a different error type, or the same error type with additional metadata?

**Answer:**
Same error type (`EXECUTION_LISTENER_NO_RETRIES`) with additional metadata (Source: Global). Don't fragment error types. The Source column provides the differentiation.

### Q45
> Does the Operate backend (JobEntity / JobRecordValue) currently have a field to distinguish global vs model-level listeners?

**Answer:**
No, it does not exist today. A new field needs to be added to `JobEntity`/`JobRecordValue` (see Engineering Q15). This is a cross-cutting change that is in scope for this epic.

### Q46
> What ARIA role should the hierarchical element scope selector have — tree or listbox?

**Answer:**
`listbox` with `group` role for categories. This is the standard accessible pattern for grouped multi-select components. `tree` is for hierarchical data with parent-child relationships, which doesn't apply here (categories are groups, not parents of element types).

### Q47
> When event type checkboxes dynamically become disabled, should a screen reader announcement notify the user?

**Answer:**
Yes. Use an `aria-live` region (polite) to announce when event type options change due to element scope selection. Example: "Event type 'end' is now disabled because it is not supported for the selected element types."

### Q48
> Should the performance warning InlineNotification be announced immediately or only when focused?

**Answer:**
Announced immediately via `aria-live="polite"` when the warning appears. Screen reader users should be aware of performance implications as they make selections, not only when they tab to the warning.

### Q49
> What is the expected keyboard navigation order through the form?

**Answer:**
ID field → Job Type field → Element Scope selector → Event Types checkboxes → Retries field → Advanced options toggle → (if expanded: Priority, afterNonGlobal) → Cancel button → Submit button. Standard top-to-bottom, left-to-right flow.

### Q50
> Does Identity Admin UI support responsive/mobile layouts, and how should the hierarchical selector render on narrow viewports?

**Answer:**
Stack vertically on narrow viewports. The hierarchical selector renders as a standard dropdown with internal scroll - the same component, just full-width. Admin UI is primarily a desktop tool, so narrow viewport support is secondary but should work reasonably.

### Q51
> Should modal form fields stack vertically on all viewports or use two-column layout on wider viewports?

**Answer:**
Stack vertically on all viewports. Simpler layout, consistent behavior across screen sizes, and consistent with the existing task listener form.

### Q52
> Should BPMN element type names be localized or displayed as canonical identifiers?

**Answer:**
Display user-friendly localized labels (e.g., "Service Task" not "serviceTask"). Map internal camelCase names to display labels. The API uses `serviceTask`, the UI shows "Service Task". Maintain a label mapping table.

### Q53
> Should category names be localized, and what are the translation keys?

**Answer:**
Yes, localize. Translation keys following existing i18n patterns: `globalListeners.category.all`, `globalListeners.category.tasks`, `globalListeners.category.gateways`, `globalListeners.category.events`, `globalListeners.category.processes`.

### Q54
> What is the maximum expected label width for long element type names?

**Answer:**
Longest label: "Intermediate Throw Event" (~25 characters) or "Business Rule Task" (~18 characters). Design for up to 30 characters max. Use text truncation with tooltip for unexpected edge cases.

### Q55
> Should element scope + event type cross-validation happen client-side only, server-side only, or both?

**Answer:**
Both. Client-side for immediate user feedback (dynamic form validation). Server-side for security and consistency (API validates all requests regardless of source). The validation matrix is the single source of truth for both.

### Q56
> What error message format should be used for unsupported event type combinations?

**Answer:**
"Event type '{eventType}' is not supported for element type '{elementType}'. Supported events for {elementType}: [{list}]." For multiple conflicts, list each one. Keep error messages specific and actionable.

### Q57
> Should the UI enforce or warn at a limit on the maximum number of listeners?

**Answer:**
Warn only, don't enforce. Show an InlineNotification when the total number of listeners exceeds a soft threshold (e.g., 15). Message: "Having many global listeners may impact cluster performance. Consider consolidating listeners that share the same job type." No hard limit.

### Q58
> Should the Task and Execution tabs share any UI state or be fully independent?

**Answer:**
Fully independent. No shared UI state. Each tab manages its own list, filters, and pagination. Navigating between tabs does not affect the other tab's state.

### Q59
> If a user creates a listener on the wrong tab, should there be a way to convert/move it?

**Answer:**
No. Task listeners and execution listeners are fundamentally different listener types with different semantics and configurations. Delete and recreate on the correct tab.

### Q60
> Should the list page titles be "Global task listeners" / "Global execution listeners" or just "Task listeners" / "Execution listeners"?

**Answer:**
"Task listeners" / "Execution listeners" (without "Global" prefix). The parent "Listeners" nav item and the context (Admin UI, cluster-level settings) already implies global scope. Keeping titles concise reduces visual noise.
