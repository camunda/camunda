# OpenAPI YAML Fixes - Type Alignment with Backend

This document tracks all changes made to OpenAPI YAML specifications to align them with the actual Java backend implementation.

## Goal
Fix OpenAPI specs so that auto-generated TypeScript types match the actual API responses, resolving ~200 type errors in the tasklist client.

---

## Change Log

### 1. Fixed `CamundaUserResult` in authentication.yaml

**Date:** 2026-01-28

**File:** `/Users/vinicius/repos/camunda/zeebe/gateway-protocol/src/main/proto/v2/authentication.yaml`

**Line:** 33-39

**Issue:** 
- `salesPlanType` was marked as required in the OpenAPI spec
- Java DTO has `String salesPlanType` (nullable reference type, not primitive)
- Actual API responses show it can be `""` (empty string) or null
- Frontend expects it to be optional

**Source of Truth:**
- Java DTO: `/Users/vinicius/repos/camunda/authentication/src/main/java/io/camunda/authentication/entity/CamundaUserDTO.java:23`
- Definition: `String salesPlanType` (nullable)

**Change Made:**
Removed `salesPlanType` from the `required` array in the `CamundaUserResult` schema.

**Before:**
```yaml
required:
  - tenants
  - groups
  - roles
  - salesPlanType  # ← REMOVED
  - c8Links
  - canLogout
```

**After:**
```yaml
required:
  - tenants
  - groups
  - roles
  - c8Links
  - canLogout
```

**Impact:**
- `salesPlanType` is now optional in the generated TypeScript types
- Aligns with Java backend where `String salesPlanType` can be null
- Fixes type errors in components expecting optional salesPlanType

---

## Status

- ✅ `authentication.yaml` - CamundaUserResult.salesPlanType made optional
- ⏳ Awaiting schema regeneration and type check to identify next issues

---

## Next Steps

1. Regenerate Zod schemas from updated OpenAPI specs
2. Run `npm run ts-check` in tasklist/client to identify remaining errors
3. Fix next highest-priority file based on error count
4. Repeat until all ~200 type errors are resolved

