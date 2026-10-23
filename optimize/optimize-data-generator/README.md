# Zeebe Data Generator

Synthetic Zeebe record generator that writes directly to Elasticsearch, bypassing a live Zeebe
broker. Used to populate the raw Zeebe record indexes so the Optimize importers can run against
realistic data volumes.

## Module

This tool lives in its own Maven module at `optimize/optimize-data-generator/`, a direct
sub-module of `optimize-parent`. It depends on `optimize-backend` (for Zeebe record DTOs,
`optimize-commons`, ES clients, etc.) and produces an executable shaded jar at build time.

## Building

Build the module and produce the shaded uber-jar:

```bash
./mvnw install -pl optimize/optimize-data-generator -am -Dquickly -T1C
```

The shaded jar will be at:

```
optimize/optimize-data-generator/target/optimize-data-generator-<version>-shaded.jar
```

## Running

**From the shaded jar (recommended for automation/scripting):**

```bash
java -jar optimize/optimize-data-generator/target/optimize-data-generator-*-shaded.jar \
  --instances 300000 --agent-rate 0.3
```

**From an IDE:** open `ZeebeDataGeneratorCli`, set the desired program arguments, and run `main()`.

## Options

|      Flag       |    Default     |                                 Description                                  |
|-----------------|----------------|------------------------------------------------------------------------------|
| `--host`        | `localhost`    | Elasticsearch host                                                           |
| `--port`        | `9200`         | Elasticsearch HTTP port                                                      |
| `--username`    | _(none)_       | Basic-auth username                                                          |
| `--password`    | _(none)_       | Basic-auth password                                                          |
| `--prefix`      | `zeebe-record` | Zeebe record index prefix (e.g. `zeebe-record-abc123`)                       |
| `--instances`   | `300000`       | Number of process instances to generate                                      |
| `--defs`        | `6`            | Number of distinct process definitions to use (max 6)                        |
| `--months`      | `6`            | Months of history to spread instances across                                 |
| `--seed`        | `42`           | RNG seed — same seed produces the same data set                              |
| `--batch-size`  | `1000`         | ES bulk-request batch size                                                   |
| `--update-rate` | `0.0`          | Fraction [0.0–1.0] of instances that also receive `UPDATED` variable records |
| `--agent-rate`  | `0.3`          | Fraction [0.0–1.0] of instances that spawn an AI agent instance lifecycle    |

## Incremental Runs

The generator automatically continues from where the previous run left off. On startup it queries
Elasticsearch for:

- **Max `position`** across all Zeebe record indexes (per partition) → used as `positionOffset`
- **Max `processInstanceKey`** in the process-instance index → used as `instanceKeyOffset`

This ensures no document-ID collisions and that Optimize's position-based importer picks up the
new records in the next import cycle.

```bash
# First run — generates instances 3_000_000_000 … 3_000_299_999
java -jar optimize-data-generator-*-shaded.jar --instances 300000

# Second run — continues from max position and instanceKey automatically
java -jar optimize-data-generator-*-shaded.jar --instances 300000
```

## Process Definitions

Six built-in process definitions are available (selected in order by `--defs`):

| # |        Process ID        |    Shape    |
|---|--------------------------|-------------|
| 1 | `order-fulfillment`      | Simple      |
| 2 | `invoice-processing`     | Medium      |
| 3 | `loan-approval`          | Complex     |
| 4 | `claim-processing`       | Branching   |
| 5 | `customer-onboarding`    | Event-based |
| 6 | `fraud-dispute-handling` | Fraud       |

## Variables Per Instance

Each instance receives:

**Business variables** (always present):
`customerId`, `orderId`, `amount`, `status`, `requiresReview`, `priority`

**Core `REPORTING_PROCESS_*` metrics** (always present):

