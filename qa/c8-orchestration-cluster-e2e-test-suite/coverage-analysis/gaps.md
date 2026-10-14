# Coverage gaps (heuristic)

## Entities missing delete-then-observe-absence variant

Looks for entities with create + delete tests but no `observe-absence` tag.

- **authorization** — has create+delete but no "observe absence" test name
- **clock** — has create+delete but no "observe absence" test name
- **cluster-variables** — has create+delete but no "observe absence" test name
- **decision-instance** — has create+delete but no "observe absence" test name
- **document** — has create+delete but no "observe absence" test name
- **group** — has create+delete but no "observe absence" test name
- **mapping-rule** — has create+delete but no "observe absence" test name
- **resource** — has create+delete but no "observe absence" test name
- **role** — has create+delete but no "observe absence" test name
- **tenant** — has create+delete but no "observe absence" test name
- **user** — has create+delete but no "observe absence" test name

## Entities with no unauthorized (401) coverage

- clock
- expression
- optimize

## Entities with no bad-request (400) coverage

- authentication
- cluster
- expression
- license
- message-subscriptions
- optimize
