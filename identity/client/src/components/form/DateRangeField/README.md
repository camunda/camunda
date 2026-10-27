# Date Range Field (React Hook Form version)

This component and its related validators are inspired by the original
`DateRangeField` implementation used in Operate:

https://github.com/camunda/camunda/tree/main/operate/client/src/modules/components/DateRangeField

The original implementation is built on top of **react-final-form**.
This version adapts the same behavior and validation logic to work with
**react-hook-form**.

## What’s Included

- `DateInput`
- `TimeInput`
- Time and range validators (`validateTimeCharacters`, `validateTimeComplete`, `validateTimeRange`)
- Integration via `Controller` from `react-hook-form`

The goal was to preserve:

- UX behavior
- Validation semantics
- Error messaging
- Edge case handling

while aligning with the form architecture used in Identity.

---

## Differences from the Operate Version

- Uses `react-hook-form` instead of `react-final-form`
- Validators follow the RHF validation contract:
  - `true` → valid
  - `string` → error message
  - `Promise<true | string>` → async validation
- No dependency on `FieldValidator` or final-form types

---

## Long-Term Direction

Ideally, we want to:

1. Extract a **form-library-agnostic DateRange component**
2. Move shared logic (parsing, validation, formatting) into a common package
3. Standardize the validation layer
4. Replace both the Operate and Identity implementations with the generic version

The long-term goal is to:

- Avoid duplication
- Ensure consistent UX across applications
- Decouple design components from specific form libraries
- Keep validation logic reusable and framework-agnostic

---

## Architectural Intent

Short term:

- Keep compatibility with the current form setup (RHF in Identity, final-form in Operate).
- Offering the same user experience and validation behavior in both applications without forcing a major refactor.

Long term:

- Introduce a shared, form-library-agnostic component in a common package
  (e.g., `composite-components`).
- Provide thin adapters per form library if needed.

This implementation is therefore a transitional step toward a unified solution.
