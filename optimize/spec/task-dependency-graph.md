<!--
  Task dependency graph — Agentic Control Plane feature
  Render with any Mermaid-aware viewer (GitHub, IntelliJ, VS Code + Mermaid plugin).
-->

```mermaid
flowchart TD
    subgraph L1["Layer 1 — Import Pipeline"]
        direction LR
        T1["T1\nProcessInstanceIndex\nbump + field defs"]
        T3["T3\nPainless upsert\nscript"]
        T2["T2\nImport service\n(AgentInstanceRecord)"]
        T1 --> T3 --> T2
    end

    subgraph L2["Layer 2 — Backend API"]
        direction LR
        T4["T4\nContracts &\nshared types"]
        T5["T5\nShared query\nutilities"]
        T6["T6\nGET /summary"]
        T7["T7\nGET /process-breakdown"]
        T8["T8\nGET /trends"]
        T9["T9\nGET /charts"]
        T10["T10\nExtend GET\n/process-definition"]
        T4 --> T5
        T5 --> T6 & T7 & T8 & T9 & T10
    end

    subgraph L3["Layer 3 — Frontend"]
        FAPI["F — API client\n& TS types"]
        FFilter["F — Filter context\ndate range + process selector"]
        FKPI["F — KPI cards\n(runs / duration / incident)"]
        FTokenSt["F — Token stat KPIs\n(avg + median)"]
        FDur["F — Duration stats\nP50/P95 + chart"]
        FTrend["F — Token trend\n+ outlier bands"]
        FTopCons["F — Top token\nconsumers chart"]
        FCharts["F — Charts\n(tool freq + avg tokens + incident rate)"]
        FDash["F — Dashboard layout\nrouting + L0↔L1 switching"]
        FTests["F — Tests\n(i18n + unit + E2E)"]
    end

    subgraph IT["Integration & Validation"]
        ITSmoke["IT — E2E smoke test\n(full pipeline)"]
        ITPerf["IT — Perf validation\n(nested aggs at scale)"]
    end

    %% Layer 1 → Layer 2
    T1 --> T5
    T2 --> T6 & T7 & T8 & T9

    %% Layer 2 → Frontend
    T4  --> FAPI
    T10 --> FFilter
    T10 --> FTopCons
    T6  --> FKPI & FTokenSt & FDur
    T8  --> FDur & FTrend
    T7  --> FTopCons
    T9  --> FCharts
    FAPI --> FKPI & FTokenSt & FDur & FTrend & FTopCons & FCharts

    %% Frontend assembly
    FFilter & FKPI & FTokenSt & FDur & FTrend & FTopCons & FCharts --> FDash
    FDash --> FTests

    %% Integration
    T9    --> ITPerf
    FTests --> ITSmoke
    T10   --> ITSmoke

    %% Style
    classDef done fill:#2d6a2d,color:#fff,stroke:#1a4a1a
    classDef inprogress fill:#7a5c00,color:#fff,stroke:#4a3800
    classDef blocked fill:#6b1a1a,color:#fff,stroke:#3d0000
    classDef default fill:#1e3a5f,color:#fff,stroke:#0d2240

    class T1,T2,T3,T4,T5 done
    class T6,T7,T8,T9,T10 inprogress
    class FAPI,FFilter,FKPI,FTokenSt,FDur,FTrend,FTopCons,FCharts,FDash,FTests blocked
    class ITSmoke,ITPerf blocked
```

## Legend

| Color  | Meaning                        |
|--------|--------------------------------|
| Green  | Done (Tasks 1–5)               |
| Amber  | Next — blocked on nothing      |
| Red    | Blocked — upstream not done    |

## Critical path

```
T1 → T3 → T2 → T6/T7/T8/T9 → FE charts → FDash → FTests → IT-Smoke
```

Parallel fast track (no L1 dependency):

```
T4 → T5 → T10 → FFilter
```

## Parallelism opportunities

| Can run in parallel            | Shared blocker            |
|-------------------------------|---------------------------|
| T6, T7, T8, T9               | Both need T2 + T5 done    |
| T10, T6–T9                   | All need T5               |
| FKPI, FTokenSt, FDur, FTrend  | All need T6 + FAPI        |
| FCharts (×3 components)       | All need T9 + FAPI        |
| ITPerf (starts at T9 merge)   | Does not need FE          |
