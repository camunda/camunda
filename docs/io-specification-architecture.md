# I/O Specification and Mapping Architecture

This document describes the architectural design for input/output specifications and
input/output mappings across the Camunda 8 platform — from process modeling through execution
to external invocation via the REST API, MCP gateway, and Connector Runtime.

> **Context:** This architecture underpins the extensible I/O contract model introduced to unify
> how process elements expose themselves as tools (MCP, Connectors, ad-hoc sub-process activities)
> while keeping the engine's existing variable-mapping semantics intact.

## Concepts

Four distinct concepts compose the full I/O contract for any process element:

| Concept | Where Defined | Role |
|---|---|---|
| **Input Specification** | BPMN / element template | External contract: what callers **must provide** to invoke the element |
| **Input Mapping** | BPMN `zeebe:ioMapping` (inputs) | Internal transformation: how provided inputs are shaped into local scope variables |
| **Output Specification** | BPMN / element template | External contract: what the element **produces** when it completes |
| **Output Mapping** | BPMN `zeebe:ioMapping` (outputs) | Internal transformation: how completion results are written back into process scope |

### Relationship between spec and mapping

The specification is the **contract**; the mapping is the **implementation**:

- The input specification tells external systems (callers, gateways, connectors) what variables to
  supply. The input mapping then optionally reshapes those variables before they are visible inside
  the element's local scope.
- The output specification tells external systems (job workers, connectors) what variables to
  deliver on completion. The output mapping then optionally reshapes those variables before they
  propagate into the parent process scope.

When no mapping is defined, variables flow through transparently as described by the specification.

### Special case: start events and intermediate catch events

Start events and catch events do not have input mappings because they have no execution behavior —
they simply wait to be triggered. The input specification still describes what the external trigger
must provide. Output mappings work as for any other element: they control how the trigger's payload
propagates into the process scope after the event completes.

---

## Core Architecture

```mermaid
graph TB
    subgraph Design["Design Time (Modeler)"]
        BPMN["BPMN XML\n(zeebe:ioMapping,\nzeebe:properties,\nelement templates)"]
        InputSpec["Input Specification\n(contract for callers)"]
        OutputSpec["Output Specification\n(contract for completers)"]
        InputMapping["Input Mapping\nFEEL expressions\n(zeebe:input)"]
        OutputMapping["Output Mapping\nFEEL expressions\n(zeebe:output)"]
    end

    subgraph Runtime["Runtime (Zeebe Engine)"]
        Deploy["Deployment &\nTransformation\n(FlowNodeTransformer)"]
        Activate["Element Activation\napplyInputMappings()"]
        Execute["Element Execution\n(job, event trigger,\nuser task, ...)"]
        Complete["Element Completion\napplyOutputMappings()"]
        VarScope["Variable Scope\n(local / process)"]
    end

    subgraph External["External Invocation Channels"]
        RestAPI["OC REST API\n(/v2/process-instances\n/v2/jobs/{key}/completion\n/v2/messages/correlation)"]
        gRPC["gRPC Gateway"]
        MCPGw["MCP Gateway\n(validates input spec,\ncreates process / activates)"]
        ConnRT["Connector Runtime\n(inbound: validates input spec;\noutbound: delivers per output spec)"]
        JobWorker["Job Worker / Application\n(fulfills output spec\non job completion)"]
    end

    BPMN --> InputSpec
    BPMN --> OutputSpec
    BPMN --> InputMapping
    BPMN --> OutputMapping

    InputSpec -.->|"validates / shapes\nincoming payload"| MCPGw
    InputSpec -.->|"validates incoming\nwebhook / trigger"| ConnRT
    OutputSpec -.->|"shapes job completion\npayload"| ConnRT
    OutputSpec -.->|"shapes job completion\npayload"| JobWorker

    InputSpec -->|"used as schema hint\nfor Modeler linting"| InputMapping
    OutputSpec -->|"used as schema hint\nfor Modeler linting"| OutputMapping

    BPMN -->|"deploy"| Deploy
    Deploy -->|"compiles mappings to\nFEEL expressions"| Activate
    Activate -->|"merges into local scope"| VarScope
    VarScope --> Execute
    Execute -->|"completion variables"| Complete
    Complete -->|"merges into parent scope"| VarScope

    RestAPI -->|"start / signal / correlate\nwith variables"| Activate
    gRPC -->|"start / complete job\nwith variables"| Activate
    MCPGw -->|"creates process instance\nor activates ad-hoc element"| RestAPI
    ConnRT -->|"triggers start/catch event\nper output spec"| RestAPI
    ConnRT -->|"completes job\nper output spec"| RestAPI
    JobWorker -->|"completes job\nwith variables"| RestAPI
```

