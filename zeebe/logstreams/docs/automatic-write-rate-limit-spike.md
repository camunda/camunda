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

## Symbols and formulas

All rates are in records each second. The other sections refer to the formulas by their number, for example (F5).

### Symbols

| Symbol | Meaning | Default |
| --- | --- | --- |
| $`B`$ | Count of brokers | 3 |
| $`C`$ | CPU limit of one broker, in CPUs | 2 |
| $`P_t`$ | Count of partitions of tenant $`t`$ | 3 |
| $`RF_t`$ | Replication factor of tenant $`t`$ | 3 |
| $`w_t`$ | Weight of tenant $`t`$ | 1 |
| $`R`$ | Records that one CPU processes each second on a leader. The controller uses this assumed value. | 10 000 |
| $`R_{\text{true}}`$ | The real value of $`R`$ on the hardware. The controller does not know it. | Set by each scenario |
| $`L`$, $`F`$ | Relative CPU cost of a record on a leader (`LEADER`) and on a follower (`FOLLOWER`) | 1, 0.6 |
| $`\lambda`$ | Share of the replicas on a broker that the broker leads | Calculated |
| $`c`$ | Ceiling of a broker: the limit of each partition for a tenant with $`w_t = \bar{w}`$ | Calculated |
| $`\theta`$ | Throttled ratio: share of the CFS periods in a window in which the CPU was throttled | Measured |
| $`W`$ | Length of a window | 10 s |
| $`X_t`$, $`r_t`$ | Rate that tenant $`t`$ sends to one partition, and rate that the partition accepts | Set by each scenario |
| $`\rho_t`$ | CPU cost of a rejected record of tenant $`t`$, relative to the CPU cost of a record on a leader. REST has a higher $`\rho`$ than gRPC. | 0 |
| $`d`$, $`s`$ | Decrease factor, increase step | 0.9, 0.05 |
| $`\theta_{\max}`$, $`n`$ | Throttled ratio threshold, count of throttled windows | 0.1, 2 |

### Topology

| No. | Formula | Meaning |
| --- | --- | --- |
| F1 | $`N = \dfrac{\sum_t P_t \cdot RF_t}{B}`$ | Replicas on one broker |
| F2 | $`\bar{w} = \dfrac{\sum_t P_t \cdot RF_t \cdot w_t}{\sum_t P_t \cdot RF_t}`$ | Mean weight over all replicas |
| F3 | $`\lambda_{\text{up}} = \dfrac{\sum_t P_t}{B \cdot N}`$ | Leader share when all brokers run |
| F4 | $`\lambda_{\text{down}} = \min\left(1, \dfrac{\sum_t P_t}{(B - 1) \cdot N}\right)`$ | Leader share when one broker is down |
| F5 | $`\operatorname{cost}(\lambda) = \lambda \cdot L + (1 - \lambda) \cdot F`$ | CPU cost of one replica, relative to a leader |

### Bounds and limit

| No. | Formula | Meaning |
| --- | --- | --- |
| F6 | $`\operatorname{ceiling}(\lambda) = R \cdot \min\left(1, \dfrac{C}{N \cdot \operatorname{cost}(\lambda)}\right)`$ | Ceiling at which the CPU of a broker is just sufficient, a maximum of $`R`$ |
| F7 | $`\text{floor} = \operatorname{ceiling}(1)`$ | The broker leads all its replicas |
| F8 | $`\text{start} = \operatorname{ceiling}(\lambda_{\text{down}})`$ | In v1, the start value is also the maximum of $`c`$ |
| F9 | $`\text{limit}_t = c \cdot \dfrac{w_t}{\bar{w}}`$ | Write rate limit of one partition of tenant $`t`$ |
| F10 | $`\text{rate}_t \le P_t \cdot \text{limit}_t`$ | Write rate limit of tenant $`t`$ |

### Controller

| No. | Formula | Meaning |
| --- | --- | --- |
| F11 | $`\theta = \dfrac{\text{nr\_throttled}}{\text{nr\_periods}}`$ | Throttled ratio over one window $`W`$ |
| F12 | $`c \leftarrow \max(\text{floor}, d \cdot c)`$ | Decrease after $`n`$ windows in sequence with $`\theta > \theta_{\max}`$ |
| F13 | $`c \leftarrow \min(\text{start}, c + s \cdot \text{start})`$ | Increase on a rejection at the limit, when $`\theta \le \theta_{\max}`$. A maximum of one step in each window. |
| F14 | $`c \leftarrow \operatorname{clamp}\left(c \cdot \dfrac{\text{start}_{\text{new}}}{\text{start}_{\text{old}}}\right)`$ | Change of the topology. The clamp keeps $`c`$ between the floor and the start value. |

