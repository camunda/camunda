# @camunda/case-management-sdk

Prototype. Derives the case state from the BPMN XML and the orchestration cluster APIs, as defined in the
case management design (`CASE_UI.md`, `STAGES.md`, `CASE_DATA.md`). Tasklist and custom case applications share it.

The engine has no case or stage entity. This package reads:

- the case marker `<zeebe:case title="…" caseForm="…" />` on the process
- the stage marker `<zeebe:stage />` on top-level sub-processes, ad-hoc sub-processes and event sub-processes

## API

| Function                        | Purpose                                                                                |
| ------------------------------- | -------------------------------------------------------------------------------------- |
| `parseCaseModel(xml, id)`       | Case marker, stages in sequence-flow order, required and optional tasks, manual "Done" |
| `getCaseProgress(model, ei, s)` | State of each stage from the element instance search                                   |
| `getCurrentStage(progress)`     | The stage that is in progress                                                          |
| `getCaseHeader(variable)`       | The `caseHeader` label-value pairs                                                     |
| `getCaseTitle(variable)`        | The case title from the variable the case marker names                                 |
| `getDocumentReferences(vars)`   | Document references in variable values                                                 |

## Prototype conventions

- The "I am done" activity of a stage is the send task with the job type `case-stage-done`, or a send task whose
  name contains "done".
- Required tasks come from a static `activeElementsCollection` list of strings, for example `=["a", "b"]`.
