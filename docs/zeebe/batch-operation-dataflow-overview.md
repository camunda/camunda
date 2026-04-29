# Batch Operations – High-Level Data Flow

A simplified overview of how a batch operation flows from creation to completion.

```mermaid
flowchart TB
    USER(["User / REST API"])

    subgraph CREATION ["1 · Creation"]
        CREATE["BatchOperationIntent.CREATE\nreceived on leader partition"]
        DISTRIBUTE["CreationRecord distributed\nto all partitions"]
        CREATE --> DISTRIBUTE
    end

    subgraph INITIALIZATION ["2 · Initialization  (per partition, async)"]
        SCHED["BatchOperationExecutionScheduler"]
        QUERY["Query Secondary Storage\n(ES / OS / RDBMS)\npage by page"]
        CHUNKS["Split pages into Chunks\n(BatchOperationChunkIntent.CREATE)\nstored in RocksDB"]
        DONE_INIT["INITIALIZED\n→ trigger EXECUTE"]

        SCHED -->|"INITIALIZE command"| QUERY
        QUERY -->|"page results"| CHUNKS
        CHUNKS -->|"more pages → loop"| SCHED
        CHUNKS -->|"all pages done"| DONE_INIT
    end

    subgraph EXECUTION ["3 · Execution  (per partition, async loop)"]
        EXEC_PROC["BatchOperationExecutionProcessor\nreads itemKeys from RocksDB"]
        ZEEBE_CMD["Fire-and-forget Zeebe command\n(e.g. CANCEL_PROCESS_INSTANCE)"]
        LOOP["EXECUTE loop\nuntil all items processed"]

        EXEC_PROC --> ZEEBE_CMD
        EXEC_PROC -->|"EXECUTED → remove from RocksDB\n→ next EXECUTE"| LOOP
        LOOP --> EXEC_PROC
    end

    subgraph AGGREGATION ["4 · Aggregation  (leader partition)"]
        COLLECT["Leader collects\nCOMPLETE_PARTITION\nfrom every partition"]
        FINAL["Append COMPLETED\n(or FAILED)\nto log stream"]
        COLLECT --> FINAL
    end

    USER --> CREATION
    CREATION --> INITIALIZATION
    INITIALIZATION --> EXECUTION
    EXECUTION -->|"COMPLETE_PARTITION\n(inter-partition)"| AGGREGATION
    AGGREGATION -->|"exported to\nES / OS / RDBMS"| USER
```

> **Detailed view** → [`batch-operation-dataflow.md`](batch-operation-dataflow.md)