### Capacity and fair share

| No. | Formula | Meaning |
| --- | --- | --- |
| F15 | $`c_{\text{true}} = \dfrac{R_{\text{true}} \cdot C}{N \cdot \operatorname{cost}(\lambda_{\text{up}})}`$ | Ceiling that the hardware sustains when all brokers run |
| F16 | $`K = \dfrac{B \cdot C \cdot R_{\text{true}}}{L + (RF - 1) \cdot F}`$ | Records each second for the cluster, when all tenants have the same $`RF`$ |
| F17 | $`\text{fair}_t = \min(\text{demand}_t, x \cdot w_t)`$, with $`x`$ so that $`\sum_t \text{fair}_t = K`$ | Weighted max-min fair share of tenant $`t`$ |
| F18 | $`\text{v1}_t = \min\left(\text{fair}_t, P_t \cdot \text{start} \cdot \dfrac{w_t}{\bar{w}}\right)`$ | Expected value of tenant $`t`$ in v1 |

(F17) is the weighted max-min fair share. A tenant that sends less than its share keeps its rate. The other tenants divide the remaining capacity in proportion to their weights.

(F15) and (F16) give the same capacity when all $`T`$ tenants have the same partitions, replication factor and weight. Then $`K / T = P_t \times c_{\text{true}}`$.

### Limits of the controller

| No. | Formula | Meaning |
| --- | --- | --- |
| F19 | $`\dfrac{\text{start}}{c_{\text{true}}} = \dfrac{R}{R_{\text{true}}} \cdot \dfrac{\operatorname{cost}(\lambda_{\text{up}})}{\operatorname{cost}(\lambda_{\text{down}})}`$ | Headroom of the start value |
| F20 | $`c_{\text{true}} \ge \text{floor} \iff \dfrac{R}{R_{\text{true}}} \le \dfrac{\operatorname{cost}(1)}{\operatorname{cost}(\lambda_{\text{up}})}`$ | Largest error of $`R`$ that the controller can correct |

(F19) gives the headroom of the start value. With $`R = R_{\text{true}}`$, the start value is less than $`c_{\text{true}}`$ because $`\operatorname{cost}(\lambda_{\text{down}}) > \operatorname{cost}(\lambda_{\text{up}})`$. This CPU stays free for a failover. (F20) gives the largest error of $`R`$ that the controller can correct. Below the floor, the controller cannot decrease the ceiling.

## Code

| Class | Location | Function |
| --- | --- | --- |
| `AutomaticWriteRateLimitCfg` | `src/main/.../flowcontrol/automatic` | The parameters and their start values. |
| `WriteRateLimitTopology` | `src/main/.../flowcontrol/automatic` | The tenants, partitions, replication factors and weights of the cluster (F1 to F4). |
| `WriteRateLimitBounds` | `src/main/.../flowcontrol/automatic` | The floor (F7), the start value (F8) and the limit of a tenant (F9). |
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
| CPU guard | $`\theta > \theta_{\max}`$ (10%) for $`n`$ (2) windows in sequence | Decrease the ceiling (F12) |
| No data | All leader partitions with writes have an active exporter throttle | Keep the ceiling, block increases |
| Increase | $`\theta \le \theta_{\max}`$, and a partition rejects a write at its limit | Increase the ceiling (F13), a maximum of one step in each window |

CPU throttling is the only signal that decreases the ceiling. Finding 2 gives the signals that the spike tested and the reasons to remove them.

## Model of the simulation

- Each broker has a CPU limit $`C`$. Each replica uses a maximum of one core.
- The simulation steps in ticks of $`\Delta t = 100\,\text{ms}`$.
- One record of tenant $`t`$ costs CPU seconds on a replica:

  | No. | Formula | Meaning |
  | --- | --- | --- |
  | S1 | $`\text{cpu}_{\text{record}} = \dfrac{\text{role} \cdot k_t}{R_{\text{true}}}`$ | CPU seconds of one record. $`\text{role} = L`$ on a leader and $`F`$ on a follower. $`k_t`$ is the relative cost of the records of tenant $`t`$, default 1. |
  | S2 | $`\text{demand}_r = \min(\text{pending}_r \cdot \text{cpu}_{\text{record}}, \Delta t)`$ | CPU demand of replica $`r`$ in one tick |
  | S3 | $`D = \sum_r \text{demand}_r + \text{reject}`$ | CPU demand of the broker in one tick. $`\text{reject}`$ is the CPU of the rejections (S8). |
  | S4 | $`\text{share} = \min\left(1, \dfrac{C \cdot \Delta t}{D}\right)`$ | Each replica gets $`\text{share} \cdot \text{demand}_r`$ |

  A tick with $`D > C \times \Delta t`$ counts as throttled. $`\theta`$ (F11) is the count of throttled ticks divided by the count of ticks in the window.
