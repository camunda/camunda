# AlwaysGreen fix workspace

Four repositories, because an AlwaysGreen failure can need a change in any of them:

|              Repo              |                              When it is the right place to fix                              |
|--------------------------------|---------------------------------------------------------------------------------------------|
| `camunda`                      | product code, the AlwaysGreen workflow itself, this manual                                  |
| `c8-cross-component-e2e-tests` | the Playwright specs and page objects that ran (`tests/SM-8.x/`, `tests/8.x/`)              |
| `camunda-platform-helm`        | chart values, templates, deploy config — including Keycloak/Identity wiring                 |
| `camunda-docs`                 | the authority on intended product behaviour; read before changing what an assertion expects |

The e2e specs execute from the published `@camunda/e2e-test-suite` npm package, so a
report's `file` is a compiled basename. The source lives in
`c8-cross-component-e2e-tests` — the manual explains the mapping.

`camunda` is checked out at the branch that failed. The other three track `main`.
