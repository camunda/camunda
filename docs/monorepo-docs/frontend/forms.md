# Forms

This page covers the form library landscape across the Camunda
frontends today and the best practices to follow when picking and
writing forms in `@camunda/orchestration-cluster-webapp`.

## Current state

| App                                     | Library          |
| --------------------------------------- | ---------------- |
| `@camunda/orchestration-cluster-webapp` | react-final-form |
| Tasklist (`tasklist/client`)            | react-final-form |
| Operate (`operate/client`)              | react-final-form |
| Identity (`identity/client`)            | react-hook-form  |

## Future direction

We plan to unify on a single form library across the unified webapp.
The candidates are [react-hook-form](https://react-hook-form.com/) and
[@tanstack/react-form](https://tanstack.com/form/latest). No decision
has been made yet.

Until the unification lands, new forms in
`@camunda/orchestration-cluster-webapp` follow the guidance in the
sections below.

## When to reach for a form library

Decision rule:

- **Simple form** → plain HTML `<form>` with native validation. No
  library needed.
- **Reach for a library** when the form needs either of:
  - schema-based validation (cross-field, async, or otherwise complex
    rules), or
  - form / field meta state — `dirty`, `touched`, `pristine`, blur
    tracking, submission state, field arrays, etc.

Concrete examples:

- A single-input search box → plain HTML.
- A multi-step creation form with conditional fields and per-field
  errors → form library.

## Validation strategy

- **On submit** → validate the whole form.
- **On blur** → validate only the field that lost focus.
- Do **not** validate on every keystroke — it's noisy and disruptive
  to users still typing.

Reuse Zod schemas from `@camunda/camunda-api-zod-schemas` where the
form maps to an API contract; otherwise co-locate a Zod schema next
to the form that uses it.

## References

- [react-final-form](https://final-form.org/react)
- [react-hook-form](https://react-hook-form.com/)
- [Camunda API Zod schemas](./camunda-api-zod-schemas.md)
