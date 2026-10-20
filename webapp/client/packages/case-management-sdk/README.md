# @camunda/case-management-sdk

Prototype. Derives the case state from the BPMN XML and the orchestration cluster APIs, as defined in the
case management design (`CASE_UI.md`, `STAGES.md`, `CASE_DATA.md`). Tasklist and custom case applications share it.

The engine has no case or stage entity. This package reads:

- the case marker `<zeebe:case title="…" caseForm="…" />` on the process
- the stage marker `<zeebe:stage />` on top-level sub-processes, ad-hoc sub-processes and event sub-processes

## API

| Function                        | Purpose                                                                                            |
| ------------------------------- | -------------------------------------------------------------------------------------------------- |
| `parseCaseModel(xml, id)`       | Case marker, stages in sequence-flow order, required and optional tasks, manual "Done"             |
| `getCaseProgress(model, ei, s)` | State of each stage from the element instance search                                               |
| `getCurrentStage(progress)`     | The stage that is in progress                                                                      |
| `getCaseHeader(variable)`       | The `caseHeader` label-value pairs                                                                 |
| `getCaseTitle(variable)`        | The case title from the variable the case marker names                                             |
| `getDocumentReferences(vars)`   | Document references in variable values                                                             |
| `getCaseVariableNames(model)`   | The root-scope variables to request: title variable, case header, correlation keys of "Stage done" |
| `getVariableReference(expr)`    | The variable that an expression such as `=caseId` reads                                            |

A case application requests only the variables that `getCaseVariableNames` returns, plus the fields of the case form,
never all variables of a case. The BPMN does not name the document variables: search them by value, for example
`value: {$like: "*camunda.document.type*"}`.

## Stage kinds

| Kind          | BPMN                           | On the line                                                             |
| ------------- | ------------------------------ | ----------------------------------------------------------------------- |
| `subProcess`  | Embedded sub-process           | Yes, in the order of the sequence flows                                 |
| `adHoc`       | Ad-hoc sub-process             | Yes                                                                     |
| `alternative` | Interrupting event sub-process | Only once the case enters it; it replaces the stages that did not start |

A stage marker below the top level is ignored. Optional stages are not supported (GAP-001): a stage marker on a
non-interrupting event sub-process is ignored. A loop back to an earlier stage (rework) runs it again: the stages
after it that ended before the new run read as ahead.

## Prototype conventions

- The "I am done" activity of a stage is the send task with the job type `case-stage-done`, or a send task whose
  name contains "done".
- Required tasks come from a static `activeElementsCollection` list of strings, for example `=["a", "b"]`.
- Dates are compared as timestamps: the searches return them with different offsets.