- The partitions of a tenant get their replicas and priorities as in `RoundRobinPartitionDistributor` and `PartitionPriorityAssigner`. The leader is the replica with the highest priority on a broker that runs.
- Each leader has a request limiter with the default configuration of the broker: `StabilizingAIMDLimit` in a `WindowedLimit`. The request limiter applies before the write rate limit, as in `FlowControl`.

  | No. | Formula | Meaning |
  | --- | --- | --- |
  | S5 | $`\text{latency} = 5\,\text{ms} + \dfrac{\text{backlog}}{\text{processing rate}}`$ | Latency of a request |
  | S6 | $`\text{limit}_{\text{req}} \leftarrow 0.9 \cdot \text{limit}_{\text{req}}`$ | When the latency is more than 200 ms (expected RTT) |
  | S6 | $`\text{limit}_{\text{req}} \leftarrow \text{limit}_{\text{req}} + 1`$ | When the latency is 200 ms or less, and $`\text{in-flight} \ge \text{limit}_{\text{req}} / 2`$ |
  | S7 | $`\text{admitted} = \min\left(\text{offered}, \max(0, \text{limit}_{\text{req}} - \text{backlog}) \cdot \dfrac{\Delta t}{5\,\text{ms}}\right)`$ | Records that the request limiter admits in one tick |

  The start value of $`\text{limit}_{\text{req}}`$ is 100. The limit changes approximately once each second.
- A tenant that floods sends $`2 \cdot R_{\text{true}}`$ records each second to each partition. This is two times the rate of one core on a leader. One replica uses a maximum of one core, so a partition cannot process this rate.
- The leader accepts $`\min(\text{admitted}, \text{limit}_t \times \Delta t)`$ records (F9). When the exporter backlog is more than 10 000 records, the exporter throttle sets the limit to the export rate. The partition is then excluded.
- A rejected record uses CPU on the leader. The request limiter and the write rate limit both reject in the sequencer. Before the sequencer, the gateway parsed and authenticated the request, and the command API decoded it.

  | No. | Formula | Meaning |
  | --- | --- | --- |
  | S8 | $`\text{cpu}_{\text{reject}} = \dfrac{\rho_t \cdot L}{R_{\text{true}}}`$ | CPU seconds of one record that the leader rejects. All the records that the leader does not accept count: $`X_t - r_t`$ each second. |

  The rejections do not use the core of a replica. When the broker throttles, the rejections get the same share as the replicas (S4). The CPU that the rejections do not get is lost: the client gets a timeout, not a rejection.
- A broker that stops loses its queues. After a restart, the broker starts at the start value.

### Limits of the model

1. One record is one command. Real commands write follow-up records. So a real backlog is larger than the request limit, and a real latency is higher.
2. The rate of a flood is constant. A real client with retries sends more after a rejection. A real client with a limit of requests in flight sends less.
3. A throttled broker gives CPU in proportion to demand. Real CFS gives a more equal share to each thread that can run. Real CFS can protect a small tenant better than the simulation shows.
4. A replica with a backlog has a higher demand (S2). So in a throttled tick, the processing gets more CPU than the rejections. In real CFS, the share depends on the count of threads that reject and the count of threads that process (limit 3).

## Results

Setup, if not stated otherwise: 3 brokers with 2 CPUs, 3 tenants with 3 partitions each, replication factor 3, $`R = 10\,000`$. The values are records each second for each tenant, as a sum over its 3 partitions. Each value is a mean over the last 2 minutes of a 5-minute run.

### Calculations

The values below use the defaults of the symbols table.

$`R`$ is a rate for one CPU on a leader. It is not a rate for one tenant.

**Step 1: topology.**

