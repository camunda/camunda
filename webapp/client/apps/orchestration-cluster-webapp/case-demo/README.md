# Case management demo

Prototype. Demo models and data for the _Cases_ tab of Tasklist. The design lives in the case management design
project (`CASE_UI.md`, `STAGES.md`, `CASE_DATA.md`, `CASE_IDENTITY.md`, `NEXT_WORK.md`).

## Contents

| File                           | Purpose                                                                                                                                 |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| `loan-application.bpmn`        | The loan case: case marker, two embedded stages, an ad-hoc stage with "I am done" / "Stage done", two more stages, an alternative stage |
| `loan-with-credit-bureau.bpmn` | A case with a call activity. It is outside the case contract, so the case page shows the simplified view                                |
| `credit-bureau-check.bpmn`     | The process that the call activity calls                                                                                                |
| `forms/`                       | The task forms and the case form `loan-case-details`                                                                                    |
| `seed.mjs`                     | Deploys everything and starts five cases in different stages                                                                            |
| `start-camunda.sh`             | Starts the orchestration cluster from the local `dist` build, like `make env-up` in `webapp/`                                           |

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

| Topic                           | Design                                                 | Prototype                                                                                |
| ------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| Case list                       | Filter by `kind: "CASE_INSTANCE"`                      | `kind` does not exist yet: the list shows every active root process instance             |
| Case title                      | `caseTitle` on the process instance                    | Read from the variable that `<zeebe:case title>` names                                   |
| Case form                       | Latest form by form ID                                 | New endpoint `GET /v2/forms/{formId}/latest`                                             |
| Variables of an opened task     | Effective variables from primary storage               | Existing eventually consistent variable search                                           |
| After a completion              | The case page opens the next task of the case          | The case page returns to the task list of the case                                       |
| "Done" on a stage               | "I am done" publishes the message through a job worker | The case page activates "I am done" and correlates the "Stage done" message itself       |
| "I am done" activity            | Not named by the design                                | The send task with job type `case-stage-done`, or a send task whose name contains "done" |
| Correlation key of "Stage done" | Any FEEL expression                                    | Only a variable reference such as `=caseId`, or a static value                           |