---

## Element Lifecycle: I/O Flow

### Activities (service task, business rule task, user task, …)

```mermaid
sequenceDiagram
    participant Caller as External Caller<br/>(REST / MCP / Job Worker)
    participant Engine as Zeebe Engine
    participant Scope as Variable Scope

    Note over Caller,Engine: Invocation
    Caller->>Engine: Provide input variables<br/>(validated against Input Spec by caller)

    Note over Engine,Scope: Activation
    Engine->>Engine: Apply Input Mappings<br/>(zeebe:input rules → FEEL → local scope)
    Engine->>Scope: Merge result into element local scope

    Note over Engine,Scope: Execution
    Engine->>Caller: Create job / user task<br/>(with local scope variables as job payload)

    Note over Caller,Engine: Completion
    Caller->>Engine: Complete with output variables<br/>(must adhere to Output Spec)

    Note over Engine,Scope: Completion handling
    Engine->>Engine: Apply Output Mappings<br/>(zeebe:output rules → FEEL)
    Engine->>Scope: Merge result into parent/process scope
```

### Start Events and Intermediate Catch Events

```mermaid
sequenceDiagram
    participant Caller as External Trigger<br/>(REST / Connector Runtime / MCP)
    participant Engine as Zeebe Engine
    participant Scope as Variable Scope

    Note over Caller,Engine: Trigger
    Caller->>Engine: Provide trigger payload<br/>(validated against Input Spec by trigger channel)
    Note right of Engine: No Input Mappings — events<br/>have no execution behavior

    Note over Engine,Scope: Completion
    Engine->>Engine: Apply Output Mappings<br/>(zeebe:output rules → FEEL)
    Engine->>Scope: Merge result into process scope
```

---

## Invocation Channel Responsibilities

### OC REST API (and gRPC)

The REST API is the canonical invocation layer. It does not enforce input or output specifications
on its own; enforcement is the caller's responsibility. The API accepts variables as an opaque map
and passes them to the engine:

- `POST /v2/process-instances` → starts a process, passes `variables` to the root scope
- `POST /v2/jobs/{jobKey}/completion` → completes a job, passes `variables` as completion payload
- `POST /v2/messages/correlation` → correlates a message, passes `variables` as message payload
- `POST /v2/element-instances/ad-hoc-activities/{key}/activation` → activates ad-hoc elements
  with per-element `variables`

The input specification defines the expected shape of those variable maps.
The output specification defines the expected shape of the completion/trigger payload.

### MCP Gateway

The MCP gateway translates MCP tool calls into REST API calls. It can:

1. **Discover** process elements exposed as tools (processes via start events, ad-hoc sub-process
   activities, etc.) from the process definition and its input specification.
2. **Validate** the AI-provided arguments against the element's input specification before
   forwarding to the REST API.
3. **Reshape** tool arguments into the variable map the REST API expects.

The MCP gateway sits _between_ the AI agent and the REST API and is therefore the right place for
input validation and schema enforcement.

### Connector Runtime

The Connector Runtime manages both inbound and outbound connectors:

- **Inbound connectors** receive an external trigger (e.g. a webhook). The Connector Runtime
  validates the incoming payload against the element's input specification, then correlates or
  starts the process via the REST API, sending the correlated payload as the trigger variables.
  The output specification governs what those variables must look like when they reach the engine.