```math
\begin{aligned}
&\text{(F1)} & N &= \frac{3 \text{ tenants} \cdot 3 \text{ partitions} \cdot 3 \text{ replicas}}{3 \text{ brokers}} = 9 \\
&\text{(F2)} & \bar{w} &= 1 \quad \text{(all weights are 1)} \\
&\text{(F3)} & \lambda_{\text{up}} &= \frac{9}{3 \cdot 9} = \frac{1}{3} \\
&\text{(F4)} & \lambda_{\text{down}} &= \frac{9}{2 \cdot 9} = \frac{1}{2} \\
&\text{(F5)} & \operatorname{cost}(1) &= 1 \\
& & \operatorname{cost}(1/2) &= 0.5 \cdot 1 + 0.5 \cdot 0.6 = 0.8 \\
& & \operatorname{cost}(1/3) &= \tfrac{1}{3} \cdot 1 + \tfrac{2}{3} \cdot 0.6 = 0.733
\end{aligned}
```

**Step 2: bounds of the ceiling.** For each tenant, multiply by $`P_t = 3`$.

```math
\begin{aligned}
&\text{(F7)} & \text{floor} &= \frac{10\,000 \cdot 2}{9 \cdot 1} = 2\,222 & &\rightarrow 6\,667 \text{ for each tenant} \\
&\text{(F8)} & \text{start} &= \frac{10\,000 \cdot 2}{9 \cdot 0.8} = 2\,778 & &\rightarrow 8\,333 \text{ for each tenant}
\end{aligned}
```

**Step 3: capacity.**

```math
\begin{aligned}
&\text{(F15)} & c_{\text{true}} &= \frac{R_{\text{true}} \cdot 2}{9 \cdot 0.733} \\
& & R_{\text{true}} = 10\,000 &\rightarrow 3\,030 \text{ for each partition} \rightarrow 9\,091 \text{ for each tenant} \\
& & R_{\text{true}} = 8\,000 &\rightarrow 2\,424 \text{ for each partition} \rightarrow 7\,273 \text{ for each tenant} \\
& & R_{\text{true}} = 6\,000 &\rightarrow 1\,818 \text{ for each partition} \rightarrow 5\,455 \text{ for each tenant} \\[1ex]
&\text{(F16)} & K &= \frac{3 \cdot 2 \cdot R_{\text{true}}}{1 + 2 \cdot 0.6} = \frac{6 \cdot R_{\text{true}}}{2.2} \\
& & R_{\text{true}} = 10\,000 &\rightarrow 27\,273 \text{ for the cluster} \\
& & R_{\text{true}} = 8\,000 &\rightarrow 21\,818 \text{ for the cluster}
\end{aligned}
```

**Step 4: fair share of each scenario (F17).**

| Scenario | Calculation | Fair share |
| --- | --- | --- |
| All tenants flood, $`R_{\text{true}} = R`$ | $`x = 27\,273 / 3`$ | 9 091 each |
| Tenant a floods, b and c send 7 500 | b and c send less than 9 091 and keep 7 500. Tenant a gets $`27\,273 - 2 \times 7\,500`$. | a 12 273, b and c 7 500 |
| Weights 2:1:1, all tenants flood, $`R_{\text{true}} = 8\,000`$ | $`x = 21\,818 / (2 + 1 + 1)`$. Tenant a gets $`2x`$, b and c get $`x`$. | 10 909, 5 454, 5 454 |
| Tenant a floods, b and c send 7 500, $`R_{\text{true}} = 8\,000`$ | $`x = 21\,818 / 3 = 7\,273`$. Tenants b and c send more than 7 273. | 7 273 each |
| All tenants flood, $`R_{\text{true}} = 8\,000`$ | $`x = 21\,818 / 3`$ | 7 273 each |

**Step 5: expected value in v1 (F18).**

- With $`R_{\text{true}} = R`$, the limit is $`3 \times 2\,778 = 8\,333`$ for each tenant. This is less than the fair share of 9 091 and 12 273.
- With weights 2:1:1, $`\bar{w} = (9 \times 2 + 9 \times 1 + 9 \times 1) / 27 = 4/3`$ (F2). The limit of tenant a is $`3 \times 2\,778 \times 2 / (4/3) = 12\,500`$. The limit of tenants b and c is $`3 \times 2\,778 \times 1 / (4/3) = 6\,250`$. These limits are more than the fair share with $`R_{\text{true}} = 8\,000`$. The controller decreases the ceiling, so the fair share applies.
- With $`R_{\text{true}} = 8\,000`$, the capacity of 7 273 is less than 8 333. The controller decreases the ceiling, so the fair share applies.

### Start value

The start value is less than the capacity of 9 091 for each tenant for two reasons:

