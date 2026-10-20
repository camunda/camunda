# Case management demo

Prototype. Demo models and data for the _Cases_ tab of Tasklist. The design lives in the case management design
project (`CASE_UI.md`, `STAGES.md`, `CASE_DATA.md`, `CASE_IDENTITY.md`, `NEXT_WORK.md`).

## Contents

| File                           | Purpose                                                                                                                                 |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| `loan-application.bpmn`        | The loan case: case marker, two embedded stages, an ad-hoc stage with "I am done" / "Stage done", two more stages, an alternative stage |
| `loan-with-credit-bureau.bpmn` | A case with a call activity. It is outside the case contract, so the case page shows the simplified view                                |
| `credit-bureau-check.bpmn`     | The process that the call activity calls                                                                                                |
| `insurance-claim.bpmn`         | The claim case: deep nesting, a skipped stage, a rework loop, three alternative stages, an incident                                     |
| `employee-onboarding.bpmn`     | The onboarding case: stages with sequential tasks, a loop inside a stage, an automatic step, an alternative stage                       |
| `supplier-onboarding.bpmn`     | The supplier case: stages that mix people and automation, with service tasks, a business rule task, timers and a message catch event    |
| `supplier-risk.dmn`            | The decision that the business rule task of the supplier case calls                                                                     |
| `address-change.bpmn`          | A process without a case marker: the case page shows it without stages                                                                  |
| `forms/`                       | The task forms and the case forms `loan-case-details`, `claim-case-details`, `onboarding-case-details` and `supplier-case-details`      |
| `seed.mjs`                     | Deploys everything and starts cases in different situations; `--only=<loan, claim, onboarding, supplier or address>` seeds one group    |
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
└─ Claim expired                         alternative stage: timer, the deadline comes from the variable claimDeadline
```

## The employee onboarding case

```
Employee onboarding                      process, case marker
├─ Preparation                           stage, sequential tasks:
│  Create the contract → Collect the signed contract → (Contract signed?)
│     no:  Follow up with the candidate → back to Collect the signed contract
│     yes: Order the equipment → Generate the employee ID (script task, no person)
├─ First day                             Welcome the employee → Hand over the equipment → Set up the accounts
├─ Training                              Complete the safety training → Shadow a colleague → Sign off the training
├─ Probation                             Hold the mid-probation review → Hold the final review
└─ Offer declined                        alternative stage: message
```

## The supplier onboarding case

```
Supplier onboarding                      process, case marker
├─ Registration                          Register the supplier (user task)
│                                        → Check the sanctions list (service task, job type sanctions-check)
│                                        → Classify the supplier risk (business rule task, decision supplier-risk)
├─ Due diligence                         ad-hoc stage:
│  ├─ Review the risk assessment         required, user task
│  ├─ Request the credit report          required, service task (credit-report);
│  │                                     interrupting timer "No report in time" → Estimate the credit risk
│  ├─ Visit the supplier                 optional, user task
│  └─ Request the ESG rating             optional, service task (esg-rating)
├─ Contract                              input mapping adds "Contract owner" to the header while the stage runs
│                                        Negotiate the terms → Sign the contract → Countersigned contract received
│                                        (message catch event); non-interrupting timer on Sign the contract
│                                        → Remind the supplier
├─ Activation                            Create the supplier in the ERP (service task, erp-create-supplier)
│                                        → Wait for the first order (timer catch event) → Review the first order
└─ Supplier blocked                      alternative stage: BPMN error SANCTIONS_HIT from the sanctions check
```

The seed plays the job workers: it completes, fails or throws an error on the job of each case, so every case
stops where a system, a timer or a message holds it. Nothing works on the jobs after the seed ends. The timers
read their durations from the variables `creditReportTimeout`, `signatureReminder` and `firstOrderWait`.

## Seeded situations

| Business ID | Situation                           | What the case page shows                                                                    |
| ----------- | ----------------------------------- | ------------------------------------------------------------------------------------------- |
| `LA-…-01`   | New loan                            | First stage in progress                                                                     |
| `LA-…-02`   | Loan in the ad-hoc stage            | Required and optional activities, Done after the required one                               |
| `LA-…-03`   | Loan past Done                      | The ad-hoc stage reads as done, not as cancelled                                            |
| `LA-…-04`   | Loan withdrawn                      | The alternative stage replaces the stages that did not start                                |
| `LA-…-05`   | Loan with a call activity           | Simplified view with the diagram                                                            |
| `CL-…-01`   | New claim                           | First stage in progress                                                                     |
| `CL-…-02`   | Damage above 10,000 EUR             | Expert assessment with parallel tasks, 2 of 3 multi-instance tasks, nested sub-processes    |
| `CL-…-03`   | Damage below 10,000 EUR             | Expert assessment skipped; required tasks first, then optional ones                         |
| `CL-…-04`   | Decision "need more information"    | Coverage check runs again: its activities start from zero, Decision reads as not started    |
| `CL-…-05`   | Fraud suspected                     | Fraud investigation replaces the line; Claim registered reads as cancelled                  |
| `CL-…-06`   | Deadline of 2 minutes               | After 2 minutes, Claim expired replaces the line                                            |
| `CL-…-07`   | Missing `fraudSuspected`            | Incident in Claim registered: the stage reads as blocked                                    |
| `CL-…-08`   | Approved                            | Payout in progress                                                                          |
| `CL-…-09`   | Withdrawn                           | Claim withdrawn replaces the line                                                           |
| `CL-…-10`   | Suspended                           | Badge "Suspended"                                                                           |
| (none)      | No business ID                      | The list and the header show the process instance key                                       |
| `CL-…-12`   | Rejected                            | Completed case: not in the list, Payout reads as skipped; the seed prints its link          |
| `ON-…-01`   | New onboarding                      | Preparation, step 1: Create the contract                                                    |
| `ON-…-02`   | Contract created                    | Preparation, step 2: Collect the signed contract                                            |
| `ON-…-03`   | Contract not signed                 | The loop inside Preparation: Follow up with the candidate                                   |
| `ON-…-04`   | Preparation done, employee welcomed | First day, step 2; the header shows the employee ID from the script task                    |
| `ON-…-05`   | First day done, two trainings done  | Training, step 3: Sign off the training                                                     |
| `ON-…-06`   | Mid-probation review done           | Probation, step 2: Hold the final review                                                    |
| `ON-…-07`   | Offer declined                      | Offer declined replaces the line; Preparation reads as cancelled                            |
| `SO-…-01`   | New supplier                        | Registration, step 1: Register the supplier                                                 |
| `SO-…-02`   | Waiting on the sanctions check      | The stage lists the running service task "Check the sanctions list"                         |
| `SO-…-03`   | Sanctions hit                       | The BPMN error starts Supplier blocked, which replaces the line                             |
| `SO-…-04`   | Due diligence, high risk            | Required user task open, required service task running; header shows the risk level         |
| `SO-…-05`   | Credit report times out             | After 1 minute, the timer cancels the credit report and opens Estimate the credit risk      |
| `SO-…-06`   | Optional ESG rating running         | Both required activities done, the optional service task still running                      |
| `SO-…-07`   | Reminder due                        | After 1 minute, Remind the supplier opens next to Sign the contract; header: Contract owner |
| `SO-…-08`   | Waiting for the countersignature    | The stage lists the message catch event as waiting                                          |
| `SO-…-09`   | Waiting for the first order         | The stage lists the timer as waiting; the case form shows the ERP supplier number           |
| `SO-…-10`   | ERP rejected the supplier           | The failed job raises an incident: the stage reads as blocked, the step as incident         |
| `SO-…-11`   | First order due                     | After 1 minute, the timer opens Review the first order                                      |
| `AC-…-01`   | Address change                      | No case marker: no stages, all open tasks                                                   |

## Run

```bash
# 1. Build once from the repository root
./mvnw install -Dquickly -T1C