|           Variable name            |  Type  |          Range           |
|------------------------------------|--------|--------------------------|
| `REPORTING_PROCESS_baselineCost`   | double | 400 – 2 000              |
| `REPORTING_PROCESS_llmCost`        | double | 20 – 300                 |
| `REPORTING_PROCESS_automationCost` | double | 50 – 400                 |
| `REPORTING_PROCESS_totalCost`      | double | llmCost + automationCost |
| `REPORTING_PROCESS_valueCreated`   | double | 40–100 % of baselineCost |
| `REPORTING_PROCESS_agentTaskCount` | int    | 0 – 5                    |
| `REPORTING_PROCESS_humanTaskCount` | int    | 0 – 3                    |
| `REPORTING_PROCESS_autoTaskCount`  | int    | 1 – 6                    |
| `REPORTING_PROCESS_tokenUsage`     | long   | 1 000 – 50 000           |

**Optional `REPORTING_PROCESS_*` metrics** (each present with 60 % probability):

`errorCount`, `retryCount`, `processingTimeMs`, `queueWaitTimeMs`, `apiCallCount`,
`complianceChecksPassed`, `dataVolumeMb`, `confidenceScore`, `co2EmissionsKg`,
`customerSatisfactionScore`, `fraudRiskScore`, `externalServiceCostUsd`,
`slaBreached`, `escalated`, `manualOverride`

## Simulating Variable Updates (`--update-rate`)

By default all variable records use `VariableIntent.CREATED`. Passing `--update-rate` causes the
generator to emit an additional batch of `VariableIntent.UPDATED` records **after** all `CREATED`
records, at higher positions. This simulates mid-flight variable updates on existing process
instances.

The Optimize importer processes these in a later round (higher position → picked up on next cycle),
merging the new field values into existing docs. Each merge produces a soft-deleted Lucene document,
visible in index stats as `docs.deleted > 0`.

```bash
# 30 % of instances get an UPDATED variable record at a higher position
java -jar optimize-data-generator-*-shaded.jar --instances 100000 --update-rate 0.3
```

## Instance State Distribution

|     State     |        Rate        |
|---------------|--------------------|
| Active        | ~3 %               |
| Terminated    | ~5 % of non-active |
| With incident | ~5 % of non-active |
| Completed     | remainder          |

## Agent Instances

Instances whose path passes one of the three agent elements (document review ad-hoc sub-process in
`customer-onboarding`, risk assessment AI Agent Task in `loan-approval`, external fraud review agent
in `fraud-dispute-handling`) get an agent run with probability `--agent-rate`. Deploying the
processes also writes one `AGENT_DEFINITION` CREATED record per agent and process version to
`zeebe-record-agent-definition`.

Runs are written the way the engine records them (see `AgentRunSimulator`):

| Aspect | Generated data |
|---|---|
| Records | CREATED, one UPDATED per model call and per tool round (`changedAttributes` = `metrics`, `status`), a status-only IDLE, COMPLETED at process end |
| Duration | The run is active for 15-55 % of the process instance, so run duration differs from process duration |
| Model | About 8 % of runs switch from Bedrock to OpenAI through a config-commit UPDATED (`changedAttributes` = `model`, `provider`) |
| Limits | About 40 % of runs have no limits (`-1`); about 10 % of all runs reach their model call limit |
| Tokens | Input/output always; cache creation/read for 70 % of runs (20 % cache-heavy); reasoning only for `o3-mini`, included in output |
| Tools | Ad-hoc sub-process tools run as flow node instances inside the sub-process, as often as the run called them, with uneven weights; the other agents' tools have no BPMN element |
| Incidents | About 12 % of finished runs get an incident on the agent element, a third of them after the run's last activity |

Records use Optimize's own `ZeebeAgentInstanceRecordDto` / `ZeebeAgentDefinitionRecordDto`.

```bash
# Generate with 50 % of instances having agent instances
java -jar optimize-data-generator-*-shaded.jar --instances 100000 --agent-rate 0.5

# Disable agent instance generation
java -jar optimize-data-generator-*-shaded.jar --instances 100000 --agent-rate 0.0
```