1. The start value keeps CPU free for a failover (F19). $`\text{start} / c_{\text{true}} = 0.733 / 0.8 = 0.917`$ when $`R = R_{\text{true}}`$.
2. In v1, the ceiling cannot go above the start value (Finding 4). A tenant cannot use the share that another tenant does not use.

### Fairness

The column "Fair share" gives the result of a work-conserving mechanism with the full capacity. The column "Expected in v1" adds the limit of the start value.

| Scenario | Fair share | Expected in v1 | Both mechanisms | Request limiter only |
| --- | --- | --- | --- | --- |
| All tenants flood, $`R_{\text{true}} = R`$ | 9 091 each | 8 333 each, no throttling | 8 333 each, CPU throttled 0% | 8 550 each, CPU throttled 86% |
| Tenant a floods, b and c send 7 500 | a 12 273, b and c 7 500 | a 8 333, b and c 7 500 | a 8 333, b and c 7 500, CPU throttled 0% | a 12 471, b and c 7 408, CPU throttled 100% |
| Weights 2:1:1, all tenants flood, $`R_{\text{true}} = 8\,000`$ | 10 909, 5 454, 5 454 | Same as fair share | 10 451, 5 413, 5 413, CPU throttled 44% | 6 840 each, the weights have no effect |
| Tenant a floods, b and c send 7 500, $`R_{\text{true}} = 8\,000`$ | 7 273 each | Same as fair share | 7 110, 7 098, 7 098, CPU throttled 52% | a 9 288, b and c 6 270 |
| All tenants flood, $`R_{\text{true}} = 8\,000`$ | 7 273 each | Same as fair share | 7 015, 7 017, 7 017, CPU throttled 36% | 6 840 each |

When $`R_{\text{true}}`$ is 8 000, the capacity is less than the start value. The controller then decreases the ceiling to the capacity, and the start value has no effect. In the second row, tenant a can use the capacity that b and c do not use: $`27\,273 - 2 \times 7\,500 = 12\,273`$. In v1, tenant a stays at 8 333.

With the request limiter only, tenant a gets more than b and c in the second and fourth rows. Tenants b and c then get less than they send, or less than their fair share.

Other results:

- With one tenant, the mechanism is off.
- When the CPU is sufficient and one or two tenants have expensive records, the CPU does not throttle. The ceiling does not change, and the other tenants keep their full limit.
- When the exporter of one tenant is slow, the ceiling does not change.
- When all exporters stop, the ceiling does not change.
- When the operator adds a tenant, the limits of the other tenants decrease.
- With 6 partitions for each tenant on 3 brokers, the start value holds during a rolling restart.
- When $`R_{\text{true}}`$ goes back to $`R`$, the ceiling goes back to the start value on demand.

## Known gaps

1. **Measurement of the CPU load.** The controller needs $`\theta`$ (F11) for each window. This is a requirement. On cgroup v2, `cpu.stat` gives `nr_periods` and `nr_throttled`. The broker does not read these values today. When the broker cannot read them, for example without a CPU limit, the mechanism is off. Without a measurement of the CPU, the broker needs another estimate of its load. Possible estimates:
   - The CPU usage of the container (`usage_usec` in `cpu.stat`) divided by $`C \times W`$.
   - The idle time of the actor scheduler threads.
   - The processing rate of the leaders, compared with the rate that $`R`$ predicts.
2. **Expensive records.** The limit counts records, not CPU. A tenant with expensive records causes CPU throttling. The CPU guard then decreases the ceiling of all tenants. The solution direction gives this gap in section 5. The spike keeps records each second as the unit.
3. **Bottleneck that is not CPU.** The controller sees only CPU throttling. When the disk or the network is the bottleneck, the ceiling does not decrease. The request limiter then protects the queues, but without weights. A solution needs a capacity signal for that resource, not a backlog signal.
4. **Not work-conserving.** See Finding 4.
5. **AIMD probe.** When $`R`$ is too high, the ceiling oscillates around $`c_{\text{true}}`$ (F15). The step of (F13) and the factor of (F12) set the amplitude. Each increase step causes some CPU throttling, between 36% and 52% of the windows in the results. The tenants get 96% to 98% of their fair share.
6. **Unbalanced failover.** See Finding 1.
7. **Floor.** See Finding 3.
8. **Rejections use CPU.** See Finding 5.

## Finding 1: the start value does not hold when the failover is not balanced

### Observation

Setup: 3 brokers, 3 tenants, 3 partitions for each tenant, replication factor 3.