- **Outbound connectors** execute as job workers. They pick up jobs, perform their action, and
  complete the job with a result. The output specification describes the expected shape of that
  result. The Connector Runtime (or the connector itself) can enforce this before calling the
  completion API.

### Job Worker Applications

A general job worker application fulfills the output specification on job completion. The
specification can be surfaced to the job worker as a schema (e.g. via the element template or a
dedicated API endpoint) so the worker can validate its output before submitting.

---

## Use Cases

### Use Case 1: Inbound Connector (Webhook → Process Start)

An inbound connector exposes an HTTP webhook that starts a process whenever a request arrives.

```mermaid
flowchart LR
    subgraph Design["Design Time"]
        Template["Element Template\n(Inbound Webhook Connector)"]
        InputSpec["Input Spec\n(hardwired + hidden:\nwebhook payload schema)"]
        OutputSpec["Output Spec\n(hardwired + hidden:\ncorrelation variables)"]
        OutputMap["Output Mapping\n(user-defined: maps\noutput spec vars to\nprocess variables)"]
    end

    subgraph Runtime["Runtime"]
        Webhook["Incoming HTTP Request"]
        ConnRT["Connector Runtime\n(Inbound)"]
        API["REST API\nPOST /v2/process-instances\nor message correlation"]
        StartEvent["Start Event\n(or Catch Event)\nin Engine"]
        ProcScope["Process Variable Scope"]
    end

    Template --> InputSpec
    Template --> OutputSpec
    Template --> OutputMap

    Webhook -->|"1. receives payload"| ConnRT
    ConnRT -->|"2. validates payload\nagainst Input Spec"| ConnRT
    ConnRT -->|"3. triggers via REST\nwith correlation variables\n(per Output Spec)"| API
    API -->|"4. activates element"| StartEvent
    StartEvent -->|"5. applies Output Mappings\n(user-defined)"| ProcScope
```

**Key points:**
- The input specification and output specification are hardwired and hidden by the element template.
  Users cannot change them — the connector itself defines the contract.
- The user defines output mappings to control how the correlation variables (defined by the output
  spec) propagate into named process variables.
- There is no input mapping because start/catch events do not transform inputs — the input
  specification describes what the Connector Runtime itself validates and forwards.

### Use Case 2: Outbound Connector (Service Task)

An outbound connector executes as a job worker and calls an external service.

```mermaid
flowchart LR
    subgraph Design["Design Time"]
        Template["Element Template\n(Outbound Connector)"]
        InputSpec["Input Spec\n(user-defined: exposes task\nas a tool in ad-hoc sub-process)"]
        InputMap["Input Mapping\n(user-defined: maps\nprocess variables to\nconnector input)"]
        OutputSpec["Output Spec\n(hardwired + hidden:\nconnector result schema)"]
        OutputMap["Output Mapping\n(user-defined: maps\nresult variables to\nprocess variables)"]
    end

    subgraph Runtime["Runtime"]
        Engine["Zeebe Engine"]
        Job["Job\n(with local variables)"]
        ConnRT["Connector Runtime\n(Outbound)"]
        ExtService["External Service"]
    end

    Template --> InputSpec
    Template --> InputMap
    Template --> OutputSpec
    Template --> OutputMap

    Engine -->|"1. activates service task\napplies Input Mappings\n→ local scope"| Job
    Job -->|"2. polled by Connector Runtime"| ConnRT
    ConnRT -->|"3. calls external service\nwith local variables"| ExtService
    ExtService -->|"4. returns result"| ConnRT
    ConnRT -->|"5. completes job\nwith result variables\n(per Output Spec)"| Engine
    Engine -->|"6. applies Output Mappings\n→ process scope"| Engine
```

**Key points:**
- The output specification is hardwired and hidden: the connector always produces the same result
  shape, regardless of how it is used. The Connector Runtime enforces this on job completion.
- The user defines the input specification if the outbound connector should be available as a
  tool in an agentic ad-hoc sub-process. This spec describes what the AI agent must supply.
