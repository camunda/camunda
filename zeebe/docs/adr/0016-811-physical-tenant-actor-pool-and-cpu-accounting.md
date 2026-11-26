# A dedicated actor pool and a CPU account for each physical tenant

**DRI**: Carlo Sana

**Status**: Accepted (8.11)

**Purpose**: Defines where the actors of a physical tenant run and how the broker measures the CPU
time that each physical tenant uses.

**Audience**: Zeebe engineers who work on the actor scheduler, the broker partition management, or
the physical tenants epic, and AI agents that reason about tenant isolation or CPU limits.

## Context

All physical tenants on a broker share the same actor threads. When one tenant has a spike or runs a
long actor job, such as a slow FEEL evaluation, it holds threads that the other tenants need. The
processing latency of the other tenants then increases, and their request limiters reduce their
throughput. To limit the CPU of a tenant later, the broker must also know how much CPU each tenant
uses. Before this decision, the broker did not measure this.

The result: each physical tenant gets its own actor pool, and its threads are in a thread group
that the broker uses to charge CPU time to the tenant. This decision measures CPU time only. It does
not stop work. A different decision will define how the broker enforces the budget.

## Decision

**D1. The partition actors of a physical tenant run on a scheduler for that tenant only.**
The actors that are not specific to a tenant stay on the shared scheduler of the broker. This
function is optional and is off by default. When it is off, all actors run on the shared scheduler,
as before.

**D2. The broker creates the scheduler of a tenant when it needs it and stops it with the tenant.**
The broker creates the scheduler when the first actor of the tenant starts on this broker. A tenant
that has no partitions on the broker gets no threads. The broker stops the scheduler when the
partition manager of the tenant stops.

**D3. Broker settings set the default size of each tenant's pool; a tenant can override its own
CPU and IO thread counts.** The broker-wide actor-pool enablement switch and the default CPU and IO
thread counts are configured under `camunda.system.actor-pool.*`. A tenant can
override the counts under
`camunda.physical-tenants.<id>.system.actor-pool.*`. The broker-wide enablement
switch cannot be overridden per tenant. Thread counts size pools; they do not assign a CPU quota or
limit how much CPU a tenant can use. A later decision about CPU allocation will define that policy.

**D4. A Java thread group identifies the threads of a tenant.**
The broker creates the actor threads of a tenant in a thread group for that tenant. A thread that
one of these threads starts is in the same group, and the broker charges its CPU time to the tenant.
This includes the threads that exporters start, because the exporters of a partition run on the
actor threads of its tenant.

**D5. One sampler for each broker measures the CPU time of the thread groups.**
In each period, the sampler reads the CPU time of the threads in each registered group. It uses CPU
time, not wall time. The sampler identifies a group with an ID. It does not know about tenants, so
it stays in the scheduler module.

**D6. The budget of a sample is the time since the last read of the group, multiplied by the CPU
quota of the group.**
The caller gives the quota as a number of CPUs, for example 1.5. The sampler starts one sample in
each period, but a sample can start late, or start after a sample that failed. The budget of that
sample covers all the time that its CPU time covers, so the delay does not create debt. CPU time
above the budget becomes debt for the next samples. The sampler does not keep budget that a group
did not use, so a group cannot save idle time and use it later.

**D7. The broker does not charge threads that are not in a tenant group.**
These threads include Netty threads, garbage collection, the JIT compiler, RocksDB threads, and
virtual threads.

## Alternatives considered

- **Weighted or priority scheduling in one shared pool.** One long actor job still holds a shared
  thread. Weights cannot release a thread that a job holds.
- **Isolation by the operating system, with cgroups or `nice`.** Weights do not work when the
  container has a `cpu.max` limit. Customer containers also do not give write access to cgroups.
- **Thread name prefixes to identify the threads of a tenant.** A new thread does not get the
  prefix of the thread that starts it. Thus the broker cannot charge the threads that tenant code
  starts.
- **Measure the CPU time of each actor job on the actor thread.** This adds work to each job on the
  scheduler loop. It also does not measure the threads that tenant code starts.

## Consequences

- When the tenant pools are on, the broker has more threads. The shared pool keeps its size, so
  operators must make the shared pool smaller to keep the same number of threads. Tenant-specific
  thread counts can further change the total number of actor threads on the broker.
- If all partitions of a tenant leave the broker at run time, the pool of the tenant stays until
  the partition manager of the tenant stops.
- A tenant can use CPU that the broker does not charge, through threads that are not in its group.
  For example, an exporter that gives work to a thread pool that a thread outside the group created,
  such as a pool that all of the JVM uses, uses CPU that the broker does not charge to the tenant.
- The sampler reads the list of all threads of the JVM one time in each period. Its cost increases
  with the number of tenant threads.
- The sampler does not charge the CPU time that a thread used after the last sample if the thread
  stops before the next sample.

## Source

- [Reduce noisy neighbour risks for physical tenants](https://github.com/camunda/camunda/issues/70093)
- [Run the partition actors of each physical tenant on a dedicated actor pool](https://github.com/camunda/camunda/issues/71042),
  implemented in [#71044](https://github.com/camunda/camunda/pull/71044)
- [Charge the CPU time of each tenant to a budget with a ThreadGroup and a sampler](https://github.com/camunda/camunda/issues/71022),
  implemented in [#71049](https://github.com/camunda/camunda/pull/71049)