- `PartitionPriorityAssigner` changes the order of the secondary priorities after each round of partitions. One round has one partition for each broker.
- With 3 partitions on 3 brokers, each tenant has only one round. The second priority is always the next broker in the ring.
- Each tenant starts its distribution at broker 0. So all tenants have the same pattern.
- When broker 0 stops, broker 1 gets all the leaders of broker 0. Broker 1 then leads 6 of its 9 replicas ($`\lambda = 2/3`$). The start value uses $`\lambda_{\text{down}} = 1/2`$ (F4). $`\operatorname{cost}(2/3) = 0.867`$ (F5), so the CPU demand is 8% more than at $`\lambda_{\text{down}}`$.
- The CPU of broker 1 throttles. Its controller decreases its ceiling to the floor. Broker 2 does not change.
- After the restart, the leadership goes back. The ceiling of broker 1 goes back to the start value on demand.
- With 6 partitions for each tenant, the leaders of a stopped broker go to the two other brokers in equal parts. The start value then holds.

A related effect: when a tenant has fewer partitions than brokers, the leaders are not balanced, also when all brokers run. For example, with 2 partitions on 3 brokers, broker 2 is never a leader.

Test: `shouldLowerCeilingOfTakeoverBrokerDuringRollingRestartWhenFailoverIsUnbalanced`. The test records the gap. It does not record the target behaviour.

### Possible solutions

1. Calculate $`\lambda`$ for the start value from the worst failover. The worst failover is that one broker gets all the leaders of the stopped broker. This gives a lower start value for all topologies.
2. Calculate $`\lambda`$ from the real priorities of the topology. The start value is then correct for each topology. The calculation needs the partition distribution, not only the count of partitions.
3. Change `PartitionPriorityAssigner` so that the secondary priorities alternate between partitions, also inside one round. Or let each tenant start its distribution at a different broker. This changes the cluster, not the controller.
4. Keep the formula. Write the condition in the documentation: the count of partitions of each tenant is a multiple of $`2 \times \text{brokers}`$.

## Finding 2: CPU throttling is the only decrease signal

The spike tested four sets of signals. The first three failed. This section gives the sequence.

> [!NOTE]
> The values in this section come from an earlier version of the model. In that version, a tenant that floods sent 1 000 000 records each second to each partition, and a rejection used no CPU. The code of the signals that the spike removed is not in the branch. Thus the spike did not run these scenarios again with the current model, and the values can be different with the current flood of $`2 \cdot R_{\text{true}}`$.

### Step 1: the size of the backlog

The first version used the size of the backlog, without a request limiter in the model.

- A partition counted as backlogged when its backlog was more than 1 000 records and more than in the last window.
- The CPU guard decreased the ceiling in each throttled window. While the backlog drained, the CPU stayed at 100%. The guard then decreased the ceiling again, to the floor.
- With $`R_{\text{true}} = 8\,000`$, the backlog increased to approximately 49 000 records. The CPU throttled in approximately 50% of the windows.

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

- With $`R_{\text{true}} = 8\,000`$, the first decrease came after 120 seconds, not after 20 seconds. Before that, the CPU throttled in approximately 80% of the windows.
- The backlog rule almost never fired. When it fired, two tenants had expensive records and the CPU was sufficient. The rule then decreased the limit of a third tenant that had no problem.

The spike also measured the latency of each partition, to use it as a threshold for the backlog rule:

| Scenario | CPU throttled | Mean latency | Maximum latency | Samples at 100 ms or more |
| --- | --- | --- | --- | --- |
| All tenants at their limit | 0% | 5 ms | 5 ms | 0% |
| $`R_{\text{true}} = 8\,000`$, first 90 seconds | 75% to 89% | 27 to 43 ms | 49 to 86 ms | 0% |
| $`R_{\text{true}} = 8\,000`$, after 210 seconds | 100% | 80 to 82 ms | 105 to 109 ms | 10% to 19% |
| One tenant with records of cost 3 | 67% to 100% | 44 to 76 ms | 98 to 160 ms | 1% to 26% |

The latency follows the current limit of the request limiter, not its expected RTT. By Little's law, $`\text{latency} \approx \text{limit}_{\text{req}} / \text{processing rate}`$ (S5, S7). Example: $`100 / 2\,424 = 41\,\text{ms}`$. `StabilizingAIMDLimit` adds 1 to the limit in each update, approximately once each second (S6). The latency is 200 ms when $`\text{limit}_{\text{req}} = 0.2\,\text{s} \times 2\,424 = 485`$. From 100, this takes approximately 385 seconds. So a latency threshold reacts late, and its value depends on the throughput of the partition.

