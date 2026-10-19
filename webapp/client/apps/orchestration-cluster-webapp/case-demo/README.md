# Case management demo

Prototype. Demo models and data for the _Cases_ tab of Tasklist. The design lives in the case management design
project (`CASE_UI.md`, `STAGES.md`, `CASE_DATA.md`, `CASE_IDENTITY.md`, `NEXT_WORK.md`).

## Contents

| File                           | Purpose                                                                                                                                 |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| `loan-application.bpmn`        | The loan case: case marker, two embedded stages, an ad-hoc stage with "I am done" / "Stage done", two more stages, an alternative stage |
| `loan-with-credit-bureau.bpmn` | A case with a call activity. It is outside the case contract, so the case page shows the simplified view                                |
| `credit-bureau-check.bpmn`     | The process that the call activity calls                                                                                                |
| `insurance-claim.bpmn`         | The claim case: deep nesting, a skipped stage, a rework loop, three alternative stages, an optional stage, an incident                  |
| `address-change.bpmn`          | A process without a case marker: the case page shows it without stages                                                                  |
| `forms/`                       | The task forms and the case forms `loan-case-details` and `claim-case-details`                                                          |
| `seed.mjs`                     | Deploys everything and starts cases in different situations; `--only=loan`, `--only=claim` or `--only=address` seeds one group          |
| `start-camunda.sh`             | Starts the orchestration cluster from the local `dist` build, like `make env-up` in `webapp/`                                           |

## The insurance claim case

```
Insurance claim                          process, case marker
├─ Claim registered                      stage; "Fraud suspected?" throws an escalation
├─ (Damage above 10,000 EUR?)            gateway: no skips the next stage
├─ Expert assessment                     stage, deep nesting:
│  ├─ Inspect the damage                 ┐ parallel
│  ├─ Collect repair quotes              ┘ sub-process with a multi-instance task (3 workshops)
│  └─ Expert report                      sub-process
│     └─ Draft the report                sub-process with a nested stage marker: ignored, only top-level stages count
├─ Coverage check                        ad-hoc stage, 2 required and 2 optional tasks, completes on its own
├─ Decision                              stage; "need more information" loops back to Coverage check
├─ Payout                                stage, only when approved
├─ Claim withdrawn                       alternative stage: message
├─ Fraud investigation                   alternative stage: escalation from inside "Claim registered"
├─ Claim expired                         alternative stage: timer, the deadline comes from the variable claimDeadline
└─ Customer contact                      non-interrupting event sub-process: optional stage, see GAP-001
```

## Seeded situations

| Business ID | Situation                                | What the case page shows                                                                 |
| ----------- | ---------------------------------------- | ---------------------------------------------------------------------------------------- |
| `LA-…-01`   | New loan                                 | First stage in progress                                                                  |
| `LA-…-02`   | Loan in the ad-hoc stage                 | Required and optional activities, Done after the required one                            |
| `LA-…-03`   | Loan past Done                           | The ad-hoc stage reads as done, not as cancelled                                         |
| `LA-…-04`   | Loan withdrawn                           | The alternative stage replaces the stages that did not start                             |
| `LA-…-05`   | Loan with a call activity                | Simplified view with the diagram                                                         |
| `CL-…-01`   | New claim                                | First stage in progress                                                                  |
| `CL-…-02`   | Damage above 10,000 EUR                  | Expert assessment with parallel tasks, 2 of 3 multi-instance tasks, nested sub-processes |
| `CL-…-03`   | Damage below 10,000 EUR, customer called | Expert assessment skipped; the task of the optional stage shows next to the ad-hoc stage |
| `CL-…-04`   | Decision "need more information"         | Coverage check runs again: its activities start from zero, Decision reads as not started |
| `CL-…-05`   | Fraud suspected                          | Fraud investigation replaces the line; Claim registered reads as cancelled               |
| `CL-…-06`   | Deadline of 2 minutes                    | After 2 minutes, Claim expired replaces the line                                         |
| `CL-…-07`   | Missing `fraudSuspected`                 | Incident in Claim registered: the stage reads as blocked                                 |
| `CL-…-08`   | Approved                                 | Payout in progress                                                                       |
| `CL-…-09`   | Withdrawn                                | Claim withdrawn replaces the line                                                        |
| `CL-…-10`   | Suspended                                | Badge "Suspended"                                                                        |
| (none)      | No business ID                           | The list and the header show the process instance key                                    |
| `CL-…-12`   | Rejected                                 | Completed case: not in the list, Payout reads as skipped; the seed prints its link       |
| `AC-…-01`   | Address change                           | No case marker: no stages, all open tasks                                                |

## Run

```bash
# 1. Build once from the repository root
./mvnw install -Dquickly -T1C

# 2. Start Elasticsearch and the orchestration cluster
docker compose -f webapp/docker-compose.yml up -d elasticsearch
webapp/client/apps/orchestration-cluster-webapp/case-demo/start-camunda.sh

# 3. Seed the demo data (run it again for more cases)
node webapp/client/apps/orchestration-cluster-webapp/case-demo/seed.mjs

# 4. Start the webapp and open http://localhost:3000/tasklist/cases (user demo / demo)
cd webapp/client && npm run dev:oc
```

## Prototype conventions and deviations from the design

| Topic                           | Design                                                 | Prototype                                                                                                           |
| ------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| Case list                       | Filter by `kind: "CASE_INSTANCE"`                      | `kind` does not exist yet: the list shows every active root process instance                                        |
| Case title                      | `caseTitle` on the process instance                    | Read from the variable that `<zeebe:case title>` names                                                              |
| Case form                       | Latest form by form ID                                 | New endpoint `GET /v2/forms/{formId}/latest`                                                                        |
| Variables of an opened task     | Effective variables from primary storage               | Existing eventually consistent variable search                                                                      |
| After a completion              | The case page opens the next task of the case          | The case page returns to the task list of the case                                                                  |
| "Done" on a stage               | "I am done" publishes the message through a job worker | The case page activates "I am done" and correlates the "Stage done" message itself                                  |
| Optional stage                  | Not decided (GAP-001)                                  | A non-interrupting event sub-process with a stage marker: not on the line, its tasks show next to the current stage |
| "I am done" activity            | Not named by the design                                | The send task with job type `case-stage-done`, or a send task whose name contains "done"                            |
| Correlation key of "Stage done" | Any FEEL expression                                    | Only a variable reference such as `=caseId`, or a static value                                                      |