- The user defines input mappings to shape process variables into the connector's expected input
  (no need to know internal variable names like `toolCall`).
- The user defines output mappings to extract the connector's result into named process variables.

### Use Case 3: Agentic AI Ad-Hoc Sub-Process

An AI agent orchestrates a set of activities inside an ad-hoc sub-process. Each activity is a
"tool" the agent can invoke.

```mermaid
flowchart TD
    subgraph Design["Design Time"]
        direction LR
        AHSPDef["Ad-Hoc Sub-Process\nDefinition"]
        ToolA["Activity A (Service Task)\nInput Spec: {param1, param2}\nOutput Spec: {result}"]
        ToolB["Outbound Connector B\nInput Spec: {url}\nOutput Spec: hardwired"]
        ToolC["Partial Sub-Process C\nInput Spec: {context}\nOutput Spec: {summary}"]
        AHSPDef --> ToolA
        AHSPDef --> ToolB
        AHSPDef --> ToolC
    end

    subgraph AgentRuntime["Agent Runtime"]
        AIAgent["AI Agent\n(LLM + tool use)"]
        AgentConn["Agent Connector\n(Ad-Hoc Sub-Process\nJob Worker)"]
    end

    subgraph Engine["Zeebe Engine"]
        AHSP["Ad-Hoc Sub-Process\nInstance"]
        ActA["Activity A Instance"]
        ActB["Connector B Instance"]
        ActC["Sub-Process C Instance"]
    end

    AIAgent -->|"1. picks tools from\nInput Specs"| AgentConn
    AgentConn -->|"2. activates elements\nwith input variables\n(per Input Spec)"| AHSP
    AHSP --> ActA
    AHSP --> ActB
    AHSP --> ActC
    ActA -->|"3. job completed\nper Output Spec"| AgentConn
    ActB -->|"3. job completed\nper Output Spec"| AgentConn
    ActC -->|"3. sub-process completed\nper Output Spec"| AgentConn
    AgentConn -->|"4. result available\nto agent"| AIAgent
```

**Detailed element activation flow:**

```mermaid
sequenceDiagram
    participant AI as AI Agent
    participant AgentConn as Agent Connector
    participant API as REST API
    participant Engine as Zeebe Engine
    participant Worker as Job Worker<br/>(tool implementation)

    AI->>AgentConn: Choose tool "Activity A"\nwith {param1: "x", param2: 42}

    Note over AgentConn: Validates args against\nActivity A's Input Spec

    AgentConn->>API: POST /v2/element-instances/ad-hoc-activities/{key}/activation\n{elementId: "ActivityA", variables: {param1, param2}}
    API->>Engine: Activate element

    Note over Engine: Apply Input Mappings\n(user-defined, reference param1/param2\nfrom Input Spec directly)
    Engine->>Engine: Merge mapped vars into local scope
    Engine->>Worker: Create job (with local variables)

    Worker->>Worker: Execute
    Worker->>API: POST /v2/jobs/{key}/completion\n{variables: {result: "done"}} (per Output Spec)
    API->>Engine: Complete job

    Note over Engine: Apply Output Mappings\n(user-defined, based on Output Spec)
    Engine->>Engine: Merge result into ad-hoc scope

    Engine->>AgentConn: Activity completed with result
    AgentConn->>AI: Tool result: {result: "done"}
```

**Key points:**
- Elements without incoming flow are "tools" the agent can invoke. Their input specification is
  the tool schema — the agent must provide the described parameters.
- Input mappings reference the input specification parameters directly (no hidden `toolCall`
  variable needed). If no mapping is defined, the input spec variables are passed through as-is.
- Elements without outgoing flow produce results. Their output specification describes what they
  return. Output mappings shape those results back into the ad-hoc sub-process scope.
- The agent connector validates the AI-provided arguments against the input specification before
  activating elements via the REST API — analogous to how the MCP gateway validates inputs for
  process-as-tool invocations.

### Use Case 4: Process as a Tool via MCP Gateway

