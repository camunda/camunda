# Order-to-cash OCPM showcase

Three Zeebe processes plus driver wiring (`analytics/run-realistic-load.sh ocpm`) that exercise
object-centric process mining (OCPM) mechanics end to end: scope-precise variable sightings,
multi-hop call-activity instance links, and message-correlation convergence.

This document is the **validation checklist**: it states, per mechanism, what a later live run
against the analytics pipeline should be able to reconstruct. Numbers are exact and derived
purely from the deterministic FEEL expressions in `ocpm/order-intake.bpmn`'s start event (see
"Payload generation" below), so a live run can assert against them directly.

## Processes

| Process              | BPMN file                    | Role                                                        |
|----------------------|-------------------------------|--------------------------------------------------------------|
| `order-intake`       | `ocpm/order-intake.bpmn`      | Entry point. Registers items, runs the credit check, calls fulfillment. |
| `order-fulfillment`  | `ocpm/order-fulfillment.bpmn` | Picks/packs stock, publishes `ship-order`, calls invoicing. |
| `order-invoicing`    | `ocpm/order-invoicing.bpmn`   | Trivial leaf: one "Create invoice" task.                     |
| `region-shipping`    | `ocpm/region-shipping.bpmn`   | One long-running instance per region; converges many orders via message correlation. |

## Object model

```
customer (customerId)  --1:N-->  order (orderId)  --1:N-->  item (itemId)
order   --N:1-->  region-shipping batch (region)   [via ship-order message correlation]
```

- **order**: root object of `order-intake`. Identified by `orderId`. Carries `customerId`,
  `amount`, `region`. Propagated by value into `order-fulfillment` and `order-invoicing` via
  call-activity variable propagation (no `zeebe:ioMapping` restriction on either call activity, so
  the Zeebe default — propagate everything both ways — applies).
- **item**: sub-object of order. One per entry of the `items` array, materialized inside the
  "Register items" multi-instance body.
- **customer**: referenced by `customerId`, not separately modeled as a process (out of scope for
  this showcase; a real deployment would look this up from a system of record).
- **region-shipping batch**: a long-running convergence object, one per region, that many orders'
  fulfillment instances correlate into via the `ship-order` message.

`seq` is **not** part of the object model — it is the load-tester Starter's own auto-incrementing
per-instance counter (renamed from `businessKey`, see "Payload generation"), kept around only to
deterministically derive every other field. Ignore it in ground-truth assertions.

## Mechanism -> modeling choice -> ground truth

### 1. MI-inner itemId -> scope-precise item sightings

`order-intake.bpmn`'s "Register items" embedded subprocess has multi-instance loop characteristics
(`inputCollection="=items" inputElement="item"`). Inside it, a script task
(`zeebe:script expression="=item.itemId" resultVariable="itemId"`) writes `itemId` as an *output*
of that script task. In Zeebe, a task's output mapping lands in the flow scope of the task — for a
task inside a multi-instance body, that scope is **the per-iteration MI body instance**, not the
process root. This is the load-bearing bit: it is what makes `itemId` a scope-precise child
sighting instead of one array blob sitting at root.

**Ground truth**: an order with *k* items yields exactly *k* `itemId` VARIABLE records, each at a
non-root scope (one MI body instance per array element) — never at the `order-intake` root scope.
It also yields exactly *k* `order ⊃ item` relations (each item scope is nested directly under the
`order-intake` process instance).

`k` is `modulo(seq, 4) + 1`, cycling 1, 2, 3, 4, 1, 2, 3, 4, ... — so across a large sample, item
counts are uniformly distributed over {1, 2, 3, 4} (25% each).

### 2. Call-activity chain -> 2 instance links, 3 order sightings

`order-intake` calls `order-fulfillment` calls `order-invoicing` — a two-deep chain, three process
instances per order. Neither call activity restricts variables with `zeebe:ioMapping`, so Zeebe's
default (propagate all variables down on start, all top-level variables back up on completion)
applies: `orderId`/`customerId`/`amount`/`region`/`items` all ride the whole chain.

**Ground truth per order**:
- 2 call-activity instance links: `order-intake -> order-fulfillment`, `order-fulfillment ->
  order-invoicing`.
- 3 instances touched: intake, fulfillment, invoicing.
- 3 `orderId` sightings at ROOT scope — one per instance, because Zeebe materializes propagated
  parent variables as new variable records in the child's own scope at instance creation, not a
  reference to the parent's. (The intake sighting is the original `zeebe:output` on the start
  event; the fulfillment and invoicing sightings are each call activity's variable propagation.)

### 3. amount > 4000 -> planted slow variant (bottleneck/lift story)

The "Credit check" exclusive gateway in `order-intake` routes `amount > 4000` orders (~20%, see
below) to "Manual credit review" (long-running: the driver launches this job's worker with a
multi-second completion delay) and everyone else to "Automatic credit check" (fast, ~200 ms).

