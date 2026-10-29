# Variable history PoC load tests

PoC for [camunda/pdp-3517-variable-trace#53](https://github.com/camunda/pdp-3517-variable-trace/issues/53). Not meant to be merged.

Every run uses the same image and the same workload. Only the share of variable changes written to the variable history changes, set by `camunda.data.audit-log.variable-history-percent` (0 is the baseline).

## Workload

The realistic scenario with large values, selected through `load-test-load`:

```
--set load-tester.starter.bpmnXmlPath=bpmn/realistic/bankCustomerComplaintDisputeHandlingVariableHistory.bpmn --set load-tester.starter.payloadPath=bpmn/realistic/variableHistoryPayload.json --set load-tester.workers.extract-data-from-document.payloadPath=bpmn/realistic/variableHistoryExtractPayload.json --set load-tester.workers.refunding.payloadPath=bpmn/realistic/variableHistoryRefundPayload.json
```

Add `--set load-tester.starter.rate=<n>` to change the rate.

## Share of recorded changes

Picked with `platform-helm-values`, from the files in `load-tests/setup/main/values/variable-history-*.yaml`:

|   Run    |                                     Elasticsearch                                     |                                         PostgreSQL                                         |
|----------|---------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Baseline | nothing                                                                               | `--set-file 'orchestration.extraConfiguration[1].content=./variable-history-0-rdbms.yaml'` |
| 33%      | `--set-file 'orchestration.extraConfiguration[1].content=./variable-history-33.yaml'` | `...=./variable-history-33-rdbms.yaml'`                                                    |
| 66%      | `...=./variable-history-66.yaml'`                                                     | `...=./variable-history-66-rdbms.yaml'`                                                    |
| 100%     | `...=./variable-history-100.yaml'`                                                    | `...=./variable-history-100-rdbms.yaml'`                                                   |

The PostgreSQL files also shorten the history time to live to 10 minutes, for the baseline too, so the history cleanup runs inside the measured window.

## Result list items only

`camunda.data.audit-log.variable-history-collection-items: true` records only the item that each update of a multi-instance output collection adds, instead of the whole list. The engine tags each such update with the slot it fills. Pick it with `variable-history-100-items.yaml` (Elasticsearch) or `variable-history-100-items-rdbms.yaml` (PostgreSQL).

## More hardware

The hardware runs record 100% and add storage capacity through `load-test-setup-helm-values`:

- Elasticsearch with more nodes, and the history index spread over one primary shard per node: `--set elasticsearch.count=5` with `variable-history-100-es-5shards.yaml`, or `--set elasticsearch.count=6` with `variable-history-100-es-6shards.yaml`.
- PostgreSQL with more CPU and memory, moved to the 8 core node pool: `--set postgresql.nodeSelector.component=benchmark-n2-standard-8` with all four `postgresql.tolerations[0]` fields (`key=nodepool`, `operator=Equal`, `value=n2-standard-8`, `effect=NoSchedule`; setting only `value` drops the others), plus `postgresql.resources`, `postgresql.parameters.shared_buffers`, `effective_cache_size` and `postgresql.storage.size`.
- PostgreSQL with a higher WAL limit: `--set postgresql.parameters.max_wal_size=16GB` (default 4GB).
- Brokers with more CPU go through `platform-helm-values`, after the `--set-file`: `--set orchestration.resources.requests.cpu=4500m --set orchestration.resources.limits.cpu=4500m --set orchestration.nodeSelector.component=benchmark-n2-standard-8` with the same four `orchestration.tolerations[0]` fields. The 4 core broker nodes leave room for about 3 cores, so more CPU needs the 8 core pool.

`stable-vms=true` only moves the brokers to non-spot nodes. Elasticsearch and PostgreSQL always run on spot nodes.

## Dispatch

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda --ref bcan/53-variable-history-poc \
  -f name=<namespace> -f ref=bcan/53-variable-history-poc -f scenario=realistic \
  -f secondary-storage-type=<elasticsearch|postgresql> -f stable-vms=true -f enable-optimize=false -f ttl=2 \
  -f load-test-load="<workload above>" -f platform-helm-values="<share above>" [-f reuse-tag=<tag>]
```

After the first build, pass `reuse-tag` so every run uses the same image.
