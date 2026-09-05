# Per-index shard configuration and the single-shard-by-design taxonomy

**DRI**: Data Layer team

**Status**: Accepted (8.11)

**Purpose**: Record that the global `number-of-shards` knob no longer applies to every
secondary-storage index, fix the taxonomy that decides which indices are single-shard by
design, and pin down how an operator overrides that per index.

**Audience**: Engineers and AI coding agents working on secondary storage
(`schema-manager/`, `webapps-schema/`, `configuration/`) or on how Camunda 8 is
configured in containerised deployments.

Relates to: camunda/camunda#56246 (this decision), camunda/camunda#56245 (descriptor
defaults), camunda/camunda#56117 (the post-importer-queue watermark skip),
camunda/camunda#56179 (the brownfield read-path fix).

## Context

Camunda 8 creates roughly 40 indices in its secondary storage. Until 8.11 a single
global knob, `camunda.data.secondary-storage.<db>.number-of-shards`, set the primary
shard count for all of them, with a `number-of-shards-per-index` map as the only
escape hatch.

Two problems followed from that.

**The global knob was applied where sharding cannot help, and where it actively
hurts.** The large majority of the indices are plain `index/` descriptors —
authorization, role, user, group, tenant, mapping-rule, process, decision,
decision-requirements, form, metadata and so on. They hold configuration, definition
or singleton data: small, bounded, and read by exact key. Spreading them over several
shards buys nothing and costs a fan-out on every read. Only the roughly ten
process-instance-volume `Template` indices — list-view, flow-node-instance, variable,
sequence-flow, incident, operation, job, decision-instance, task — have the volume
that sharding exists to serve.

For `post-importer-queue` a multi-shard index was not merely useless but the root
cause of camunda/camunda#56117. Entries for a single partition scattered across shards
that refresh independently, so a reader that assumed one atomic refresh could observe
a later watermark while earlier entries were still invisible, and skip them.
camunda/camunda#56179 hardened the read path for deployments that already had such an
index; camunda/camunda#56245 removed the cause for newly created ones.

**The per-index escape hatch was unusable where it was most needed.** The
`number-of-shards-per-index` property was a `Map<String, Integer>` keyed by the raw
index name. Those names contain dashes, and a dash cannot appear in an environment
variable name, so `CAMUNDA_..._NUMBEROFSHARDSPERINDEX_POST-IMPORTER-QUEUE` did not
bind. A containerised deployment, which configures almost exclusively through the
environment, therefore could not reach the property at all. The map's type also said
nothing about which keys it accepted, so the valid index names could only be found by
reading descriptor sources.

## Decision

**D1. The global `number-of-shards` knob no longer applies to `index/` indices.**

`AbstractIndexDescriptor.getDefaultShardCount()` returns `OptionalInt.of(1)`, so every
plain index defaults to one primary shard regardless of the global setting.
`AbstractTemplateDescriptor` overrides it back to `OptionalInt.empty()`, so
template-backed indices keep following the global knob. The knob is thereby
redefined: it is the shard count for process-instance-volume indices, not for the
whole schema.

This changes behaviour on upgrade for anyone running a global count above 1: newly
created `index/` indices get one shard where previously they got the global value.
Shards are immutable after creation, so existing indices are untouched and no
migration is involved.

**D2. The descriptor is the single source of per-index defaults.**

The taxonomy lives in the descriptor class hierarchy, not in a configuration file, a
constant list, or a naming convention:

|                 Descriptor                 | Default shards |                        Rationale                        |
|--------------------------------------------|----------------|---------------------------------------------------------|
| `AbstractIndexDescriptor` (plain `index/`) | 1              | config/definition/singleton data; sharding buys nothing |
| `AbstractTemplateDescriptor` (`template/`) | global knob    | process-instance volume; sharding is the point          |
| `PostImporterQueueTemplate`                | 1              | atomic refresh is a correctness requirement (#56117)    |
| `MetadataIndex`                            | 1              | singleton schema metadata                               |

A descriptor that must be pinned against its base class overrides
`getDefaultShardCount()` explicitly. Adding a new index therefore decides its shard
behaviour by which base class it extends, which is the property we want: the decision
cannot be forgotten, only overridden.

No tuned per-index defaults are introduced beyond the pinning above. Setting, say,
list-view to 3 by default is a per-configuration choice that can be made later without
disturbing this taxonomy.

**D3. Resolution precedence is explicit config, then descriptor, then global.**

```
config number-of-shards-per-index.<index>  →  descriptor.getDefaultShardCount()  →  global number-of-shards
```

Explicit operator configuration always wins, for every index. This is deliberate: it
keeps an escape hatch for the single-shard-by-design indices, of which
persistent web session is the realistic case — an installation running very many
concurrent sessions may genuinely outgrow one shard.

**D4. `number-of-shards-per-index` is a typed POJO, one nullable field per index.**

The property key is unchanged. Spring's relaxed binding keeps existing kebab-case YAML
working (`list-view` binds to the field `listView`) and additionally makes
`CAMUNDA_..._NUMBEROFSHARDSPERINDEX_LISTVIEW` bind, which the map form could not. The
declared fields also reach `spring-configuration-metadata.json`, which is what makes
the valid keys discoverable in an IDE and in the generated `defaults.yaml`.

A field is declared for **every** index rather than a curated subset, so nothing the
map used to accept is silently dropped and D3's escape hatch survives for all of them.
A `null` field means "not configured" and is omitted from the projected map entirely,
which is what lets the index fall through to the descriptor default.

The replicas and refresh-interval maps keep their `Map<String, Integer>` shape. Their
keys have the same environment-variable weakness, but converting them is a separate
decision and was deliberately kept out of this change.

**D5. Misconfiguration is rejected when it is wrong and warned about when it is a
trade.**

- A count below 1 is a hard error, raised in the configuration layer so the message
  names the property path and the index rather than surfacing as an engine-level
  schema-creation failure.
- Raising a single-shard-by-design index above 1 shard is a **warning**, not a
  rejection — rejecting would contradict D3. What is traded away differs by index:
  for most of them one shard is merely the efficient choice for
  configuration or definition data, and widening costs a fan-out per read; only
  where a reader also assumes a single atomic refresh is it a correctness risk.
  The warning therefore names the index and offers post-importer-queue and
  camunda/camunda#56117 as the example of the latter, rather than asserting that
  risk of whichever index it fires on.
- A configured count that differs from an already-created index's actual count is a
  **warning**. Shards are immutable after creation, so such a setting is otherwise a
  silent no-op: the operator sees the value they asked for in their configuration and a
  differently sharded index in the engine, with nothing connecting the two. The check
  reads live settings at startup and is best-effort — a failure to read them is logged
  at debug and never fails a startup that would otherwise succeed. The engine clients
  deliberately do not log this particular read failure themselves, so that a failure
  the caller ignores cannot reach an operator's alerting as an ERROR.

All three run once per startup, from a single pass over the descriptors. The settings
resolver is called again for every descriptor during template creation, index creation
and the settings update, so warning from there would repeat each message up to three
times.

## Consequences

- Operators who set a global `number-of-shards` above 1 will see newly created
  `index/` indices come up with one shard. This is a documented behaviour change for
  8.11 and belongs in the release notes.
- Existing indices are never resharded. Any deployment that already has a multi-shard
  `post-importer-queue` continues to depend on the read-path hardening from
  camunda/camunda#56179.
- Adding an index to `webapps-schema` now requires adding a field to
  `NumberOfShardsPerIndex`. Without it the index still gets a correct default from its
  descriptor, but it cannot be overridden. The field-to-index-name projection is
  covered by a unit test so a field that is added but not projected fails the build.
- The `configuration` module holds the index names as string literals rather than
  referencing the descriptor `INDEX_NAME` constants, to avoid a new module dependency.
  This is the one place where drift between the two is possible.