A complete process is exposed as an MCP tool. An AI agent starts it with specific inputs and
waits for it to complete.

```mermaid
sequenceDiagram
    participant AI as AI Agent (MCP Client)
    participant MCP as MCP Gateway
    participant API as REST API
    participant Engine as Zeebe Engine

    Note over MCP: Discovers processes with\nInput Specs on their start events

    AI->>MCP: Call tool "OrderProcess"\nwith {orderId: "123", amount: 99.50}

    Note over MCP: Validates args against\nstart event Input Spec

    MCP->>API: POST /v2/process-instances\n{processDefinitionId: "OrderProcess",\nvariables: {orderId, amount},\nawaitCompletion: true}
    API->>Engine: Start process instance

    Note over Engine: Start event: no input mappings.\nVariables placed in process scope.\nOutput mappings applied on start event completion.

    Engine->>Engine: Execute process...
    Engine->>API: Process completed\nwith result variables (per Output Spec)
    API->>MCP: Return result variables
    MCP->>AI: Tool result: {status: "shipped", trackingId: "T42"}
```

**Key points:**
- The start event's input specification defines the tool's input schema as seen by the MCP gateway.
- The start event has no input mappings (by design — catch/start events skip input mappings).
- The process's output specification (on the end event or process level) defines what the MCP
  gateway returns as the tool result.
- The MCP gateway handles `awaitCompletion` semantics transparently.

---

## Where Each Concept Lives in Code

| Concept | BPMN Representation | Engine Model | Runtime Application |
|---|---|---|---|
| Input Mapping | `zeebe:ioMapping / zeebe:input` | `ExecutableFlowNode.inputMappings` (FEEL `Expression`) | `BpmnVariableMappingBehavior.applyInputMappings()` on element ACTIVATING |
| Output Mapping | `zeebe:ioMapping / zeebe:output` | `ExecutableFlowNode.outputMappings` (FEEL `Expression`) | `BpmnVariableMappingBehavior.applyOutputMappings()` on element COMPLETING |
| Input Specification | `zeebe:properties` / element template | (stored in BPMN; future: exposed via process definition API) | Validated by MCP gateway / Connector Runtime / Agent Connector before API call |
| Output Specification | `zeebe:properties` / element template | (stored in BPMN; future: exposed via process definition API) | Validated by Connector Runtime / Job Worker before job completion |

**Key source files:**
- `zeebe/bpmn-model/.../zeebe/ZeebeIoMapping.java` — BPMN model for `zeebe:ioMapping`
- `zeebe/engine/.../transformer/FlowNodeTransformer.java` — compiles mappings to FEEL expressions
- `zeebe/engine/.../transformer/VariableMappingTransformer.java` — FEEL expression builder for mappings
- `zeebe/engine/.../behavior/BpmnVariableMappingBehavior.java` — runtime application of mappings
- `gateways/gateway-mcp/.../tool/process/instance/ProcessInstanceTools.java` — MCP tool: create process instance
- `gateways/gateway-mcp/.../tool/process/definition/ProcessDefinitionTools.java` — MCP tool: discover processes
- `zeebe/gateway-rest/.../controller/AdHocSubProcessActivityController.java` — REST: activate ad-hoc elements

---

## Summary: The I/O Contract Lifecycle

```mermaid
flowchart LR
    A["1. Model\nDefine Input Spec,\nOutput Spec,\nInput Mappings,\nOutput Mappings\nin BPMN/Modeler"] --> B

    B["2. Deploy\nEngine compiles\nmappings to\nFEEL expressions"] --> C

    C["3. Invoke\nCaller validates\nand shapes input\nper Input Spec\n(MCP, Connector, Worker)"] --> D

    D["4. Activate\nEngine applies\nInput Mappings\n→ local scope"] --> E

    E["5. Execute\nElement runs\n(job, event, task, ...)"] --> F

    F["6. Complete\nCompleter delivers\noutput per Output Spec\n(Connector, Worker, event)"] --> G

    G["7. Propagate\nEngine applies\nOutput Mappings\n→ process scope"]
```