# 2. Start Elasticsearch and the orchestration cluster
docker compose -f webapp/docker-compose.yml up -d elasticsearch
webapp/client/apps/orchestration-cluster-webapp/case-demo/start-camunda.sh

# 3. Seed the demo data (run it again for more cases)
node webapp/client/apps/orchestration-cluster-webapp/case-demo/seed.mjs

# Or one case per diagram
node webapp/client/apps/orchestration-cluster-webapp/case-demo/seed.mjs --single

# 4. Start the webapp and open http://localhost:3000/tasklist/cases (user demo / demo)
cd webapp/client && npm run dev:oc
```

### A SaaS cluster

The seed talks to a SaaS cluster with the client credentials of an API client. Set `CAMUNDA_URL` to the REST
address of the cluster (`CAMUNDA_REST_ADDRESS` and `ZEEBE_REST_ADDRESS` work too), `CAMUNDA_CLIENT_ID`,
`CAMUNDA_CLIENT_SECRET`, `CAMUNDA_OAUTH_URL` and `CAMUNDA_TOKEN_AUDIENCE`, then run the seed. `WEBAPP_URL` sets
the base of the printed case links.

## Prototype conventions and deviations from the design

| Topic                           | Design                                                 | Prototype                                                                                |
| ------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| Case list                       | Filter by `kind: "CASE_INSTANCE"`                      | `kind` does not exist yet: the list shows every active root process instance             |
| Case title                      | `caseTitle` on the process instance                    | Read from the variable that `<zeebe:case title>` names                                   |
| Case form                       | Latest form by form ID                                 | New endpoint `GET /v2/forms/{formId}/latest`                                             |
| Variables of an opened task     | Effective variables from primary storage               | Existing eventually consistent variable search                                           |
| After a completion              | The case page opens the next task of the case          | The case page returns to the task list of the case                                       |
| "Done" on a stage               | "I am done" publishes the message through a job worker | The case page activates "I am done" and correlates the "Stage done" message itself       |
| Optional stage                  | Not decided (GAP-001)                                  | Not supported: a stage marker on a non-interrupting event sub-process is ignored         |
| "I am done" activity            | Not named by the design                                | The send task with job type `case-stage-done`, or a send task whose name contains "done" |
| Correlation key of "Stage done" | Any FEEL expression                                    | Only a variable reference such as `=caseId`, or a static value                           |
