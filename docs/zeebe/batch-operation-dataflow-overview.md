# Batch Operations – High-Level Data Flow

A simplified overview of how a batch operation flows from creation to completion.

```mermaid
flowchart LR
    USER(["User / REST API"])

    subgraph ENGINE ["Zeebe Broker"]
        direction LR

        subgraph CREATION ["1 · Creation (StreamProcessor, leader)"]
            CREATE["BatchOperationIntent.CREATE\nreceived on leader partition"]
            DISTRIBUTE["CreationRecord distributed\nto all partitions"]
            CREATE --> DISTRIBUTE
        end

        subgraph INITIALIZATION ["2 · Initialization (Scheduler Actor, per partition)"]
            SCHED["BatchOperationExecutionScheduler"]
            QUERY["Query items\npage by page"]
            CHUNKS["Split pages into Chunks\n(BatchOperationChunkIntent.CREATE)\nstored in RocksDB"]
            DONE_INIT["INITIALIZED\n→ trigger EXECUTE"]

            SCHED -->|"INITIALIZE command"| QUERY
            CHUNKS -->|"more pages → loop"| SCHED
            CHUNKS -->|"all pages done"| DONE_INIT
        end

        subgraph EXECUTION ["3 · Execution (StreamProcessor, per partition)"]
            EXEC_PROC["BatchOperationExecutionProcessor\nreads itemKeys from RocksDB"]
            ZEEBE_CMD["Fire-and-forget Zeebe command\n(e.g. CANCEL_PROCESS_INSTANCE)"]
            LOOP["EXECUTE loop\nuntil all items processed"]

            EXEC_PROC --> ZEEBE_CMD
            EXEC_PROC -->|"EXECUTED → remove from RocksDB\n→ next EXECUTE"| LOOP
            LOOP --> EXEC_PROC
        end

        subgraph AGGREGATION ["4 · Aggregation (StreamProcessor, leader)"]
            COLLECT["Leader collects\nCOMPLETE_PARTITION\nfrom every partition"]
            FINAL["Append COMPLETED\n(or FAILED)\nto log stream"]
            COLLECT --> FINAL
        end
    end

    SECONDARY[("Secondary Storage\nES / OS / RDBMS")]

    USER --> CREATE
    CREATION --> INITIALIZATION
    QUERY -.->|"paged API call"| SECONDARY
    SECONDARY -.->|"page results"| CHUNKS
    INITIALIZATION --> EXECUTION
    EXECUTION -->|"COMPLETE_PARTITION\n(inter-partition)"| AGGREGATION
    FINAL -.->|"exported via\nCamundaExporter"| SECONDARY
```

> **Detailed view** → [`batch-operation-dataflow.md`](batch-operation-dataflow.md)