### Step 4: rejections of the request limiter

The spike then tested the rejections of the request limiter (`RequestLimitExhausted`) as the decrease signal, without the CPU guard. A decrease happened when at least 2 tenants, and at least half of the active tenants, had the signal.

- Mode 1: the partition had a rejection in the window.
- Mode 2: the partition had a rejection in the window, and its tenant was below its write rate limit.

| Scenario | Expected | Mode 1 | Mode 2 |
| --- | --- | --- | --- |
| All tenants flood, $`R_{\text{true}} = R`$ | 8 333 each | 6 667 each. The ceiling goes to the floor while the CPU is idle. | 8 333 each |
| Weights 2:1:1, all tenants flood, $`R_{\text{true}} = 8\,000`$ | 2:1:1, CPU not throttled | 2:1:1 at the floor. 20% of the capacity is not used. | 9 326, 6 250, 6 250. No decrease, CPU throttled 100%. |
| Tenant a floods, b and c send 7 500, $`R_{\text{true}} = 8\,000`$ | 7 273 each | 6 750 each | 7 157, 7 163, 7 163 |
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

## Finding 3: the floor does not protect the tenants when $`R`$ is much too high

The controller can correct an $`R`$ that is too high only down to the floor (F20):

```math
\begin{aligned}
\frac{R}{R_{\text{true}}} &\le \frac{\operatorname{cost}(1)}{\operatorname{cost}(\lambda_{\text{up}})} = \frac{1}{0.733} = 1.36 \\
R_{\text{true}} &\ge \frac{10\,000}{1.36} = 7\,333
\end{aligned}
```

So the controller can correct an $`R`$ that is a maximum of approximately 36% too high.

With $`R_{\text{true}} = 6\,000`$, $`c_{\text{true}}`$ is 1 818 (F15). The floor is 2 222 (F7). The ceiling stays at the floor and the CPU throttles all the time. The floor is a promise only when $`R`$ is approximately correct. The load test must measure $`R`$ with a margin.

## Finding 4: the limit is not work-conserving

A mechanism is work-conserving when it never leaves capacity idle while a tenant wants more. In v1, the ceiling cannot go above the start value. So the maximum of each tenant does not change.

Example: tenant a floods, b and c send 7 500 records each second, and $`R_{\text{true}} = R`$. The CPU is not throttled. Tenant a stays at $`3 \times \text{start} = 8\,333`$ records each second. The cluster can do $`K - 2 \times 7\,500 = 12\,273`$ for tenant a (F16, F17). The share that b and c do not use is lost.

| | Maximum = start value (v1) | Ceiling can go above the start value when the CPU is idle |
| --- | --- | --- |
| Isolation | The share of a tenant is always available, without delay | When a tenant increases its rate, the ceiling must decrease first. This takes 1 to 2 windows (10 to 20 seconds). The tenant gets less than its share during this time. |
| Use of the hardware | The share that a tenant does not use is lost | The broker uses all of its capacity |
| Complexity | Low | A rule to go above the start value, and a test for a tenant that increases its rate |

The spike keeps v1.

## Finding 5: the rejections of a tenant that floods decrease the ceiling of all tenants

### Observation

Setup: tenant a floods, b and c send 7 500 records each second, $`R_{\text{true}} = R`$. All tenants have the same $`\rho`$. The values for b and c are equal.

Flood of $`2 \cdot R_{\text{true}}`$ for each partition:

| $`\rho`$ | CPU throttled | Mean ceiling | CPU of the rejections | a | b and c | b and c, request limiter only |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 0% | 2 778 | 0% | 8 333 | 7 500 | 7 408 |
| 0.1 | 0% | 2 778 | 9% | 8 333 | 7 500 | 7 130 |
| 0.15 | 0% | 2 778 | 13% | 8 333 | 7 500 | 7 000 |
| 0.2 | 58% | 2 424 | 17% | 7 528 | 7 380 | 6 839 |
| 0.25 | 66% | 2 269 | 22% | 6 943 | 6 944 | 6 658 |
| 0.3 | 100% | 2 222, the floor | 27% | 6 667 | 6 667 | 6 635 |
| 0.6 | 100% | 2 222, the floor | 27% | 6 667 | 6 667 | 5 926 |
| 1.0 | 100% | 2 222, the floor | 40% | 5 485 | 5 485 | 5 061 |

