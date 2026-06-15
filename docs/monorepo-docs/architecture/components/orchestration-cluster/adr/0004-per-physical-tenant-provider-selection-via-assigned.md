# ADR-0004: Per-Physical-Tenant Provider Selection via OC-side `assigned` Narrowing

## Status

Proposed

## Deciders

- Proposed by: Sebastian Bathke ([@megglos](https://github.com/megglos))

_This ADR is a proposal; deciders will be recorded once the team aligns._

## Context

[ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) and OC #54729
established per-physical-tenant (PT) API security chains: OC contributes a
`CamundaSecurityScopeProvider` (`PhysicalTenantScopeProvider`) that emits one
`ScopedSecurityDescriptor` per configured PT, each carrying an `AuthenticationConfiguration` that
OC assembles on the OC side from the root cluster config plus the per-tenant overlay
(`PhysicalTenantAuthConfigurations.forPhysicalTenant`). CSL builds an issuer-aware decoder / filter
chain from whatever providers that descriptor carries.

Today every PT inherits **all** cluster providers — the union of the root providers and the PT's
own overlay providers. The local smoke harness documents the resulting gap as an informational
cell:

```
default token -> /pt/tenanta  (cross-issuer; 200 today, 401 once #54730 lands)
```

A token minted by the **root/default** realm is currently **accepted** on `tenanta`'s chain,
because `tenanta` inherited the root `oidc` provider. #54730 closes this: a PT should be able to
select **which** of the cluster's providers apply to it, instead of inheriting the full set.

Acceptance criteria (post-rescope of #54730):

1. `providers.assigned` is resolved and validated per PT: the **default tenant has the implicit full
   set**; a **non-default PT must declare an explicit list of valid provider ids** — a missing or
   invalid `assigned` **fails startup** with a clear error.
2. The scope provider **narrows** each PT's merged `AuthenticationConfiguration` to its `assigned`
   providers.
3. Tests cover: default-tenant full set, a valid explicit selection, and startup failure on
   missing/invalid `assigned`.

A **hard constraint** carries over from #54729: CSL (`camunda-security-library`) must remain
**physical-tenant-agnostic** — the term "physical tenant" must not appear in it. CSL only ever
receives a finished `AuthenticationConfiguration`; it never sees `camunda.physical-tenants.*`.

The decision is **where** the selection lives and is applied. Two sub-questions:

- **Whose config schema owns `assigned`** — CSL's provider config, or OC's PT namespace?
- **Where the narrowing runs** — inside CSL at chain-build time, or in OC before the descriptor is
  built?

## Decision

`assigned` is an **OC-only** selector applied by **OC-side narrowing**. CSL is unchanged and stays
PT-agnostic.

**Config shape.** The selection is a list of provider ids under the per-tenant namespace:

```yaml
camunda:
  physical-tenants:
    tenanta:
      security:
        authentication:
          providers:
            assigned:
              - oidc        # reserved id → the root default slot (authentication.oidc.*)
              - tenanta     # a named provider (authentication.providers.oidc.tenanta)
```

- `assigned` appears **only** under `camunda.physical-tenants.<id>.*`, which is **OC's** namespace.
  It never appears under the real CSL namespace (`camunda.security.*`). It only *shape-mirrors* CSL's
  `AuthenticationConfiguration` because the PT overlay mirrors it; CSL's `OidcProvidersConfiguration`
  has no `assigned` field and never binds it. CSL's schema is therefore **not** extended.
- Ids are drawn from the union `{oidc} ∪ providers.oidc.<name>`. The reserved id **`oidc`** names the
  unnamed default slot (`camunda.security.authentication.oidc.*`), which has no map key of its own
  and so is referenced by the leaf name of the property that defines it. A named provider literally
  called `oidc` (`providers.oidc.oidc`) would collide; that pathological name is documented as
  unsupported.

**Two responsibilities, two layers.**

1. **Application (narrowing) — `authentication` module**, in
   `PhysicalTenantAuthConfigurations.forPhysicalTenant`, after the existing union-merge: read
   `...providers.assigned` via a dedicated `Binder` call (it is not a field on CSL's config), then
   filter the merged config to the selection — keep the default slot iff `oidc ∈ assigned` (else
   reset it to a content-less `OidcConfiguration`, which CSL's `flatten` ignores), and retain only
   the named providers whose ids are listed. The implicit `default` tenant (and its
   `/physical-tenants/default` alias) is **never** narrowed — it always carries the full set (AC-1).
   A non-default tenant with no `assigned` list bound is also left at the full union here; requiring
   one is the validation layer's job.

2. **Validation — `configuration` module** (fail-fast at startup, sibling to
   `PhysicalTenantOverridePolicyValidation`): a non-default PT **must** declare a non-empty
   `assigned`, and every id must resolve to a known provider (`oidc` when the root default slot has
   content, or a configured `providers.oidc.<name>`); otherwise throw
   `UnifiedConfigurationException`. A "known provider ids" notion analogous to
   `PhysicalTenantIds.known()` keeps this out of the auth merge. The merge then only ever *applies*
   an already-valid selection. *(This PR spikes the narrowing + tests; the configuration-layer
   validation follows in the same issue.)*

## Consequences

### Positive

- CSL is untouched and remains PT-agnostic — no PT/selection concept leaks into the shared library,
  honouring the #54729 constraint.
- No redundancy: OC already assembles per-PT config, so it narrows in place rather than passing the
  full set plus a filter for CSL to re-apply.
- The selector sits adjacent to the providers it selects (matching the issue's `providers.assigned`
  wording) while never touching CSL's actual config namespace.
- Clean separation of concerns: fail-fast validation in the configuration layer; pure application in
  the auth merge. The merge stays total (it only ever applies a valid selection).
- Flips the smoke harness's cross-issuer `[#54730]` cell to a pass and is mirrored by an
  `PhysicalTenantApiChainIsolationIT` case (a provider assigned to PT-A but not PT-B → PT-A's token
  rejected on PT-B).

### Negative / trade-offs

- `assigned` is nominally adjacent to the CSL-shaped subtree (`...security.authentication.providers`)
  even though CSL does not define it. Mitigated by the fact that it lives only under OC's
  `physical-tenants.<id>` namespace; the alternative of a sibling namespace was weighed and rejected
  (see Alternatives).
- The reserved `oidc` id is a small piece of magic; it collides with a (pathological) named provider
  called `oidc`. Documented as unsupported rather than guarded.
- Selection is split across two modules (validation vs application). This mirrors the existing PT
  config/auth split and is deliberate, but it does mean the "must declare a valid list" rule and the
  "apply the list" code live apart and must stay conceptually in sync.

## Alternatives Considered

1. **`assigned` as a CSL config field** (add an `assigned`/`enabled`/`active` field to CSL's provider
   config; OC passes the full union and CSL filters at chain-build time). Rejected: it puts a
   selection concept into PT-agnostic CSL that only the PT feature uses, forces "valid provider ids"
   validation into or duplicated across CSL, and is redundant — OC already assembles per-PT config,
   so passing all-then-filter is wasted work.
2. **OC-side narrowing, but `assigned` in a dedicated OC sibling namespace** (e.g.
   `camunda.physical-tenants.<id>.assigned-providers`, outside the CSL-mirrored
   `security.authentication` subtree). Rejected as the default: it removes the nominal adjacency to
   the CSL shape but diverges from the issue's `providers.assigned` wording, places the selector
   further from the providers it selects, and is less discoverable. Retained as the fallback if the
   nominal adjacency proves confusing in practice.
3. **Narrow in `PhysicalTenantScopeProvider` instead of `PhysicalTenantAuthConfigurations`.**
   Rejected: `forPhysicalTenant` already owns the per-PT merge semantics and the provider map; the
   scope provider's job is descriptor emission and the default alias. Keeping narrowing next to the
   merge keeps both concerns cohesive and unit-testable without a scope provider.
4. **Drop the default slot for all non-default PTs (no reserved id).** Rejected: it would prevent a
   PT from legitimately sharing the cluster's default-slot provider, and the reserved `oidc` id makes
   the choice explicit and uniform with named providers at negligible cost.

## References

- OC #54730 — per-tenant provider selection (`providers.assigned`); this ADR.
- OC #54729 — per-physical-tenant API security chains (PR
  [#54971](https://github.com/camunda/camunda/pull/54971)); the union-merge this narrows.
- [ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) — pre-security filter
  for PT request scoping (how the tenant id reaches the chain).
- [camunda/camunda-security-library#378](https://github.com/camunda/camunda-security-library/pull/378)
  — the `CamundaSecurityScopeProvider` SPI and per-scope issuer-aware chains (consumed at
  `0.1.0-alpha30`).
- OC #54731 — forbidden per-tenant override validation (the `PhysicalTenantOverridePolicyValidation`
  pattern the `assigned` validation extends).