**Ground truth**: exactly two variants of `order-intake` exist, split ~80/20, and the 20% variant
containing "Manual credit review" has materially higher cycle time. A bottleneck/lift analysis
keyed on the `amount` attribute should recover the >4000 threshold as the explanation — this is
the planted variant/lift/explain story.

`modulo(seq, 5) = 0` selects the slow variant deterministically (exactly 1-in-5 = 20%). Amounts:
non-high orders land in `[50, 3000)`, high orders in `[4500, 7500)` — see "Payload generation".

### 4. Message correlation -> convergence + the payload gap

`order-fulfillment`'s "Publish ship order" job worker publishes message `ship-order` keyed by
`region`. `region-shipping` has one long-running instance per region, looping on an intermediate
catch event subscribed to `ship-order` with `correlationKey="=region"`. Every order's fulfillment
run correlates into the *same* region-shipping instance for its region — a genuine N:1
convergence, the shape OCPM calls out as distinct from the tree-shaped call-activity links above.

**Ground truth, if the message carried a payload** (see gap below): every catch event would create
a new `orderId` VARIABLE record on the shared region-shipping instance (last-write-wins on the
*visible* value is fine for the demo — each write is still its own record, so sightings capture
every value even though only the latest is visible via a point-in-time read of the instance).

**KNOWN GAP — not currently produced by the live driver.** `io.camunda.zeebe.worker.Worker`
(load-tester, out of this suite's territory to modify) publishes messages via
`newPublishMessageCommand().messageName(...).correlationKey(...).send()` — it never calls
`.variables(...)`. So `ship-order` carries **no payload** today, and the catch event contributes
**zero** additional `orderId` sightings on the region-shipping instance. What the live driver *does*
prove: the correlation/convergence mechanic itself (N fulfillment instances -> 1 region-shipping
instance, keyed correctly by region) and the MESSAGE-qualifier consumption record per catch. See
`ocpm/order-fulfillment.bpmn` and `ocpm/region-shipping.bpmn` for the documented reasoning, and the
report for this task for the two load-tester APIs that were checked (Worker's message publish:
correlationKey-only; Starter's message-start publish: payload but random correlationKey and
process-start-only — neither combination supports "specific correlationKey + payload" without a
small, out-of-scope change to `Worker.java`, e.g. a `messagePayloadPath` sibling to the existing
`payloadPath` property).

## Payload generation

The load-tester's `Starter` reads one static payload file **once** and reuses it for every
instance unchanged, except for a single per-instance value: an auto-incrementing counter it always
injects (`businessKey` by default, renamed here to `seq` via `LOAD_TESTER_STARTER_BUSINESS_KEY`).
That's the *only* built-in source of per-instance variation.

Rather than inventing a payload-pool file (a viable fallback the task allowed, but unnecessary
here), every other field is FEEL-derived from `seq` at the `order-intake` start event:

| Field        | Expression (abbreviated)                                                   | Range / cardinality |
|--------------|-------------------------------------------------------------------------------|----------------------|
| `orderId`    | `"ORD-" + string(seq)`                                                       | unique per instance  |
| `customerId` | `"CUST-" + string(modulo(seq, 50) + 1)`                                      | 50 distinct values, cycling |
| `region`     | `["EU","US","APAC","LATAM"][modulo(seq, 4) + 1]`                             | 4 values, 25% each   |
| `amount`     | `if modulo(seq,5)=0 then 4500+modulo(seq*877,3000) else 50+modulo(seq*1327,2950)` | 80% in [50,3000), 20% in [4500,7500) |
| `items`      | `for i in 1..(modulo(seq,4)+1) return {itemId:"ITM-"+string(seq)+"-"+string(i), name:"Item "+string(i), qty:modulo(seq+i,4)+1}` | 1-4 elements, 25% each count |

All five expressions were evaluated (not just parsed) against representative `seq` values with the
FEEL engine used by this repo (`org.camunda.feel:feel-engine:1.21.0`, via
`io.camunda.zeebe.el.ExpressionLanguageFactory`'s underlying engine) — see the report for this
task for the harness and sample output. The `order-intake-seed.json` starter payload is
intentionally `{}`: nothing beyond `seq` is needed.

`region-shipping` is started once per region by four dedicated starters, each with a trivial
payload file (`region-shipping-<region>.json`, e.g. `{"region": "EU"}`).

## Approximations (not gaps, just load-tester config limits)

- **Job completion delay is a fixed duration per worker process, not a randomized range.** The
  load-tester's `Worker` reads one `completionDelay` `Duration` and sleeps exactly that long every
  time — no jitter support. "Manual credit review" (spec: 3-8s) runs with a fixed 5s delay;
  "Automatic credit check" (spec: 100-300ms) runs with a fixed 200ms delay; "Pick stock"/"Pack
  order" (spec: a few hundred ms with jitter) run with a fixed 300ms delay. The variant/duration
  *lift* story is preserved (long vs. short branch); only the within-branch jitter is lost.
- **`region-shipping` "restart on complete" is approximated as a slow trickle rate**, since the
  Starter schedules new instances on a fixed timer, not on completion of the previous one. See the
  driver script for the configured interval.
