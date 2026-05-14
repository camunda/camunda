---
name: Operate Notebook detail widget
description: A single-entity card widget added to the registry alongside table/bpmn/metric/chart/action
---

Notebook widget registry includes a `detail` type: single-entity card showing chosen fields for one process instance / incident / etc.

**Why:** Stephan asked for it during design. Useful demo case ("show me details of incident X"), cheap to implement, complements `table` (list view).

**How to apply:**
- Renders as Carbon `<StructuredListWrapper>` — labeled rows, one per field.
- Config shape: `{ type: "detail", title, query: { endpoint, method: "GET", pathParams }, fields: [...] }`.
- Schema extension: existing widget config draft only had `query.body` for POST searches. `detail` introduces `query.pathParams` for GET-by-id endpoints. Both should be supported alongside one another.
- `fields` are validated against the endpoint's Zod response schema, same retry policy as `columns` for tables (one LLM retry on validation failure).