With a flood of $`1.5 \cdot R_{\text{true}}`$, the CPU starts to throttle between $`\rho = 0.2`$ and $`0.25`$. The ceiling goes to the floor between $`\rho = 0.4`$ and $`0.6`$.

- The CPU guard cannot see the difference between an overload and rejections. It decreases the ceiling of all tenants. So the flood of tenant a decreases the rate of b and c.
- While tenant a floods, the ceiling stays at the floor. (F13) needs $`\theta \le \theta_{\max}`$ to increase the ceiling.
- Tenants b and c get more than with the request limiter only, for all values of $`\rho`$.
- At $`\rho = 1`$, b and c get less than the floor. The floor does not protect them.
- In the simulation, the floor holds up to $`\rho = 0.6`$ because of limit 4 of the model. The real value of $`\rho`$ at which the floor fails is not known.

Tests: `shouldNotLowerCeilingWhenRejectionsCostLittleCpu`, `shouldLowerCeilingOfAllTenantsWhenRejectionsOfFloodingTenantThrottleCpu` and `shouldGiveOtherTenantsMoreThanRequestLimiterAloneWhenRejectionsCostCpu`.

### Formulas

When each broker leads one partition of each tenant ($`P_t = B`$):

| No. | Formula | Meaning |
| --- | --- | --- |
| F21 | $`\text{cpu}_{\text{broker}} = \dfrac{1}{R_{\text{true}}} \sum_t \left( r_t \cdot (L + (RF - 1) \cdot F) + \rho_t \cdot L \cdot (X_t - r_t) \right)`$ | CPU of one broker. The CPU throttles when $`\text{cpu}_{\text{broker}} > C`$. |
| F22 | $`g = \dfrac{\rho \cdot L}{L + (RF - 1) \cdot F}`$ | Gain of the loop. One accepted record less removes $`L + (RF - 1) \cdot F`$ of CPU from the cluster and adds $`\rho \cdot L`$. |

The loop is: a lower ceiling causes more rejections, the rejections use more CPU, and the CPU guard decreases the ceiling again. The loop continues without limit only when $`g \ge 1`$, that is when $`\rho \ge 2.2`$ with the defaults. Below that value, each decrease removes more CPU than it adds, and the ceiling stops at a lower value. The floor (F12) stops the decrease in all cases.

Calculation with a flood of $`2 \cdot R_{\text{true}}`$ (F21):

```math
\begin{aligned}
&\text{Start value: } r = 2\,778, 2\,500, 2\,500 & \rho &> \frac{(2 - 7\,778 \cdot 2.2 / 10\,000) \cdot 10\,000}{20\,000 - 2\,778} = 0.17 \\
&\text{Floor: } r = 2\,222 \text{ each} & \rho &> \frac{(2 - 6\,667 \cdot 2.2 / 10\,000) \cdot 10\,000}{(20\,000 - 2\,222) + 2 \cdot (2\,500 - 2\,222)} = 0.29
\end{aligned}
```

The first line gives the value of $`\rho`$ at which the CPU starts to throttle. The second line gives the value of $`\rho`$ at which the ceiling stays at the floor. With a flood of $`1.5 \cdot R_{\text{true}}`$, the values are 0.24 and 0.40.

### Unknowns

- The value of $`\rho`$ for gRPC and for REST. REST has a higher value. The load test must measure both.
- The rate of a real flood. A client with retries and no backoff sends more after each rejection. This increases $`X_t - r_t`$.
- The CPU share of the rejections in real CFS (limit 3 and limit 4 of the model).

### Possible solutions

1. Measure $`\rho`$ for gRPC and REST in the load test. Compare the result with the thresholds of (F21).
2. Reject early and at a low cost, before the gateway parses the payload. Send a retry delay with the rejection, so that the clients use a backoff. This decreases $`\rho`$ and $`X_t`$.
3. Do not decrease the ceiling when the rejections cause the throttling. This needs a measured $`\rho`$ and an $`R`$ that the controller can trust.
4. Limit the requests of each tenant in the gateway, before the broker. This is necessary when the rejections of one tenant alone use most of $`C`$.

## Weights

The spike calculates $`\bar{w}`$ from the topology (F2), with the replicas spread evenly over all brokers. So all brokers use the same $`\bar{w}`$, and a tenant gets the same share after a leader change. If each broker used its real replicas, the limit of a tenant would change after a leader change.

The ceiling is still different on each broker, because each broker has its own controller. After a leader change, a tenant can get a different limit when the two brokers have different ceilings.
