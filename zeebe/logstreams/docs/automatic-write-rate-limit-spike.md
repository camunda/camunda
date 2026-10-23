# Spike: automatic write rate limit

Status: spike. Date: 2026-10-23.

This spike tests the solution direction [automatic write rate limit](https://github.com/camunda/pdp-initiative-pc-physical-tenants-at-scale/blob/767cb9283abee8a513861a316868d9384b8e5d02/noisy-neighbour/solutions/automatic-write-rate-limit.md) in a simulation. The broker does not use the code.

## Goal

The write rate limit gives each tenant a share of the broker in records each second, in proportion to its weight. When the broker has not enough capacity for all tenants, each tenant gets the lower of two values:

- the rate that it sends
- its weighted share of the capacity

A tenant that sends more than its share must not take the share of other tenants. The spike measures each scenario against this goal.

## Two mechanisms with two different functions

The broker already has request flow control. The spike modelled it to find out if the two mechanisms do the same work. They do not.

| | Request limiter (flow control) | Automatic write rate limit |
| --- | --- | --- |
| Controls | The queue depth of one partition | The throughput of each tenant |
| Depends on | The requests: their rate and their latency | The capacity of the broker |
| Signal | Latency of each request | CPU throttling of the broker |
| Weights | No | Yes |
| Scope | One partition, no relation to the other tenants | All leader partitions of the broker |

The request limiter keeps the backlog of each partition small. It does not divide the capacity between the tenants. The results below show this. Without the write rate limit, a tenant that sends too much gets more than its share. The weights then have no effect.

## Code

| Class | Location | Function |
| --- | --- | --- |
| `AutomaticWriteRateLimitCfg` | `src/main/.../flowcontrol/automatic` | The parameters and their start values. |
| `WriteRateLimitTopology` | `src/main/.../flowcontrol/automatic` | The tenants, partitions, replication factors and weights of the cluster. |
| `WriteRateLimitBounds` | `src/main/.../flowcontrol/automatic` | The floor, the start value and the limit of a tenant (`ceiling × w_t / w̄`). |
| `AutomaticWriteRateController` | `src/main/.../flowcontrol/automatic` | The AIMD controller of one broker. |
| `WriteRateLimitSimulation` | `src/test/.../flowcontrol/automatic` | A simulation of a cluster in time steps of 100 ms. |
| `AutomaticWriteRateLimitSimulationTest` | `src/test/.../flowcontrol/automatic` | The scenarios. Each test prints a table with one row for each window. |

To run the scenarios:

```bash
./gradlew :zeebe-logstreams:test --tests '*automatic*'
```

## Controller

Each broker has one controller and one ceiling. The ceiling stays between the floor and the start value. In v1, the start value is also the maximum.

| Rule | Condition | Action |
| --- | --- | --- |
| CPU guard | More than 10% of the CFS periods are throttled, for 2 windows in sequence | Multiply the ceiling by 0.9 |
| No data | All leader partitions with writes have an active exporter throttle | Keep the ceiling, block increases |
| Increase | The window is not throttled, and a partition rejects a write at its limit | Add 5% of the start value, a maximum of one step in each window |

CPU throttling is the only signal that decreases the ceiling. Finding 2 gives the signals that the spike tested and the reasons to remove them.

## Model of the simulation

- Each broker has a CPU limit. Each replica uses a maximum of one core.
- One record costs `LEADER / R_true × cost` CPU seconds on a leader and `FOLLOWER / R_true × cost` on a follower. `R_true` is the real capacity of the hardware. The controller does not know it.
- When the CPU demand of a broker is more than its limit, each replica gets a share in proportion to its demand. The time step then counts as throttled.
- The partitions of a tenant get their replicas and priorities as in `RoundRobinPartitionDistributor` and `PartitionPriorityAssigner`. The leader is the replica with the highest priority on a broker that runs.
- Each leader has a request limiter with the default configuration of the broker: `StabilizingAIMDLimit` in a `WindowedLimit`. The expected RTT is 200 ms, the start limit is 100, and the backoff ratio is 0.9.
- The request limiter applies before the write rate limit, as in `FlowControl`. The latency of a request is 5 ms plus the backlog divided by the processing rate.
- The leader accepts `min(admitted, limit × step)` records. When the exporter backlog is more than 10 000 records, the exporter throttle sets the limit to the export rate. The partition is then excluded.
- A broker that stops loses its queues. After a restart, the broker starts at the start value.

### Limits of the model

1. One record is one command. Real commands write follow-up records. So a real backlog is larger than the request limit, and a real latency is higher.
2. A tenant that "floods" sends 1 000 000 records each second for each partition. A real client has a limited number of requests in flight. So the simulated flood hits the request limiter harder than a real client.
3. A throttled broker gives CPU in proportion to demand. Real CFS gives a more equal share to each thread that can run. Real CFS can protect a small tenant better than the simulation shows.

## Results

Setup, if not stated otherwise: 3 brokers with 2 CPUs, 3 tenants with 3 partitions each, replication factor 3, `R` = 10 000. The values are records each second for each tenant, as a mean over the last 2 minutes of a 5-minute run.

| Scenario | Expected | Both mechanisms | Request limiter only |
| --- | --- | --- | --- |
| All tenants flood, `R_true` = `R` | 8 333 each, no throttling | 8 333 each, CPU throttled 0% | 8 550 each, CPU throttled 86% |
| Tenant a floods, b and c send 7 500 | a 8 333, b and c 7 500 | a 8 333, b and c 7 500, CPU throttled 0% | a 12 664, b and c 7 284, CPU throttled 98% |
| Weights 2:1:1, all tenants flood, `R_true` = 8 000 | 10 909, 5 454, 5 454 | 10 451, 5 413, 5 413, CPU throttled 44% | 7 027 each, the weights have no effect |
| Tenant a floods, b and c send 7 500, `R_true` = 8 000 | 7 273 each | 7 110, 7 098, 7 098, CPU throttled 52% | a 8 529, b and c 6 650 |
| All tenants flood, `R_true` = 8 000 | 7 273 each | 7 015, 7 017, 7 017, CPU throttled 36% | 7 027 each |

When `R_true` is 8 000, the broker can do `2 × 8 000 / 2.2 = 7 273` records each second for each partition, over all tenants. The factor 2.2 is one leader and two followers (`1 + 2 × 0.6`). So the fair share is 7 273 records each second for each tenant. All tenants want more than this value, so all tenants have the same share.

Other results:

- With one tenant, the mechanism is off.
- When the CPU is sufficient and one or two tenants have expensive records, the CPU does not throttle. The ceiling does not change, and the other tenants keep their full limit.
- When the exporter of one tenant is slow, the ceiling does not change.
- When all exporters stop, the ceiling does not change.
- When the operator adds a tenant, the limits of the other tenants decrease.
- With 6 partitions for each tenant on 3 brokers, the start value holds during a rolling restart.
- When `R_true` goes back to `R`, the ceiling goes back to the start value on demand.

## Known gaps

1. **Expensive records.** The limit counts records, not CPU. A tenant with expensive records causes CPU throttling. The CPU guard then decreases the ceiling of all tenants. The solution direction gives this gap in section 5. The spike keeps records each second as the unit.
2. **Bottleneck that is not CPU.** The controller sees only CPU throttling. When the disk or the network is the bottleneck, the ceiling does not decrease. The request limiter then protects the queues, but without weights. A solution needs a capacity signal for that resource, not a backlog signal.
3. **Not work-conserving.** See Finding 4.
4. **AIMD probe.** When `R` is too high, the ceiling oscillates around the sustainable value. Each increase step causes some CPU throttling, between 36% and 52% of the windows in the results. The tenants get 96% to 98% of their fair share.
5. **Unbalanced failover.** See Finding 1.
6. **Floor.** See Finding 3.

## Finding 1: the start value does not hold when the failover is not balanced

### Observation

Setup: 3 brokers, 3 tenants, 3 partitions for each tenant, replication factor 3.

- `PartitionPriorityAssigner` changes the order of the secondary priorities after each round of partitions. One round has one partition for each broker.
- With 3 partitions on 3 brokers, each tenant has only one round. The second priority is always the next broker in the ring.
- Each tenant starts its distribution at broker 0. So all tenants have the same pattern.
- When broker 0 stops, broker 1 gets all the leaders of broker 0. Broker 1 then leads 6 of its 9 replicas (`λ = 2/3`). The start value uses `λ = 1/2`.
- The CPU of broker 1 throttles. Its controller decreases its ceiling to the floor. Broker 2 does not change.
- After the restart, the leadership goes back. The ceiling of broker 1 goes back to the start value on demand.
- With 6 partitions for each tenant, the leaders of a stopped broker go to the two other brokers in equal parts. The start value then holds.

A related effect: when a tenant has fewer partitions than brokers, the leaders are not balanced, also when all brokers run. For example, with 2 partitions on 3 brokers, broker 2 is never a leader.

Test: `shouldLowerCeilingOfTakeoverBrokerDuringRollingRestartWhenFailoverIsUnbalanced`. The test records the gap. It does not record the target behaviour.

### Possible solutions

1. Calculate `λ` for the start value from the worst failover. The worst failover is that one broker gets all the leaders of the stopped broker. This gives a lower start value for all topologies.
2. Calculate `λ` from the real priorities of the topology. The start value is then correct for each topology. The calculation needs the partition distribution, not only the count of partitions.
3. Change `PartitionPriorityAssigner` so that the secondary priorities alternate between partitions, also inside one round. Or let each tenant start its distribution at a different broker. This changes the cluster, not the controller.
4. Keep the formula. Write the condition in the documentation: the count of partitions of each tenant is a multiple of `2 × brokers`.

## Finding 2: CPU throttling is the only decrease signal

The spike tested four sets of signals. The first three failed. This section gives the sequence.

### Step 1: the size of the backlog

The first version used the size of the backlog, without a request limiter in the model.

- A partition counted as backlogged when its backlog was more than 1 000 records and more than in the last window.
- The CPU guard decreased the ceiling in each throttled window. While the backlog drained, the CPU stayed at 100%. The guard then decreased the ceiling again, to the floor.
- With `R_true` = 8 000, the backlog increased to approximately 49 000 records. The CPU throttled in approximately 50% of the windows.

A throttled window is not always a problem. While the backlog drains, the CPU is busy, but the ceiling is already sustainable.

### Step 2: the derivative of the backlog

The second version used the change of the backlog over the window, in records each second.

| Rule | Condition |
| --- | --- |
| Backlogged partition | The backlog increases faster than 100 records each second |
| CPU guard | Throttled for 2 windows, and the total backlog of the leaders increases |
| Backlog rule | At least 2 tenants, and at least half of the active tenants, are backlogged. The total backlog increases. |
| Increase | No decrease condition, and the total backlog of the leaders does not increase |

Without a request limiter, this version worked. The backlog stayed below 10 000 records and became empty after each decrease.

### Step 3: the request limiter makes the backlog signals useless

The spike then added the request limiter to the model. The request limiter keeps the backlog at approximately its in-flight limit. So the backlog does not grow, and its derivative stays near zero.

- With `R_true` = 8 000, the first decrease came after 120 seconds, not after 20 seconds. Before that, the CPU throttled in approximately 80% of the windows.
- The backlog rule almost never fired. When it fired, two tenants had expensive records and the CPU was sufficient. The rule then decreased the limit of a third tenant that had no problem.

The spike also measured the latency of each partition, to use it as a threshold for the backlog rule:

| Scenario | CPU throttled | Mean latency | Maximum latency | Samples at 100 ms or more |
| --- | --- | --- | --- | --- |
| All tenants at their limit | 0% | 5 ms | 5 ms | 0% |
| `R_true` = 8 000, first 90 seconds | 75% to 89% | 27 to 43 ms | 49 to 86 ms | 0% |
| `R_true` = 8 000, after 210 seconds | 100% | 80 to 82 ms | 105 to 109 ms | 10% to 19% |
| One tenant with records of cost 3 | 67% to 100% | 44 to 76 ms | 98 to 160 ms | 1% to 26% |

The latency follows the current limit of the request limiter, not its expected RTT. The latency is approximately the limit divided by the processing rate. `StabilizingAIMDLimit` adds 1 to the limit in each update, approximately once each second. The latency gets near 200 ms only after 6 to 7 minutes of overload. So a latency threshold reacts late, and its value depends on the throughput of the partition.

### Step 4: rejections of the request limiter

The spike then tested the rejections of the request limiter (`RequestLimitExhausted`) as the decrease signal, without the CPU guard. A decrease happened when at least 2 tenants, and at least half of the active tenants, had the signal.

- Mode 1: the partition had a rejection in the window.
- Mode 2: the partition had a rejection in the window, and its tenant was below its write rate limit.

| Scenario | Expected | Mode 1 | Mode 2 |
| --- | --- | --- | --- |
| All tenants flood, `R_true` = `R` | 8 333 each | 6 667 each. The ceiling goes to the floor while the CPU is idle. | 8 333 each |
| Weights 2:1:1, all tenants flood, `R_true` = 8 000 | 2:1:1, CPU not throttled | 2:1:1 at the floor. 20% of the capacity is not used. | 9 326, 6 250, 6 250. No decrease, CPU throttled 100%. |
| Tenant a floods, b and c send 7 500, `R_true` = 8 000 | 7 273 each | 6 750 each | 7 157, 7 163, 7 163 |
| Two tenants with expensive records, 8 CPUs | Tenant b keeps 30 000 | Tenant b gets 26 667 | Tenant b keeps 30 000 |

A rejection depends on the requests of the client, not only on the capacity of the broker:

- In mode 1, a client that sends faster than the request limiter allows gets rejections also when the CPU is idle. This is a false signal.
- In mode 2, a tenant that floods is always at its write rate limit, so its rejections do not count. When all tenants flood, the controller sees nothing.

### Decision

The controller uses only CPU throttling, without the condition on the backlog. This version gives the results in the section "Results".

| Rule | Status |
| --- | --- |
| CPU guard | Kept. The condition "the backlog increases" is removed. |
| Backlog rule | Removed. The request limiter controls the queue depth. |
| Derivative of the backlog | Removed. |
| Rejections of the request limiter | Not used. |

The request limiter also solves the problem of step 1. The backlog stays small, so the CPU drains it in less than one window after a decrease.

### Open points

1. The amplitude of the oscillation depends on the increase step (5% of the start value) and the decrease factor (0.9). Smaller values give a smaller amplitude and a slower reaction. The load test must find the values.
2. The decrease factor can depend on the overload. A decrease to exactly the sustainable value gives no free CPU to drain the backlog. The factor must keep a margin.
3. The controller does not see the followers. CPU throttling also delays the followers. The replay lag of the followers can be an additional signal.

## Finding 3: the floor does not protect the tenants when `R` is much too high

The controller can correct an `R` that is too high only down to the floor. In the default setup, the floor is 80% of the start value. With all brokers up, the sustainable ceiling is `R_true × 2 / (9 × 0.733)`. This value is equal to the floor when `R_true` is 7 333. So the controller can correct an `R` that is a maximum of approximately 36% too high.

With `R_true` = 6 000, the sustainable ceiling is approximately 1 818. The floor is 2 222. The ceiling stays at the floor and the CPU throttles all the time. The floor is a promise only when `R` is approximately correct. The load test must measure `R` with a margin.

## Finding 4: the limit is not work-conserving

A mechanism is work-conserving when it never leaves capacity idle while a tenant wants more. In v1, the ceiling cannot go above the start value. So the maximum of each tenant does not change.

Example: tenant a floods, b and c send 7 500 records each second, and `R_true` = `R`. The CPU is not throttled. Tenant a stays at 8 333 records each second, but the broker can do approximately 12 000 for tenant a. The share that b and c do not use is lost.

| | Maximum = start value (v1) | Ceiling can go above the start value when the CPU is idle |
| --- | --- | --- |
| Isolation | The share of a tenant is always available, without delay | When a tenant increases its rate, the ceiling must decrease first. This takes 1 to 2 windows (10 to 20 seconds). The tenant gets less than its share during this time. |
| Use of the hardware | The share that a tenant does not use is lost | The broker uses all of its capacity |
| Complexity | Low | A rule to go above the start value, and a test for a tenant that increases its rate |

The spike keeps v1.

## Weights

The spike calculates `w̄` from the topology, with the replicas spread evenly over all brokers. So all brokers use the same `w̄`, and a tenant gets the same share after a leader change. If each broker used its real replicas, the limit of a tenant would change after a leader change.

The ceiling is still different on each broker, because each broker has its own controller. After a leader change, a tenant can get a different limit when the two brokers have different ceilings.
