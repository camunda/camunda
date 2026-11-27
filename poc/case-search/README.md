# Case search POC

Proof of concept for searching "cases": process instances of a process definition whose
`subProcess` elements act as stages (`case-poc.bpmn`).

- The engine writes `CASE` records (`CREATED`, `STAGE_UPDATED`, `COMPLETED`, ...) carrying a stage
  graph (order, `alternative` flag, next stages) that is computed once at deploy time.
- The camunda-exporter upserts them into the `camunda-case-8.11.0_` index (`CaseHandler`).
- `POST /v2/cases/search` returns the cases together with their stages and the state of each stage.

## Run the end-to-end test

The acceptance test deploys the demo process, walks one case through every stage (including the
loop back to Intake, the escalation path and the `withdraw` signal), leaves a second case active,
and searches both over REST against Elasticsearch (testcontainer, Docker required).

```sh
./gradlew :camunda-qa-acceptance-tests:itMultiDb --tests 'io.camunda.it.cases.CaseSearchPocIT'
```

## Try it by hand

Deploy `case-poc.bpmn` and start an instance with a `caseTitle`, then:

```sh
# search with a filter and a sort
curl -s -X POST http://localhost:8080/v2/cases/search \
  -H 'Content-Type: application/json' \
  -d '{"filter":{"state":"ACTIVE"},"sort":[{"field":"startDate","order":"DESC"}]}'

# trigger the alternative "Withdrawal" stage of all running cases
curl -s -X POST http://localhost:8080/v2/signals/broadcast \
  -H 'Content-Type: application/json' \
  -d '{"signalName":"withdraw"}'
```

## Sample response

See [sample-response.json](sample-response.json): a completed case (all stages walked, Resolution
terminated by the withdrawal) and an active case (waiting in Review).
