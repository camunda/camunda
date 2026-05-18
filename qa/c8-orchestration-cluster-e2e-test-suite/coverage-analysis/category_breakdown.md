# OC API v2 — Per-category breakdown

Total test declarations: **1001** across **33** entities.

This file answers, per category: **(1) Form** (the canonical sequence the tests embody), **(2) Prerequisite to create**, **(3) Observation channel split** (GET vs search), **(4) Variants with counts**, **(5) The actual tests in that category**.

## Table of contents

- [A. Entity Lifecycle (CRUD)](#a-entity-lifecycle-crud) — 333 tests
- [B. Membership/Association](#b-membershipassociation) — 135 tests
- [C. Deployment Lifecycle](#c-deployment-lifecycle) — 96 tests
- [D. Process-Instance Lifecycle & Ops](#d-process-instance-lifecycle--ops) — 113 tests
- [E. Batch-Operation Lifecycle](#e-batch-operation-lifecycle) — 32 tests
- [F. User-Task Lifecycle](#f-user-task-lifecycle) — 51 tests
- [G. Job Lifecycle & Stats](#g-job-lifecycle--stats) — 57 tests
- [H. Incident Lifecycle](#h-incident-lifecycle) — 28 tests
- [I. Decision-Instance Lifecycle](#i-decision-instance-lifecycle) — 23 tests
- [J/K/L. Observation-only](#jkl-observation-only) — 66 tests
- [M. Messaging/Signals](#m-messagingsignals) — 29 tests
- [N. Engine Evaluation](#n-engine-evaluation) — 16 tests
- [O. System/Admin](#o-systemadmin) — 22 tests

## A. Entity Lifecycle (CRUD)

**Form**: Create Entity → Get Entity (Observe Present) → Update Entity → Search Entity (Observe via list) → Delete Entity → Get Entity (Observe Absence)

**Total tests**: 333

### `authorization` — 78 tests

- **Prerequisite to create**: owner-entity-or-resource
- **Files**: `authorization/create-authorization-for-client-api.spec.ts`, `authorization/create-authorization-for-group-api.spec.ts`, `authorization/create-authorization-for-mapping-rule-api.spec.ts`, `authorization/create-authorization-for-role-api.spec.ts`, `authorization/create-authorization-for-user-api.spec.ts`, `authorization/delete-authorization-api.spec.ts`, `authorization/get-authorization-api.spec.ts`, `authorization/search-authorization-api.spec.ts`, `authorization/update-authorization-api.spec.ts`
- **Observation channel**: GET = 5, Search = 11
- **Form-step counts**: create=14, observe-present-get=1, observe-present-search=6, mutate=7, delete=4, negative-create=30, negative-get=3, negative-search=5, negative-mutate=5, negative-delete=3
- **Variants**: happy-path=25, observe-via-get=5, observe-via-search=11, pagination-sort=4, filter=5, bad-request=23, unauthorized=9, forbidden=9, not-found=8, conflict=5

| form step | variants | file:line | test name |
|--|--|--|--|
| create | happy-path | `authorization/create-authorization-for-client-api.spec.ts:47` | Create Authorization for client - Success |
| create | happy-path | `authorization/create-authorization-for-client-api.spec.ts:80` | Create Authorization for client - Multiple permissionTypes - Success |
| create | happy-path | `authorization/create-authorization-for-group-api.spec.ts:61` | Create Authorization for group - Success |
| create | happy-path | `authorization/create-authorization-for-group-api.spec.ts:91` | Create Authorization for group - Multiple permissionTypes - Success |
| create | not-found | `authorization/create-authorization-for-group-api.spec.ts:279` | Create Authorization for group - 404 Not Found - not existing ownerId |
| create | happy-path | `authorization/create-authorization-for-mapping-rule-api.spec.ts:68` | Create Authorization for Mapping Rule - Success |
| create | happy-path | `authorization/create-authorization-for-mapping-rule-api.spec.ts:98` | Create Authorization for Mapping Rule - Multiple permissionTypes - Success |
| create | not-found | `authorization/create-authorization-for-mapping-rule-api.spec.ts:294` | Create Authorization for Mapping Rule - 404 Not Found - not existing ownerId |
| create | happy-path | `authorization/create-authorization-for-role-api.spec.ts:65` | Create Authorization for role - Success |
| create | happy-path | `authorization/create-authorization-for-role-api.spec.ts:95` | Create Authorization for role - Multiple permissionTypes - Success |
| create | not-found | `authorization/create-authorization-for-role-api.spec.ts:284` | Create Authorization for role - 404 Not Found - not existing ownerId |
| create | happy-path | `authorization/create-authorization-for-user-api.spec.ts:56` | Create Authorization for user - Success |
| create | happy-path | `authorization/create-authorization-for-user-api.spec.ts:86` | Create Authorization for user - Multiple permissionTypes - Success |
| create | not-found | `authorization/create-authorization-for-user-api.spec.ts:269` | Create Authorization for user - 404 Not Found - not existing ownerId |
| observe-present-get | happy-path | `authorization/get-authorization-api.spec.ts:40` | Get existing Authorization - success |
| observe-present-search | filter, happy-path | `authorization/search-authorization-api.spec.ts:172` | Search Authorization - no filter, multiple results - 200 Success |
| observe-present-search | pagination-sort, filter, happy-path | `authorization/search-authorization-api.spec.ts:195` | Search Authorization - results sorted by resourceType and filtered by ownerId - 200 Success |
| observe-present-search | filter, happy-path | `authorization/search-authorization-api.spec.ts:232` | Search Authorization - filtered by ownerId, ownerType, and resourceType - single result - 200 Success |
| observe-present-search | filter, happy-path | `authorization/search-authorization-api.spec.ts:266` | Search Authorization - filtered by ownerId and resourceIds - multiple results - 200 Success |
| observe-present-search | happy-path | `authorization/search-authorization-api.spec.ts:298` | Search Authorization - no results - 200 Success |
| observe-present-search | pagination-sort | `authorization/search-authorization-api.spec.ts:463` | Search Authorization - Pagination 0 |
| mutate | happy-path | `authorization/update-authorization-api.spec.ts:50` | Update User Authorization - additional permissionType - success |
| mutate | happy-path | `authorization/update-authorization-api.spec.ts:137` | Update Role Authorization - change resourceId - success |
| mutate | happy-path | `authorization/update-authorization-api.spec.ts:232` | Update Group Authorization - change ownerId to another group - success |
| mutate | happy-path | `authorization/update-authorization-api.spec.ts:341` | Update Mapping Rule Authorization - change ownerId, resourceId and permissionType - success |
| mutate | happy-path | `authorization/update-authorization-api.spec.ts:463` | Update Role Authorization - same authorization - success |
| mutate | not-found | `authorization/update-authorization-api.spec.ts:702` | Update User Authorization - authorizationKey was not found - 404 Not Found |
| mutate | not-found | `authorization/update-authorization-api.spec.ts:757` | Update Role Authorization - wrong resourceId value - 404 Not Found |
| delete | happy-path | `authorization/delete-authorization-api.spec.ts:45` | Delete User Authorization - Success 204 |
| delete | happy-path | `authorization/delete-authorization-api.spec.ts:94` | Delete Role Authorization - Success 204 |
| delete | happy-path | `authorization/delete-authorization-api.spec.ts:160` | Delete Mapping Rule Authorization - Success 204 |
| delete | not-found | `authorization/delete-authorization-api.spec.ts:228` | Delete Authorization - second delete attempt - Not Found 404 |
| negative-create | conflict | `authorization/create-authorization-for-client-api.spec.ts:118` | Create Authorization for client - 409 Conflict |
| negative-create | bad-request | `authorization/create-authorization-for-client-api.spec.ts:147` | Create Authorization for client - 400 Bad Request - wrong value for ownerType |
| negative-create | bad-request | `authorization/create-authorization-for-client-api.spec.ts:173` | Create Authorization for client - 400 Bad Request - wrong value for resourceType |
| negative-create | bad-request | `authorization/create-authorization-for-client-api.spec.ts:199` | Create Authorization for client - 400 Invalid Argument - invalid resourceId |
| negative-create | unauthorized | `authorization/create-authorization-for-client-api.spec.ts:227` | Create Authorization for client - 401 Unauthorized |
| negative-create | forbidden | `authorization/create-authorization-for-client-api.spec.ts:283` | Create Authorization for client - 403 Forbidden |
| negative-create | conflict | `authorization/create-authorization-for-group-api.spec.ts:123` | Create Authorization for group - 409 Conflict |
| negative-create | bad-request | `authorization/create-authorization-for-group-api.spec.ts:174` | Create Authorization for group - 400 Bad Request - wrong value for ownerType |
| negative-create | bad-request | `authorization/create-authorization-for-group-api.spec.ts:200` | Create Authorization for group - 400 Bad Request - wrong value for resourceType |
| negative-create | bad-request | `authorization/create-authorization-for-group-api.spec.ts:226` | Create Authorization for group - 400 Invalid Argument - invalid resourceId |
| negative-create | unauthorized | `authorization/create-authorization-for-group-api.spec.ts:254` | Create Authorization for group - 401 Unauthorized |
| negative-create | forbidden | `authorization/create-authorization-for-group-api.spec.ts:340` | Create Authorization for group - 403 Forbidden |
| negative-create | conflict | `authorization/create-authorization-for-mapping-rule-api.spec.ts:130` | Create Authorization for Mapping Rule - 409 Conflict |
| negative-create | bad-request | `authorization/create-authorization-for-mapping-rule-api.spec.ts:189` | Create Authorization for Mapping Rule - 400 Bad Request - wrong value for ownerType |
| negative-create | bad-request | `authorization/create-authorization-for-mapping-rule-api.spec.ts:215` | Create Authorization for Mapping Rule - 400 Bad Request - wrong value for resourceType |
| negative-create | bad-request | `authorization/create-authorization-for-mapping-rule-api.spec.ts:241` | Create Authorization for Mapping Rule - 400 Invalid Argument - invalid resourceId |
| negative-create | unauthorized | `authorization/create-authorization-for-mapping-rule-api.spec.ts:269` | Create Authorization for Mapping Rule - 401 Unauthorized |
| negative-create | forbidden | `authorization/create-authorization-for-mapping-rule-api.spec.ts:352` | Create Authorization for Mapping Rule - 403 Forbidden |
| negative-create | conflict | `authorization/create-authorization-for-role-api.spec.ts:127` | Create Authorization for role - 409 Conflict |
| negative-create | bad-request | `authorization/create-authorization-for-role-api.spec.ts:179` | Create Authorization for role - 400 Bad Request - wrong value for ownerType |
| negative-create | bad-request | `authorization/create-authorization-for-role-api.spec.ts:205` | Create Authorization for role - 400 Bad Request - wrong value for resourceType |
| negative-create | bad-request | `authorization/create-authorization-for-role-api.spec.ts:231` | Create Authorization for role - 400 Invalid Argument - invalid resourceId |
| negative-create | unauthorized | `authorization/create-authorization-for-role-api.spec.ts:259` | Create Authorization for role - 401 Unauthorized |
| negative-create | forbidden | `authorization/create-authorization-for-role-api.spec.ts:346` | Create Authorization for role - 403 Forbidden |
| negative-create | conflict | `authorization/create-authorization-for-user-api.spec.ts:118` | Create Authorization for user - 409 Conflict |
| negative-create | bad-request | `authorization/create-authorization-for-user-api.spec.ts:164` | Create Authorization for user - 400 Bad Request - wrong value for ownerType |
| negative-create | bad-request | `authorization/create-authorization-for-user-api.spec.ts:190` | Create Authorization for user - 400 Bad Request - wrong value for resourceType |
| negative-create | bad-request | `authorization/create-authorization-for-user-api.spec.ts:216` | Create Authorization for user - 400 Invalid Argument - invalid resourceId |
| negative-create | unauthorized | `authorization/create-authorization-for-user-api.spec.ts:244` | Create Authorization for user - 401 Unauthorized |
| negative-create | forbidden | `authorization/create-authorization-for-user-api.spec.ts:334` | Create Authorization for user - 403 Forbidden |
| negative-get | not-found | `authorization/get-authorization-api.spec.ts:100` | Get existing Authorization - not found |
| negative-get | unauthorized | `authorization/get-authorization-api.spec.ts:119` | Get existing Authorization - unauthorized |
| negative-get | forbidden | `authorization/get-authorization-api.spec.ts:164` | Get existing Authorization - 403 Forbidden |
| negative-search | bad-request, pagination-sort | `authorization/search-authorization-api.spec.ts:325` | Search Authorization - invalid sort field - 400 Bad Request |
| negative-search | bad-request, filter | `authorization/search-authorization-api.spec.ts:348` | Search Authorization - invalid filter field - 400 Bad Request |
| negative-search | unauthorized | `authorization/search-authorization-api.spec.ts:368` | Search Authorization - Unauthorized - 401 Unauthorized |
| negative-search | forbidden, bad-request, observe-via-get, happy-path | `authorization/search-authorization-api.spec.ts:380` | Search Authorization - Returns empty results for user without permission - 200 Success |
| negative-search | bad-request, pagination-sort | `authorization/search-authorization-api.spec.ts:445` | Search Authorization - Negative pagination values - 400 Bad Request |
| negative-mutate | bad-request | `authorization/update-authorization-api.spec.ts:561` | Update User Authorization - empty requestBody - 400 bad request |
| negative-mutate | unauthorized | `authorization/update-authorization-api.spec.ts:647` | Update User Authorization - Unauthorized |
| negative-mutate | bad-request | `authorization/update-authorization-api.spec.ts:855` | Update User Authorization - wrong resourceType - 400 Bad Request |
| negative-mutate | bad-request | `authorization/update-authorization-api.spec.ts:911` | Update User Authorization - empty permissionType - 400 Bad Request |
| negative-mutate | forbidden | `authorization/update-authorization-api.spec.ts:964` | Update User Authorization - 403 Forbidden |
| negative-delete | unauthorized | `authorization/delete-authorization-api.spec.ts:292` | Delete Authorization - Unauthorized Request - 401 |
| negative-delete | bad-request | `authorization/delete-authorization-api.spec.ts:306` | Delete Authorization - Invalid Authorization Key - Bad Request 400 |
| negative-delete | forbidden | `authorization/delete-authorization-api.spec.ts:322` | Delete Authorization - 403 Forbidden |

### `cluster-variables` — 67 tests

- **Prerequisite to create**: none
- **Files**: `cluster-variables/cluster-variable-global-api-tests.spec.ts`, `cluster-variables/cluster-variable-search-api.spec.ts`, `cluster-variables/cluster-variable-tenant-api-tests.spec.ts`, `cluster-variables/cluster-variable-update-api-tests.spec.ts`
- **Observation channel**: GET = 7, Search = 16
- **Form-step counts**: create=3, observe-present-get=2, observe-present-search=10, mutate=22, delete=4, negative-create=7, negative-get=4, negative-search=4, negative-mutate=9, negative-delete=2
- **Variants**: happy-path=1, observe-via-get=7, observe-via-search=16, pagination-sort=4, filter=6, bad-request=15, unauthorized=9, not-found=8, unlabeled=23

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `cluster-variables/cluster-variable-global-api-tests.spec.ts:46` | Create Global Cluster Variable |
| create | observe-via-search | `cluster-variables/cluster-variable-search-api.spec.ts:376` | Search Cluster Variables Finds Created Variables |
| create | unlabeled | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:55` | Create Tenant Cluster Variable |
| observe-present-get | — | `cluster-variables/cluster-variable-global-api-tests.spec.ts:100` | Get Global Cluster Variable |
| observe-present-get | — | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:142` | Get Tenant Cluster Variable |
| observe-present-search | happy-path | `cluster-variables/cluster-variable-search-api.spec.ts:80` | Search Cluster Variables Success |
| observe-present-search | filter | `cluster-variables/cluster-variable-search-api.spec.ts:103` | Search Cluster Variables With Name Filter |
| observe-present-search | filter | `cluster-variables/cluster-variable-search-api.spec.ts:134` | Search Cluster Variables With Scope Filter GLOBAL |
| observe-present-search | filter | `cluster-variables/cluster-variable-search-api.spec.ts:164` | Search Cluster Variables With Scope Filter TENANT |
| observe-present-search | filter | `cluster-variables/cluster-variable-search-api.spec.ts:195` | Search Cluster Variables With TenantId Filter |
| observe-present-search | filter | `cluster-variables/cluster-variable-search-api.spec.ts:225` | Search Cluster Variables With Multiple Filters |
| observe-present-search | pagination-sort | `cluster-variables/cluster-variable-search-api.spec.ts:257` | Search Cluster Variables Pagination Limit 1 |
| observe-present-search | pagination-sort | `cluster-variables/cluster-variable-search-api.spec.ts:283` | Search Cluster Variables Sort by Name ASC |
| observe-present-search | pagination-sort | `cluster-variables/cluster-variable-search-api.spec.ts:315` | Search Cluster Variables Sort by Name DESC |
| observe-present-search | — | `cluster-variables/cluster-variable-search-api.spec.ts:347` | Search Cluster Variables With truncateValues=false |
| mutate | observe-via-search | `cluster-variables/cluster-variable-search-api.spec.ts:507` | Search Finds Updated Cluster Variable Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:42` | Update Global Cluster Variable With Object Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:60` | Update Global Cluster Variable With String Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:78` | Update Global Cluster Variable With Number Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:96` | Update Global Cluster Variable With Boolean True Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:114` | Update Global Cluster Variable With Boolean False Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:132` | Update Global Cluster Variable With Array Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:148` | Update Global Cluster Variable With Nested Object |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:208` | Update Global Cluster Variable Multiple Times |
| mutate | not-found | `cluster-variables/cluster-variable-update-api-tests.spec.ts:252` | Update Global Cluster Variable Not Found |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:282` | Update Global Cluster Variable Verify Response Structure |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:329` | Update Global Cluster Variable Immediately Retrievable |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:382` | Update Tenant Cluster Variable With Object Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:410` | Update Tenant Cluster Variable With String Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:438` | Update Tenant Cluster Variable With Number Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:466` | Update Tenant Cluster Variable With Boolean Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:494` | Update Tenant Cluster Variable With Array Value |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:520` | Update Tenant Cluster Variable With Complex Nested Object |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:614` | Update Tenant Cluster Variable Multiple Times |
| mutate | not-found | `cluster-variables/cluster-variable-update-api-tests.spec.ts:681` | Update Tenant Cluster Variable Not Found |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:738` | Update Tenant Cluster Variable Verify Response Structure |
| mutate | unlabeled | `cluster-variables/cluster-variable-update-api-tests.spec.ts:799` | Update Tenant Cluster Variable Immediately Retrievable |
| delete | unlabeled | `cluster-variables/cluster-variable-global-api-tests.spec.ts:151` | Delete Global Cluster Variable |
| delete | not-found | `cluster-variables/cluster-variable-global-api-tests.spec.ts:186` | Delete Global Cluster Variable Not Found |
| delete | unlabeled | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:212` | Delete Tenant Cluster Variable |
| delete | not-found | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:265` | Delete Tenant Cluster Variable Not Found |
| negative-create | unauthorized | `cluster-variables/cluster-variable-global-api-tests.spec.ts:70` | Create Global Cluster Variable Unauthorized |
| negative-create | bad-request | `cluster-variables/cluster-variable-global-api-tests.spec.ts:78` | Create Global Cluster Variable Missing Name Invalid Body 400 |
| negative-create | bad-request | `cluster-variables/cluster-variable-global-api-tests.spec.ts:89` | Create Global Cluster Variable Missing Value Invalid Body 400 |
| negative-create | unauthorized | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:85` | Create Tenant Cluster Variable Unauthorized |
| negative-create | bad-request | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:97` | Create Tenant Cluster Variable Missing Name Invalid Body 400 |
| negative-create | bad-request | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:112` | Create Tenant Cluster Variable Missing Value Invalid Body 400 |
| negative-create | not-found, bad-request | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:127` | Create Tenant Cluster Variable Invalid Tenant Not Found |
| negative-get | unauthorized | `cluster-variables/cluster-variable-global-api-tests.spec.ts:127` | Get Global Cluster Variable Unauthorized |
| negative-get | not-found | `cluster-variables/cluster-variable-global-api-tests.spec.ts:141` | Get Global Cluster Variable Not Found |
| negative-get | unauthorized | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:175` | Get Tenant Cluster Variable Unauthorized |
| negative-get | not-found | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:198` | Get Tenant Cluster Variable Not Found |
| negative-search | unauthorized | `cluster-variables/cluster-variable-search-api.spec.ts:422` | Search Cluster Variables Unauthorized |
| negative-search | bad-request, filter | `cluster-variables/cluster-variable-search-api.spec.ts:435` | Search Cluster Variables Invalid Filter |
| negative-search | bad-request, pagination-sort | `cluster-variables/cluster-variable-search-api.spec.ts:453` | Search Cluster Variables Invalid Sort Field |
| negative-search | bad-request, observe-via-get | `cluster-variables/cluster-variable-search-api.spec.ts:475` | Search Cluster Variables By Non-Existent Name Returns Empty |
| negative-mutate | bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:174` | Update Global Cluster Variable With Empty Object |
| negative-mutate | bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:192` | Update Global Cluster Variable With Empty Array |
| negative-mutate | unauthorized | `cluster-variables/cluster-variable-update-api-tests.spec.ts:237` | Update Global Cluster Variable Unauthorized |
| negative-mutate | bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:265` | Update Global Cluster Variable Missing Value Field Invalid Body 400 |
| negative-mutate | bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:560` | Update Tenant Cluster Variable With Empty Object |
| negative-mutate | bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:588` | Update Tenant Cluster Variable With Empty Array |
| negative-mutate | unauthorized | `cluster-variables/cluster-variable-update-api-tests.spec.ts:657` | Update Tenant Cluster Variable Unauthorized |
| negative-mutate | bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:696` | Update Tenant Cluster Variable Missing Value Field Invalid Body 400 |
| negative-mutate | not-found, bad-request | `cluster-variables/cluster-variable-update-api-tests.spec.ts:722` | Update Tenant Cluster Variable Invalid Tenant Not Found |
| negative-delete | unauthorized | `cluster-variables/cluster-variable-global-api-tests.spec.ts:172` | Delete Global Cluster Variable Unauthorized |
| negative-delete | unauthorized | `cluster-variables/cluster-variable-tenant-api-tests.spec.ts:242` | Delete Tenant Cluster Variable Unauthorized |

### `tenant` — 37 tests

- **Prerequisite to create**: none
- **Files**: `tenant/tenant-api-tests.spec.ts`, `tenant/tenant-role-api-tests.spec.ts`
- **Observation channel**: GET = 4, Search = 10
- **Form-step counts**: create=1, observe-present-get=1, observe-present-search=5, mutate=6, delete=5, negative-create=6, negative-get=2, negative-search=5, negative-mutate=4, negative-delete=2
- **Variants**: happy-path=3, observe-via-get=4, observe-via-search=10, bad-request=8, unauthorized=8, not-found=8, conflict=2, unlabeled=3

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `tenant/tenant-api-tests.spec.ts:41` | Create Tenant |
| observe-present-get | — | `tenant/tenant-api-tests.spec.ts:116` | Get Tenant |
| observe-present-search | — | `tenant/tenant-api-tests.spec.ts:306` | Search Tenants |
| observe-present-search | — | `tenant/tenant-api-tests.spec.ts:342` | Search Tenants By Name |
| observe-present-search | — | `tenant/tenant-api-tests.spec.ts:381` | Search Tenants By Tenant Id |
| observe-present-search | — | `tenant/tenant-api-tests.spec.ts:420` | Search Tenants By Multiple Fields |
| observe-present-search | — | `tenant/tenant-role-api-tests.spec.ts:231` | Search Tenant Roles |
| mutate | unlabeled | `tenant/tenant-api-tests.spec.ts:161` | Update Tenant |
| mutate | happy-path | `tenant/tenant-api-tests.spec.ts:212` | Update Tenant Missing Description success 200 |
| mutate | not-found | `tenant/tenant-api-tests.spec.ts:250` | Update Tenant Not Found |
| mutate | happy-path | `tenant/tenant-role-api-tests.spec.ts:43` | Assign Role To Tenant - Success |
| mutate | not-found | `tenant/tenant-role-api-tests.spec.ts:61` | Assign Role To Tenant Non Existent Role - Not Found |
| mutate | not-found | `tenant/tenant-role-api-tests.spec.ts:81` | Assign Role To Tenant Non Existent Tenant - Not Found |
| delete | unlabeled | `tenant/tenant-api-tests.spec.ts:262` | Delete tenant |
| delete | not-found | `tenant/tenant-api-tests.spec.ts:295` | Delete tenant Not Found |
| delete | happy-path | `tenant/tenant-role-api-tests.spec.ts:136` | Unassign Role From Tenant - Success |
| delete | not-found | `tenant/tenant-role-api-tests.spec.ts:193` | Unassign Role From Tenant Non Existent Role - Not Found |
| delete | not-found | `tenant/tenant-role-api-tests.spec.ts:212` | Unassign Role From Tenant Non Existent Tenant - Not Found |
| negative-create | unauthorized | `tenant/tenant-api-tests.spec.ts:63` | Create Tenant Unauthorized |
| negative-create | bad-request | `tenant/tenant-api-tests.spec.ts:71` | Create Tenant Missing Name Invalid Body 400 |
| negative-create | bad-request | `tenant/tenant-api-tests.spec.ts:80` | Create Tenant Empty Name Invalid Body 400 |
| negative-create | bad-request | `tenant/tenant-api-tests.spec.ts:89` | Create Tenant Missing Tenant Id Invalid Body 400 |
| negative-create | conflict | `tenant/tenant-api-tests.spec.ts:103` | Create Tenant Conflict |
| negative-create | conflict | `tenant/tenant-role-api-tests.spec.ts:118` | Assign Already Added Role To Tenant - Conflict |
| negative-get | unauthorized | `tenant/tenant-api-tests.spec.ts:142` | Get Tenant Unauthorized |
| negative-get | not-found | `tenant/tenant-api-tests.spec.ts:150` | Get Tenant Not Found |
| negative-search | bad-request | `tenant/tenant-api-tests.spec.ts:458` | Search Tenants By Invalid Id |
| negative-search | unauthorized | `tenant/tenant-api-tests.spec.ts:485` | Search Tenants Unauthorized |
| negative-search | bad-request, observe-via-get | `tenant/tenant-role-api-tests.spec.ts:262` | Search Tenant Roles Tenant With No Assignments Returns Empty |
| negative-search | unauthorized | `tenant/tenant-role-api-tests.spec.ts:289` | Search Tenant Roles - Unauthorized |
| negative-search | not-found, bad-request | `tenant/tenant-role-api-tests.spec.ts:298` | Search Tenant Roles Tenant - Not Found (empty response) |
| negative-mutate | bad-request | `tenant/tenant-api-tests.spec.ts:188` | Update Tenant Empty Name Invalid Body 400 |
| negative-mutate | bad-request | `tenant/tenant-api-tests.spec.ts:200` | Update Tenant Missing Name Invalid Body 400 |
| negative-mutate | unauthorized | `tenant/tenant-api-tests.spec.ts:239` | Update Tenant Unauthorized |
| negative-mutate | unauthorized | `tenant/tenant-role-api-tests.spec.ts:101` | Assign Role To Tenant - Unauthorized |
| negative-delete | unauthorized | `tenant/tenant-api-tests.spec.ts:287` | Delete tenant Unauthorized |
| negative-delete | unauthorized | `tenant/tenant-role-api-tests.spec.ts:179` | Unassign Role From Tenant - Unauthorized |

### `user` — 30 tests

- **Prerequisite to create**: none
- **Files**: `user-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 7
- **Form-step counts**: create=4, observe-present-get=1, observe-present-search=5, mutate=5, delete=2, negative-create=7, negative-get=2, negative-search=2, negative-mutate=1, negative-delete=1
- **Variants**: happy-path=7, observe-via-get=3, observe-via-search=7, bad-request=6, unauthorized=5, not-found=3, conflict=1, unlabeled=3

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `user-api-tests.spec.ts:56` | Create User |
| create | happy-path | `user-api-tests.spec.ts:81` | Create User Existing Email Success |
| create | happy-path | `user-api-tests.spec.ts:197` | Create User Missing Email Success |
| create | happy-path | `user-api-tests.spec.ts:228` | Create User Missing Name Success |
| observe-present-get | — | `user-api-tests.spec.ts:329` | Get User |
| observe-present-search | — | `user-api-tests.spec.ts:561` | Search Users |
| observe-present-search | — | `user-api-tests.spec.ts:605` | Search Users By Username |
| observe-present-search | — | `user-api-tests.spec.ts:641` | Search Users By Name |
| observe-present-search | — | `user-api-tests.spec.ts:676` | Search Users By Email |
| observe-present-search | — | `user-api-tests.spec.ts:711` | Search Users By Multiple Fields |
| mutate | unlabeled | `user-api-tests.spec.ts:374` | Update User |
| mutate | happy-path | `user-api-tests.spec.ts:401` | Update User Missing Name Success |
| mutate | happy-path | `user-api-tests.spec.ts:431` | Update User Missing Email Success |
| mutate | happy-path | `user-api-tests.spec.ts:461` | Update User With Password Success |
| mutate | not-found | `user-api-tests.spec.ts:505` | Update User Not Found |
| delete | unlabeled | `user-api-tests.spec.ts:517` | Delete User |
| delete | not-found | `user-api-tests.spec.ts:550` | Delete User Not Found |
| negative-create | unauthorized | `user-api-tests.spec.ts:163` | Create User Unauthorized |
| negative-create | bad-request | `user-api-tests.spec.ts:171` | Create User Missing Username Invalid Body 400 |
| negative-create | bad-request | `user-api-tests.spec.ts:184` | Create User Missing Password Invalid Body 400 |
| negative-create | bad-request, happy-path | `user-api-tests.spec.ts:259` | Create User Empty Name Success |
| negative-create | bad-request | `user-api-tests.spec.ts:291` | Create User Empty Username Invalid Body 400 |
| negative-create | bad-request | `user-api-tests.spec.ts:300` | Create User Invalid Email Invalid Body 400 |
| negative-create | conflict | `user-api-tests.spec.ts:315` | Create User Conflict |
| negative-get | unauthorized | `user-api-tests.spec.ts:355` | Get User Unauthorized |
| negative-get | not-found | `user-api-tests.spec.ts:363` | Get User Not Found |
| negative-search | bad-request | `user-api-tests.spec.ts:746` | Search Users By Invalid User Name |
| negative-search | unauthorized | `user-api-tests.spec.ts:772` | Search Users Unauthorized |
| negative-mutate | unauthorized | `user-api-tests.spec.ts:494` | Update User Unauthorized |
| negative-delete | unauthorized | `user-api-tests.spec.ts:542` | Delete User Unauthorized |

### `global-task-listener` — 30 tests

- **Prerequisite to create**: none
- **Files**: `global-task-listener/global-task-listener-create-api-tests.spec.ts`, `global-task-listener/global-task-listener-delete-api-tests.spec.ts`, `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts`, `global-task-listener/global-task-listener-update-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 8
- **Form-step counts**: create=3, observe-present-search=5, mutate=3, delete=2, observe-absence=1, negative-create=8, negative-search=3, negative-mutate=4, negative-delete=1
- **Variants**: happy-path=6, observe-via-get=3, observe-via-search=8, observe-absence=1, pagination-sort=7, bad-request=9, unauthorized=4, forbidden=2, not-found=3, conflict=2

| form step | variants | file:line | test name |
|--|--|--|--|
| create | happy-path | `global-task-listener/global-task-listener-create-api-tests.spec.ts:50` | Create Global Task Listener - success with required fields |
| create | happy-path | `global-task-listener/global-task-listener-create-api-tests.spec.ts:76` | Create Global Task Listener - success with all optional fields |
| create | happy-path | `global-task-listener/global-task-listener-create-api-tests.spec.ts:302` | Create Global Task Listener - 201 Success - admin user with full permissions |
| observe-present-search | pagination-sort | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:75` | Search Global Task Listeners - sort by priority ASC |
| observe-present-search | pagination-sort | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:107` | Search Global Task Listeners - sort by priority DESC |
| observe-present-search | pagination-sort | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:139` | Search Global Task Listeners - sort by id ASC |
| observe-present-search | pagination-sort | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:168` | Search Global Task Listeners - sort by id DESC |
| observe-present-search | pagination-sort | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:196` | Search Global Task Listeners - sort by priority ASC then id ASC (compound sort) |
| mutate | happy-path | `global-task-listener/global-task-listener-update-api-tests.spec.ts:31` | Update Global Task Listener - success updating all fields |
| mutate | happy-path | `global-task-listener/global-task-listener-update-api-tests.spec.ts:72` | Update Global Task Listener - success updating required fields only |
| mutate | not-found | `global-task-listener/global-task-listener-update-api-tests.spec.ts:128` | Update Global Task Listener - not found |
| delete | happy-path | `global-task-listener/global-task-listener-delete-api-tests.spec.ts:29` | Delete Global Task Listener - success |
| delete | not-found | `global-task-listener/global-task-listener-delete-api-tests.spec.ts:61` | Delete Global Task Listener - not found |
| observe-absence | not-found, conflict, observe-absence | `global-task-listener/global-task-listener-delete-api-tests.spec.ts:70` | Delete Global Task Listener - already deleted returns not found |
| negative-create | unauthorized | `global-task-listener/global-task-listener-create-api-tests.spec.ts:110` | Create Global Task Listener - unauthorized |
| negative-create | bad-request | `global-task-listener/global-task-listener-create-api-tests.spec.ts:121` | Create Global Task Listener - missing required id field |
| negative-create | bad-request | `global-task-listener/global-task-listener-create-api-tests.spec.ts:138` | Create Global Task Listener - missing required type field |
| negative-create | bad-request | `global-task-listener/global-task-listener-create-api-tests.spec.ts:155` | Create Global Task Listener - missing required eventTypes field |
| negative-create | bad-request | `global-task-listener/global-task-listener-create-api-tests.spec.ts:172` | Create Global Task Listener - invalid eventType value |
| negative-create | conflict | `global-task-listener/global-task-listener-create-api-tests.spec.ts:188` | Create Global Task Listener - duplicate id conflict |
| negative-create | forbidden | `global-task-listener/global-task-listener-create-api-tests.spec.ts:262` | Create Global Task Listener - 403 Forbidden - user with no GLOBAL_LISTENER permissions |
| negative-create | forbidden | `global-task-listener/global-task-listener-create-api-tests.spec.ts:282` | Create Global Task Listener - 403 Forbidden - user with READ-only GLOBAL_LISTENER permission |
| negative-search | bad-request, pagination-sort, observe-via-get | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:232` | Search Global Task Listeners - invalid sort field returns 400 |
| negative-search | bad-request, pagination-sort, observe-via-get | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:245` | Search Global Task Listeners - missing sort field returns 400 |
| negative-search | unauthorized | `global-task-listener/global-task-listener-search-sort-api-tests.spec.ts:258` | Search Global Task Listeners - unauthorized |
| negative-mutate | unauthorized | `global-task-listener/global-task-listener-update-api-tests.spec.ts:110` | Update Global Task Listener - unauthorized |
| negative-mutate | bad-request | `global-task-listener/global-task-listener-update-api-tests.spec.ts:143` | Update Global Task Listener - missing required type field |
| negative-mutate | bad-request | `global-task-listener/global-task-listener-update-api-tests.spec.ts:160` | Update Global Task Listener - missing required eventTypes field |
| negative-mutate | bad-request | `global-task-listener/global-task-listener-update-api-tests.spec.ts:177` | Update Global Task Listener - invalid eventType value |
| negative-delete | unauthorized | `global-task-listener/global-task-listener-delete-api-tests.spec.ts:49` | Delete Global Task Listener - unauthorized |

### `mapping-rule` — 27 tests

- **Prerequisite to create**: none
- **Files**: `mapping-rule-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 8
- **Form-step counts**: create=1, observe-present-get=1, observe-present-search=7, mutate=2, delete=2, negative-create=6, negative-get=2, negative-search=1, negative-mutate=4, negative-delete=1
- **Variants**: observe-via-get=3, observe-via-search=8, bad-request=7, unauthorized=5, not-found=3, conflict=1, unlabeled=3

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `mapping-rule-api-tests.spec.ts:43` | Create Mapping Rule |
| observe-present-get | — | `mapping-rule-api-tests.spec.ts:151` | Get Mapping Rule |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:203` | Search Mapping Rules |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:248` | Search Mapping Rules By Id |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:281` | Search Mapping Rules By Name |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:315` | Search Mapping Rules By Claim Value |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:349` | Search Mapping Rules By Claim Name |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:384` | Search Mapping Rules No Matching Item |
| observe-present-search | — | `mapping-rule-api-tests.spec.ts:409` | Search Mapping Rules By Multiple Fields |
| mutate | unlabeled | `mapping-rule-api-tests.spec.ts:455` | Update Mapping Rule |
| mutate | not-found | `mapping-rule-api-tests.spec.ts:566` | Update Mapping Rule Not Found |
| delete | unlabeled | `mapping-rule-api-tests.spec.ts:602` | Delete Mapping Rule |
| delete | not-found | `mapping-rule-api-tests.spec.ts:629` | Delete Mapping Rule Not Found |
| negative-create | unauthorized | `mapping-rule-api-tests.spec.ts:65` | Create Mapping Rule Unauthorized |
| negative-create | bad-request | `mapping-rule-api-tests.spec.ts:76` | Create Mapping Rule With Only Claim Name Invalid Body 400 |
| negative-create | bad-request | `mapping-rule-api-tests.spec.ts:91` | Create Mapping Rule With Only Claim Value Invalid Body 400 |
| negative-create | bad-request | `mapping-rule-api-tests.spec.ts:106` | Create Mapping Rule With Only Name Invalid Body 400 |
| negative-create | bad-request | `mapping-rule-api-tests.spec.ts:121` | Create Mapping Rule With Only Mapping Rule Id Invalid Body 400 |
| negative-create | conflict | `mapping-rule-api-tests.spec.ts:136` | Create Mapping Rule With Same Id Conflict |
| negative-get | not-found | `mapping-rule-api-tests.spec.ts:178` | Get Mapping Rule Not Found |
| negative-get | unauthorized | `mapping-rule-api-tests.spec.ts:190` | Get Mapping Rule Unauthorized |
| negative-search | unauthorized | `mapping-rule-api-tests.spec.ts:447` | Search Mapping Rules Unauthorized |
| negative-mutate | bad-request | `mapping-rule-api-tests.spec.ts:489` | Update Mapping Rule With Only Claim Name Invalid Body 400 |
| negative-mutate | bad-request | `mapping-rule-api-tests.spec.ts:515` | Update Mapping Rule With Only Claim Value Invalid Body 400 |
| negative-mutate | bad-request | `mapping-rule-api-tests.spec.ts:540` | Update Mapping Rule With Only Name Invalid Body 400 |
| negative-mutate | unauthorized | `mapping-rule-api-tests.spec.ts:586` | Update Mapping Rule Unauthorized |
| negative-delete | unauthorized | `mapping-rule-api-tests.spec.ts:641` | Delete Mapping Rule Unauthorized |

### `role` — 24 tests

- **Prerequisite to create**: none
- **Files**: `role/role-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 6
- **Form-step counts**: create=1, observe-present-get=1, observe-present-search=4, mutate=3, delete=2, negative-create=5, negative-get=2, negative-search=2, negative-mutate=3, negative-delete=1
- **Variants**: happy-path=1, observe-via-get=3, observe-via-search=6, bad-request=6, unauthorized=5, not-found=3, conflict=1, unlabeled=3

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `role/role-api-tests.spec.ts:53` | Create Role |
| observe-present-get | — | `role/role-api-tests.spec.ts:126` | Get Role |
| observe-present-search | — | `role/role-api-tests.spec.ts:313` | Search Roles |
| observe-present-search | — | `role/role-api-tests.spec.ts:358` | Search Roles By Name |
| observe-present-search | — | `role/role-api-tests.spec.ts:396` | Search Roles By Role Id |
| observe-present-search | — | `role/role-api-tests.spec.ts:435` | Search Roles By Multiple Fields |
| mutate | unlabeled | `role/role-api-tests.spec.ts:168` | Update Role |
| mutate | happy-path | `role/role-api-tests.spec.ts:219` | Update Role Missing Description Success 200 |
| mutate | not-found | `role/role-api-tests.spec.ts:257` | Update Role Not Found |
| delete | unlabeled | `role/role-api-tests.spec.ts:269` | Delete Role |
| delete | not-found | `role/role-api-tests.spec.ts:302` | Delete Role Not Found |
| negative-create | unauthorized | `role/role-api-tests.spec.ts:78` | Create Role Unauthorized |
| negative-create | bad-request | `role/role-api-tests.spec.ts:86` | Create Role Missing Name Invalid Body 400 |
| negative-create | bad-request | `role/role-api-tests.spec.ts:95` | Create Role Empty Name Invalid Body 400 |
| negative-create | bad-request | `role/role-api-tests.spec.ts:104` | Create Role Missing Role Id Invalid Body 400 |
| negative-create | conflict | `role/role-api-tests.spec.ts:113` | Create Role Conflict |
| negative-get | unauthorized | `role/role-api-tests.spec.ts:152` | Get Role Unauthorized |
| negative-get | not-found | `role/role-api-tests.spec.ts:160` | Get Role Not Found |
| negative-search | bad-request | `role/role-api-tests.spec.ts:473` | Search Roles By Invalid Id |
| negative-search | unauthorized | `role/role-api-tests.spec.ts:500` | Search Roles Unauthorized |
| negative-mutate | bad-request | `role/role-api-tests.spec.ts:195` | Update Role Empty Name Invalid Body 400 |
| negative-mutate | bad-request | `role/role-api-tests.spec.ts:207` | Update Role Missing Name Invalid Body 400 |
| negative-mutate | unauthorized | `role/role-api-tests.spec.ts:246` | Update Role Unauthorized |
| negative-delete | unauthorized | `role/role-api-tests.spec.ts:294` | Delete Role Unauthorized |

### `document` — 23 tests

- **Prerequisite to create**: none
- **Files**: `document-api-tests.spec.ts`
- **Observation channel**: GET = 4, Search = 0
- **Form-step counts**: create=7, observe-present-get=2, delete=2, negative-create=9, negative-get=2, negative-delete=1
- **Variants**: observe-via-get=4, bad-request=6, unauthorized=5, not-found=3, unlabeled=7

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `document-api-tests.spec.ts:146` | Create Document |
| create | unlabeled | `document-api-tests.spec.ts:168` | Create Document With Query Parameters |
| create | unlabeled | `document-api-tests.spec.ts:197` | Create Document With Metadata |
| create | unlabeled | `document-api-tests.spec.ts:337` | Create Multiple Documents |
| create | unlabeled | `document-api-tests.spec.ts:433` | Create Document Link 403 For In-Memory Storage |
| create | unlabeled | `document-api-tests.spec.ts:455` | Create Document Link Without Hash 400 |
| create | not-found | `document-api-tests.spec.ts:474` | Create Document Link Not Found 404 |
| observe-present-get | — | `document-api-tests.spec.ts:229` | Get Document |
| observe-present-get | — | `document-api-tests.spec.ts:248` | Get Document Without Hash 400 |
| delete | not-found | `document-api-tests.spec.ts:295` | Delete Document Not Found 404 |
| delete | unlabeled | `document-api-tests.spec.ts:308` | Delete Document |
| negative-create | unauthorized | `document-api-tests.spec.ts:82` | Create Document Unauthorized 401 |
| negative-create | bad-request | `document-api-tests.spec.ts:91` | Create Document Invalid Header 415 |
| negative-create | bad-request | `document-api-tests.spec.ts:100` | Create Document Invalid Body 400 |
| negative-create | bad-request | `document-api-tests.spec.ts:109` | Create Document Invalid Store 400 |
| negative-create | bad-request | `document-api-tests.spec.ts:126` | Create Document Invalid processDefinitionId 400 |
| negative-create | unauthorized | `document-api-tests.spec.ts:394` | Create Multiple Documents Unauthorized 401 |
| negative-create | bad-request | `document-api-tests.spec.ts:409` | Create Multiple Documents Invalid Header 415 |
| negative-create | bad-request | `document-api-tests.spec.ts:424` | Create Multiple Documents Invalid Body 400 |
| negative-create | unauthorized | `document-api-tests.spec.ts:488` | Create Document Link Unauthorized 401 |
| negative-get | not-found | `document-api-tests.spec.ts:264` | Get Document Not Found 404 |
| negative-get | unauthorized | `document-api-tests.spec.ts:275` | Get Document Unauthorized 401 |
| negative-delete | unauthorized | `document-api-tests.spec.ts:285` | Delete Document Unauthorized 401 |

### `group` — 17 tests

- **Prerequisite to create**: none
- **Files**: `group/group-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 4
- **Form-step counts**: create=1, observe-present-get=1, observe-present-search=2, mutate=2, delete=2, negative-create=2, negative-get=2, negative-search=2, negative-mutate=2, negative-delete=1
- **Variants**: observe-via-get=3, observe-via-search=4, bad-request=3, unauthorized=5, not-found=3, unlabeled=3

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `group/group-api-tests.spec.ts:47` | Create Group |
| observe-present-get | — | `group/group-api-tests.spec.ts:197` | Get Group |
| observe-present-search | — | `group/group-api-tests.spec.ts:91` | Search Groups By Name |
| observe-present-search | — | `group/group-api-tests.spec.ts:124` | Search Groups By Id |
| mutate | unlabeled | `group/group-api-tests.spec.ts:249` | Update Group |
| mutate | not-found | `group/group-api-tests.spec.ts:304` | Update Group Not Found |
| delete | unlabeled | `group/group-api-tests.spec.ts:320` | Delete Group |
| delete | not-found | `group/group-api-tests.spec.ts:355` | Delete Group Not Found |
| negative-create | unauthorized | `group/group-api-tests.spec.ts:70` | Create Group Unauthorized |
| negative-create | bad-request | `group/group-api-tests.spec.ts:79` | Create Group Bad Request |
| negative-get | not-found | `group/group-api-tests.spec.ts:226` | Get Group Not Found |
| negative-get | unauthorized | `group/group-api-tests.spec.ts:239` | Get Group Unauthorized |
| negative-search | bad-request | `group/group-api-tests.spec.ts:156` | Search Groups By Invalid Id |
| negative-search | unauthorized | `group/group-api-tests.spec.ts:182` | Search Groups Unauthorized |
| negative-mutate | unauthorized | `group/group-api-tests.spec.ts:281` | Update Group Unauthorized |
| negative-mutate | bad-request | `group/group-api-tests.spec.ts:294` | Update Group Bad Request |
| negative-delete | unauthorized | `group/group-api-tests.spec.ts:347` | Delete Group Unauthorized |

## B. Membership/Association

**Form**: Create parent + member (prerequisite) → Assign member → Search members (Observe Present) → Unassign member → Search members (Observe Absence)

**Total tests**: 135

### `role` — 54 tests

- **Prerequisite to create**: role + client, role + group, role + mapping-rule, role + user
- **Files**: `role/role-clients-api-tests.spec.ts`, `role/role-groups-api-tests.spec.ts`, `role/role-mapping-rules-api-tests.spec.ts`, `role/role-users-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 18
- **Form-step counts**: observe-present-search=9, mutate=13, delete=11, negative-create=4, negative-search=9, negative-mutate=4, negative-delete=4
- **Variants**: happy-path=1, observe-via-get=3, observe-via-search=18, filter=4, bad-request=5, unauthorized=12, not-found=16, conflict=4, unlabeled=9

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-search | — | `role/role-clients-api-tests.spec.ts:126` | Search Role Clients |
| observe-present-search | — | `role/role-groups-api-tests.spec.ts:253` | Search Role Groups |
| observe-present-search | not-found | `role/role-groups-api-tests.spec.ts:318` | Search Role Groups Role Not Found |
| observe-present-search | — | `role/role-mapping-rules-api-tests.spec.ts:235` | Search Role Mapping Rules |
| observe-present-search | filter | `role/role-mapping-rules-api-tests.spec.ts:283` | Search Role Mapping Rules Filter By mappingRuleId |
| observe-present-search | filter | `role/role-mapping-rules-api-tests.spec.ts:320` | Search Role Mapping Rules Filter By name |
| observe-present-search | filter | `role/role-mapping-rules-api-tests.spec.ts:355` | Search Role Mapping Rules Filter By multiple fields |
| observe-present-search | not-found | `role/role-mapping-rules-api-tests.spec.ts:459` | Search Role Mapping Rules Role Not Found |
| observe-present-search | — | `role/role-users-api-tests.spec.ts:142` | Search Role Users |
| mutate | unlabeled | `role/role-clients-api-tests.spec.ts:54` | Assign Role To Client |
| mutate | happy-path | `role/role-clients-api-tests.spec.ts:69` | Assign Role To Client Non Existent Client Success |
| mutate | not-found | `role/role-clients-api-tests.spec.ts:80` | Assign Role To Client Non Existent Role Not Found |
| mutate | unlabeled | `role/role-groups-api-tests.spec.ts:64` | Assign Role To Group |
| mutate | unlabeled | `role/role-groups-api-tests.spec.ts:85` | Assign Role To Group Non Existent Group Sucess |
| mutate | not-found | `role/role-groups-api-tests.spec.ts:103` | Assign Role To Group Non Existent Role Not Found |
| mutate | unlabeled | `role/role-groups-api-tests.spec.ts:158` | Assign Role From Group |
| mutate | unlabeled | `role/role-mapping-rules-api-tests.spec.ts:67` | Assign Role To Mapping Rule |
| mutate | not-found | `role/role-mapping-rules-api-tests.spec.ts:83` | Assign Role To Mapping Rule Non Existent Mapping Rule Not Found |
| mutate | not-found | `role/role-mapping-rules-api-tests.spec.ts:100` | Assign Role To Mapping Rule Non Existent Role Not Found |
| mutate | unlabeled | `role/role-users-api-tests.spec.ts:61` | Assign Role To User |
| mutate | not-found | `role/role-users-api-tests.spec.ts:80` | Assign Role To User Non Existent User NotFound |
| mutate | not-found | `role/role-users-api-tests.spec.ts:95` | Assign Role To User Non Existent Role Not Found |
| delete | unlabeled | `role/role-clients-api-tests.spec.ts:186` | Unassign Role From Client |
| delete | not-found | `role/role-clients-api-tests.spec.ts:235` | Unassign Role From Client Non Existent Client Not Found |
| delete | not-found | `role/role-clients-api-tests.spec.ts:249` | Unassign Role From Client Non Existent Role Not Found |
| delete | not-found | `role/role-groups-api-tests.spec.ts:215` | Unassign Role From Group Non Existent Group Not Found |
| delete | not-found | `role/role-groups-api-tests.spec.ts:234` | Unassign Role From Group Non Existent Role Not Found |
| delete | unlabeled | `role/role-mapping-rules-api-tests.spec.ts:149` | Unassign Role From Mapping Rule |
| delete | not-found | `role/role-mapping-rules-api-tests.spec.ts:199` | Unassign Role From Mapping Rule Non Existent Mapping Rule Not Found |
| delete | not-found | `role/role-mapping-rules-api-tests.spec.ts:217` | Unassign Role From Mapping Rule Non Existent Role Not Found |
| delete | unlabeled | `role/role-users-api-tests.spec.ts:204` | Unassign Role From User |
| delete | not-found | `role/role-users-api-tests.spec.ts:257` | Unassign Role From User Non Existent User Not Found |
| delete | not-found | `role/role-users-api-tests.spec.ts:274` | Unassign Role From User Non Existent Role Not Found |
| negative-create | conflict | `role/role-clients-api-tests.spec.ts:112` | Assign Already Added Client To Role Conflict |
| negative-create | conflict | `role/role-groups-api-tests.spec.ts:140` | Assign Already Added Group To Role Conflict |
| negative-create | conflict | `role/role-mapping-rules-api-tests.spec.ts:132` | Assign Already Added Mapping Rule To Role Conflict |
| negative-create | conflict | `role/role-users-api-tests.spec.ts:126` | Assign Already Added User To Role Conflict |
| negative-search | unauthorized | `role/role-clients-api-tests.spec.ts:157` | Search Role Clients Unauthorized |
| negative-search | bad-request | `role/role-clients-api-tests.spec.ts:166` | Search Role Clients For Non Existent Role Empty |
| negative-search | bad-request, observe-via-get | `role/role-groups-api-tests.spec.ts:283` | Search Role Groups Role With No Assignments Returns Empty |
| negative-search | unauthorized | `role/role-groups-api-tests.spec.ts:309` | Search Role Groups Unauthorized |
| negative-search | bad-request, filter, observe-via-get | `role/role-mapping-rules-api-tests.spec.ts:393` | Search Role Mapping Rules Filter Non Matching Returns Empty |
| negative-search | bad-request, observe-via-get | `role/role-mapping-rules-api-tests.spec.ts:423` | Search Role Mapping Rules Role With No Assignments Returns Empty |
| negative-search | unauthorized | `role/role-mapping-rules-api-tests.spec.ts:450` | Search Role Mapping Rules Unauthorized |
| negative-search | unauthorized | `role/role-users-api-tests.spec.ts:173` | Search Role Users Unauthorized |
| negative-search | bad-request | `role/role-users-api-tests.spec.ts:183` | Search Role Users For Non Existent User Empty |
| negative-mutate | unauthorized | `role/role-clients-api-tests.spec.ts:97` | Assign Role To Client Unauthorized |
| negative-mutate | unauthorized | `role/role-groups-api-tests.spec.ts:123` | Assign Role To Group Unauthorized |
| negative-mutate | unauthorized | `role/role-mapping-rules-api-tests.spec.ts:117` | Assign Role To Mapping Rule Unauthorized |
| negative-mutate | unauthorized | `role/role-users-api-tests.spec.ts:110` | Assign Role To User Unauthorized |
| negative-delete | unauthorized | `role/role-clients-api-tests.spec.ts:223` | Unassign Role From Client Unauthorized |
| negative-delete | unauthorized | `role/role-groups-api-tests.spec.ts:201` | Unassign Role From Group Unauthorized |
| negative-delete | unauthorized | `role/role-mapping-rules-api-tests.spec.ts:187` | Unassign Role From Mapping Rule Unauthorized |
| negative-delete | unauthorized | `role/role-users-api-tests.spec.ts:244` | Unassign Role From User Unauthorized |

### `tenant` — 50 tests

- **Prerequisite to create**: tenant + client, tenant + group, tenant + mapping-rule, tenant + user
- **Files**: `tenant/tenant-clients-api-tests.spec.ts`, `tenant/tenant-groups-api-tests.spec.ts`, `tenant/tenant-mapping-rule-api-tests.spec.ts`, `tenant/tenant-users-api-tests.spec.ts`
- **Observation channel**: GET = 2, Search = 14
- **Form-step counts**: observe-present-search=6, mutate=12, delete=12, negative-create=4, negative-search=8, negative-mutate=4, negative-delete=4
- **Variants**: happy-path=6, observe-via-get=2, observe-via-search=14, bad-request=4, unauthorized=12, not-found=16, conflict=4, unlabeled=6

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-search | — | `tenant/tenant-clients-api-tests.spec.ts:126` | Search Tenant Clients |
| observe-present-search | — | `tenant/tenant-groups-api-tests.spec.ts:232` | Search Tenant Groups |
| observe-present-search | not-found | `tenant/tenant-groups-api-tests.spec.ts:300` | Search Tenant Groups Tenant Not Found |
| observe-present-search | happy-path | `tenant/tenant-mapping-rule-api-tests.spec.ts:246` | Search Tenant Mapping Rules - Success |
| observe-present-search | not-found | `tenant/tenant-mapping-rule-api-tests.spec.ts:325` | Search Tenant Mapping Rules Tenant Not Found - Not Found |
| observe-present-search | — | `tenant/tenant-users-api-tests.spec.ts:141` | Search Tenant Users |
| mutate | unlabeled | `tenant/tenant-clients-api-tests.spec.ts:52` | Assign Client To Tenant |
| mutate | happy-path | `tenant/tenant-clients-api-tests.spec.ts:66` | Assign Client To Tenant Non Existent Client Success |
| mutate | not-found | `tenant/tenant-clients-api-tests.spec.ts:80` | Assign Client To Tenant Non Existent Tenant Not Found |
| mutate | unlabeled | `tenant/tenant-groups-api-tests.spec.ts:43` | Assign Group To Tenant |
| mutate | not-found | `tenant/tenant-groups-api-tests.spec.ts:62` | Assign Group To Tenant Non Existent Group Not Found |
| mutate | not-found | `tenant/tenant-groups-api-tests.spec.ts:82` | Assign Group To Tenant Non Existent Tenant Not Found |
| mutate | happy-path | `tenant/tenant-mapping-rule-api-tests.spec.ts:42` | Assign Mapping Rule To Tenant - Success |
| mutate | not-found | `tenant/tenant-mapping-rule-api-tests.spec.ts:60` | Assign Mapping Rule To Tenant Non Existent Mapping Rule - Not Found |
| mutate | not-found | `tenant/tenant-mapping-rule-api-tests.spec.ts:83` | Assign Mapping Rule To Tenant Non Existent Tenant - Not Found |
| mutate | unlabeled | `tenant/tenant-users-api-tests.spec.ts:55` | Assign User To Tenant |
| mutate | happy-path | `tenant/tenant-users-api-tests.spec.ts:77` | Assign User To Tenant Non Existent User Success |
| mutate | not-found | `tenant/tenant-users-api-tests.spec.ts:92` | Assign User To Tenant Non Existent Tenant Not Found |
| delete | unlabeled | `tenant/tenant-clients-api-tests.spec.ts:188` | Unassign Client From Tenant |
| delete | not-found | `tenant/tenant-clients-api-tests.spec.ts:237` | Unassign Client From Tenant Non Existent Client Not Found |
| delete | not-found | `tenant/tenant-clients-api-tests.spec.ts:254` | Unassign Client From Tenant Non Existent Tenant Not Found |
| delete | unlabeled | `tenant/tenant-groups-api-tests.spec.ts:137` | Unassign Group From Tenant |
| delete | not-found | `tenant/tenant-groups-api-tests.spec.ts:194` | Unassign Group From Tenant Non Existent Group Not Found |
| delete | not-found | `tenant/tenant-groups-api-tests.spec.ts:213` | Unassign Group From Tenant Non Existent Tenant Not Found |
| delete | happy-path | `tenant/tenant-mapping-rule-api-tests.spec.ts:149` | Unassign Mapping Rule From Tenant - Success |
| delete | not-found | `tenant/tenant-mapping-rule-api-tests.spec.ts:208` | Unassign Mapping Rule From Tenant Non Existent Mapping Rule - Not Found |
| delete | not-found | `tenant/tenant-mapping-rule-api-tests.spec.ts:227` | Unassign Mapping Rule From Tenant Non Existent Tenant - Not Found |
| delete | unlabeled | `tenant/tenant-users-api-tests.spec.ts:203` | Unassign User From Tenant |
| delete | not-found | `tenant/tenant-users-api-tests.spec.ts:256` | Unassign User From Tenant Non Existent User Not Found |
| delete | not-found | `tenant/tenant-users-api-tests.spec.ts:273` | Unassign User From Tenant Non Existent Tenant Not Found |
| negative-create | conflict | `tenant/tenant-clients-api-tests.spec.ts:112` | Assign Already Added Client To Tenant Conflict |
| negative-create | conflict | `tenant/tenant-groups-api-tests.spec.ts:119` | Assign Already Added Group To Tenant Conflict |
| negative-create | conflict | `tenant/tenant-mapping-rule-api-tests.spec.ts:126` | Assign Already Added Mapping Rule To Tenant - Conflict |
| negative-create | conflict | `tenant/tenant-users-api-tests.spec.ts:125` | Assign Already Added User To Tenant Conflict |
| negative-search | unauthorized | `tenant/tenant-clients-api-tests.spec.ts:157` | Search Tenant Clients Unauthorized |
| negative-search | bad-request | `tenant/tenant-clients-api-tests.spec.ts:166` | Search Tenant Clients For Non Existent Tenant Empty |
| negative-search | bad-request, observe-via-get | `tenant/tenant-groups-api-tests.spec.ts:264` | Search Tenant Groups Tenant With No Assignments Returns Empty |
| negative-search | unauthorized | `tenant/tenant-groups-api-tests.spec.ts:291` | Search Tenant Groups Unauthorized |
| negative-search | bad-request, observe-via-get, happy-path | `tenant/tenant-mapping-rule-api-tests.spec.ts:289` | Search Tenant Mapping Rules Tenant With No Assignments Returns Empty - Success |
| negative-search | unauthorized | `tenant/tenant-mapping-rule-api-tests.spec.ts:316` | Search Tenant Mapping Rules - Unauthorized |
| negative-search | unauthorized | `tenant/tenant-users-api-tests.spec.ts:172` | Search Tenant Users Unauthorized |
| negative-search | bad-request | `tenant/tenant-users-api-tests.spec.ts:182` | Search Tenant Users For Non Existent User Empty |
| negative-mutate | unauthorized | `tenant/tenant-clients-api-tests.spec.ts:97` | Assign Client To Tenant Unauthorized |
| negative-mutate | unauthorized | `tenant/tenant-groups-api-tests.spec.ts:102` | Assign Group To Tenant Unauthorized |
| negative-mutate | unauthorized | `tenant/tenant-mapping-rule-api-tests.spec.ts:106` | Assign Mapping Rule To Tenant - Unauthorized |
| negative-mutate | unauthorized | `tenant/tenant-users-api-tests.spec.ts:109` | Assign User To Tenant Unauthorized |
| negative-delete | unauthorized | `tenant/tenant-clients-api-tests.spec.ts:225` | Unassign Client From Tenant Unauthorized |
| negative-delete | unauthorized | `tenant/tenant-groups-api-tests.spec.ts:180` | Unassign Group From Tenant Unauthorized |
| negative-delete | unauthorized | `tenant/tenant-mapping-rule-api-tests.spec.ts:192` | Unassign Mapping Rule From Tenant - Unauthorized |
| negative-delete | unauthorized | `tenant/tenant-users-api-tests.spec.ts:243` | Unassign User From Tenant Unauthorized |

### `group` — 31 tests

- **Prerequisite to create**: group + client, group + mapping-rule, group + role, group + user
- **Files**: `group/group-clients-api-tests.spec.ts`, `group/group-mapping-rules-api-tests.spec.ts`, `group/group-roles-api-tests.spec.ts`, `group/group-users-api-tests.spec.ts`
- **Observation channel**: GET = 0, Search = 15
- **Form-step counts**: observe-present-search=11, mutate=4, delete=6, negative-create=3, negative-search=4, negative-delete=3
- **Variants**: observe-via-search=15, unauthorized=7, not-found=8, conflict=3, unlabeled=6

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-search | — | `group/group-clients-api-tests.spec.ts:85` | Search Clients For Group |
| observe-present-search | not-found | `group/group-clients-api-tests.spec.ts:128` | Search Clients For Group Not Found |
| observe-present-search | — | `group/group-mapping-rules-api-tests.spec.ts:96` | Search Mapping Rules For Group |
| observe-present-search | not-found | `group/group-mapping-rules-api-tests.spec.ts:138` | Search Mapping Rules For Group Not Found |
| observe-present-search | — | `group/group-roles-api-tests.spec.ts:49` | Search Group Roles |
| observe-present-search | not-found | `group/group-roles-api-tests.spec.ts:104` | Search Group Roles Not Found |
| observe-present-search | — | `group/group-roles-api-tests.spec.ts:127` | Search Group Roles By Role Name |
| observe-present-search | — | `group/group-roles-api-tests.spec.ts:169` | Search Group Roles By Role Id |
| observe-present-search | — | `group/group-roles-api-tests.spec.ts:209` | Search Group Roles By Multiple Fields |
| observe-present-search | — | `group/group-users-api-tests.spec.ts:102` | Search Users For Group |
| observe-present-search | not-found | `group/group-users-api-tests.spec.ts:141` | Search Users For Group Not Found |
| mutate | unlabeled | `group/group-clients-api-tests.spec.ts:52` | Assign Client To Group |
| mutate | unlabeled | `group/group-mapping-rules-api-tests.spec.ts:55` | Assign Mapping Rule To Group |
| mutate | not-found | `group/group-users-api-tests.spec.ts:51` | Assign User To Group Not Found |
| mutate | unlabeled | `group/group-users-api-tests.spec.ts:69` | Assign User To Group |
| delete | unlabeled | `group/group-clients-api-tests.spec.ts:153` | Unassign Client From Group |
| delete | not-found | `group/group-clients-api-tests.spec.ts:212` | Unassign Client From Group Not Found |
| delete | unlabeled | `group/group-mapping-rules-api-tests.spec.ts:161` | Unassign Mapping Rule From Group |
| delete | not-found | `group/group-mapping-rules-api-tests.spec.ts:218` | Unassign Mapping Rule From Group Not Found |
| delete | unlabeled | `group/group-users-api-tests.spec.ts:163` | Unassign User From Group |
| delete | not-found | `group/group-users-api-tests.spec.ts:223` | Unassign User From Group Not Found |
| negative-create | conflict | `group/group-clients-api-tests.spec.ts:70` | Assign Already Added Client To Group Conflict |
| negative-create | conflict | `group/group-mapping-rules-api-tests.spec.ts:73` | Assign Already Added Mapping Rule To Group Conflict |
| negative-create | conflict | `group/group-users-api-tests.spec.ts:87` | Assign Already Added User To Group Conflict |
| negative-search | unauthorized | `group/group-clients-api-tests.spec.ts:116` | Search Clients For Group Unauthorized |
| negative-search | unauthorized | `group/group-mapping-rules-api-tests.spec.ts:126` | Search Mapping Rules For Group Unauthorized |
| negative-search | unauthorized | `group/group-roles-api-tests.spec.ts:91` | Search Group Roles Unauthorized |
| negative-search | unauthorized | `group/group-users-api-tests.spec.ts:131` | Search Users For Group Unauthorized |
| negative-delete | unauthorized | `group/group-clients-api-tests.spec.ts:197` | Unassign Client From Group Unauthorized |
| negative-delete | unauthorized | `group/group-mapping-rules-api-tests.spec.ts:204` | Unassign Mapping Rule From Group Unauthorized |
| negative-delete | unauthorized | `group/group-users-api-tests.spec.ts:208` | Unassign User From Group Unauthorized |

## C. Deployment Lifecycle

**Form**: Deploy resource → Get definition (XML/JSON) → Search definitions (Observe Present) → Delete resource → Get definition (Observe Absence)

**Total tests**: 96

### `decision-definition` — 33 tests

- **Prerequisite to create**: deployed-decision
- **Files**: `decision-definition/evaluate-decision-definitions-api.tests.spec.ts`, `decision-definition/get-decision-definitions-api.tests.spec.ts`, `decision-definition/search-decision-definitions-api.tests.spec.ts`
- **Observation channel**: GET = 8, Search = 16
- **Form-step counts**: observe-present-get=2, observe-present-search=14, evaluate=9, negative-get=6, negative-search=2
- **Variants**: observe-via-get=8, observe-via-search=16, pagination-sort=5, filter=1, bad-request=4, unauthorized=4, not-found=2, unlabeled=7

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | — | `decision-definition/get-decision-definitions-api.tests.spec.ts:52` | Get Decision Definition |
| observe-present-get | — | `decision-definition/get-decision-definitions-api.tests.spec.ts:116` | Get Decision Definition XML |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:53` | Search Decision Definitions |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:90` | Search Decision Definitions by decisionDefinitionId |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:128` | Search Decision Definitions by name |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:164` | Search Decision Definitions by version |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:204` | Search Decision Definitions by decisionRequirementsId |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:243` | Search Decision Definitions by decisionDefinitionKey |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:282` | Search Decision Definitions by decisionRequirementsKey |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:321` | Search Decision Definitions by tenantId |
| observe-present-search | filter | `decision-definition/search-decision-definitions-api.tests.spec.ts:361` | Search Decision Definitions by multiple filters |
| observe-present-search | — | `decision-definition/search-decision-definitions-api.tests.spec.ts:401` | Search Decision Definitions - No Matching Item |
| observe-present-search | pagination-sort | `decision-definition/search-decision-definitions-api.tests.spec.ts:438` | Search Decision Definitions Sort By decisionDefinitionId ASC |
| observe-present-search | pagination-sort | `decision-definition/search-decision-definitions-api.tests.spec.ts:493` | Search Decision Definitions Sort By decisionDefinitionId DESC |
| observe-present-search | pagination-sort | `decision-definition/search-decision-definitions-api.tests.spec.ts:570` | Search Decision Definitions Sort By name ASC |
| observe-present-search | pagination-sort | `decision-definition/search-decision-definitions-api.tests.spec.ts:633` | Search Decision Definitions Sort By name DESC |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:48` | Evaluate Decision Definition by decisionDefinitionKey For Input 8.8 |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:111` | Evaluate Decision Definition by decisionDefinitionKey For Input 8.7 |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:170` | Evaluate Decision Definition by decisionDefinitionKey For Input 8.6 |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:228` | Evaluate Decision Definition by decisionDefinitionKey For Non Existent Input |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:289` | Evaluate Decision Definition by decisionDefinitionId For Input With status VIP and Score 50 |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:368` | Evaluate Decision Definition by decisionDefinitionId For Input With status Regular and Score 40 |
| evaluate | unlabeled | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:446` | Evaluate Decision Definition by decisionDefinitionId For Non Existent Input |
| evaluate | bad-request | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:506` | Evaluate Decision Definition Invalid Data |
| evaluate | unauthorized | `decision-definition/evaluate-decision-definitions-api.tests.spec.ts:524` | Evaluate Decision Definition Unauthorized |
| negative-get | not-found | `decision-definition/get-decision-definitions-api.tests.spec.ts:80` | Get Decision Definition Not Found |
| negative-get | bad-request | `decision-definition/get-decision-definitions-api.tests.spec.ts:94` | Get Decision Definition Bad Request |
| negative-get | unauthorized | `decision-definition/get-decision-definitions-api.tests.spec.ts:105` | Get Decision Definition Unauthorized |
| negative-get | not-found | `decision-definition/get-decision-definitions-api.tests.spec.ts:133` | Get Decision Definition XML Not Found |
| negative-get | bad-request | `decision-definition/get-decision-definitions-api.tests.spec.ts:147` | Get Decision Definition XML Bad Request |
| negative-get | unauthorized | `decision-definition/get-decision-definitions-api.tests.spec.ts:158` | Get Decision Definition XML Unauthorized |
| negative-search | unauthorized | `decision-definition/search-decision-definitions-api.tests.spec.ts:427` | Search Decision Definitions Unauthorized |
| negative-search | bad-request, pagination-sort | `decision-definition/search-decision-definitions-api.tests.spec.ts:548` | Search Decision Definitions Sort Invalid Body |

### `process-definition` — 30 tests

- **Prerequisite to create**: deployed-process
- **Files**: `process-definition/process-definition-get-api.spec.ts`, `process-definition/process-definition-get-start-form-api.spec.ts`, `process-definition/process-definition-get-statistics-api-tests.spec.ts`, `process-definition/process-definition-get-xml-api.spec.ts`, `process-definition/process-definition-search-api-tests.spec.ts`
- **Observation channel**: GET = 19, Search = 11
- **Form-step counts**: observe-present-get=4, observe-present-search=6, aggregate=3, negative-get=9, negative-search=5, negative-aggregate=3
- **Variants**: happy-path=4, observe-via-get=19, observe-via-search=11, pagination-sort=5, filter=6, bad-request=9, unauthorized=5, not-found=3

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `process-definition/process-definition-get-api.spec.ts:37` | Get Process Definition - Success |
| observe-present-get | happy-path | `process-definition/process-definition-get-start-form-api.spec.ts:57` | Get Process Definition Start Form - Success 200 |
| observe-present-get | happy-path | `process-definition/process-definition-get-start-form-api.spec.ts:83` | Get Process Definition Start Form - Success 204 No Content |
| observe-present-get | happy-path | `process-definition/process-definition-get-xml-api.spec.ts:39` | Get Process Definition XML - Success |
| observe-present-search | — | `process-definition/process-definition-search-api-tests.spec.ts:39` | Search Process Definitions - Basic |
| observe-present-search | filter | `process-definition/process-definition-search-api-tests.spec.ts:61` | Search Process Definitions - with one filter field |
| observe-present-search | filter | `process-definition/process-definition-search-api-tests.spec.ts:89` | Search Process Definitions - with multiple filter |
| observe-present-search | filter | `process-definition/process-definition-search-api-tests.spec.ts:122` | Search Process Definitions - filter isLatestVersion & resourceName |
| observe-present-search | pagination-sort | `process-definition/process-definition-search-api-tests.spec.ts:180` | Search Process Definitions - with pagination |
| observe-present-search | pagination-sort | `process-definition/process-definition-search-api-tests.spec.ts:223` | Search Process Definitions - with sorting |
| aggregate | observe-via-get | `process-definition/process-definition-get-statistics-api-tests.spec.ts:35` | Get Process Definition Statistics - Basic |
| aggregate | filter, observe-via-get | `process-definition/process-definition-get-statistics-api-tests.spec.ts:106` | Get Process Definition Statistics - Filter by Element Id |
| aggregate | filter, observe-via-get | `process-definition/process-definition-get-statistics-api-tests.spec.ts:167` | Get Process Definition Statistics - Or Filter |
| negative-get | not-found | `process-definition/process-definition-get-api.spec.ts:63` | Get Process Definition - Not Found |
| negative-get | unauthorized | `process-definition/process-definition-get-api.spec.ts:73` | Get Process Definition - Unauthorized |
| negative-get | bad-request | `process-definition/process-definition-get-api.spec.ts:80` | Get Process Definition - Invalid Key |
| negative-get | not-found | `process-definition/process-definition-get-start-form-api.spec.ts:97` | Get Process Definition Start Form - Not Found |
| negative-get | unauthorized | `process-definition/process-definition-get-start-form-api.spec.ts:110` | Get Process Definition Start Form - Unauthorized |
| negative-get | bad-request | `process-definition/process-definition-get-start-form-api.spec.ts:119` | Get Process Definition Start Form - Invalid Key |
| negative-get | not-found | `process-definition/process-definition-get-xml-api.spec.ts:52` | Get Process Definition XML - Not Found |
| negative-get | unauthorized | `process-definition/process-definition-get-xml-api.spec.ts:62` | Get Process Definition XML - Unauthorized |
| negative-get | bad-request | `process-definition/process-definition-get-xml-api.spec.ts:69` | Get Process Definition XML - Invalid Key |
| negative-search | bad-request | `process-definition/process-definition-search-api-tests.spec.ts:154` | Search Process Definitions - with empty result |
| negative-search | bad-request, pagination-sort | `process-definition/process-definition-search-api-tests.spec.ts:205` | Search Process Definitions - with invalid pagination parameters |
| negative-search | bad-request, pagination-sort | `process-definition/process-definition-search-api-tests.spec.ts:257` | Search Process Definitions - with missing required sorting field |
| negative-search | bad-request, pagination-sort | `process-definition/process-definition-search-api-tests.spec.ts:277` | Search Process Definitions - with invalid sorting field |
| negative-search | unauthorized | `process-definition/process-definition-search-api-tests.spec.ts:297` | Search Process Definitions - Unauthorized |
| negative-aggregate | unauthorized, observe-via-get | `process-definition/process-definition-get-statistics-api-tests.spec.ts:74` | Get Process Definition Statistics - Unauthorized |
| negative-aggregate | bad-request, observe-via-get | `process-definition/process-definition-get-statistics-api-tests.spec.ts:90` | Get Process Definition Statistics - Invalid Process Definition Key |
| negative-aggregate | bad-request, filter, observe-via-get | `process-definition/process-definition-get-statistics-api-tests.spec.ts:138` | Get Process Definition Statistics - Filter by Invalid Element Id - empty result |

### `resource` — 19 tests

- **Prerequisite to create**: none
- **Files**: `resource/resource-delete-api.spec.ts`, `resource/resource-deploy-api.spec.ts`, `resource/resource-get-api.spec.ts`, `resource/resource-get-content-api.spec.ts`
- **Observation channel**: GET = 6, Search = 0
- **Form-step counts**: create=6, observe-present-get=2, delete=2, negative-create=2, negative-get=4, negative-delete=3
- **Variants**: happy-path=9, observe-via-get=6, bad-request=3, unauthorized=4, not-found=3

| form step | variants | file:line | test name |
|--|--|--|--|
| create | happy-path | `resource/resource-deploy-api.spec.ts:32` | Deploy Resource - Process Definition Success |
| create | happy-path | `resource/resource-deploy-api.spec.ts:59` | Deploy Resource - Form Success |
| create | happy-path | `resource/resource-deploy-api.spec.ts:83` | Deploy Resource - Decision Definition Success |
| create | happy-path | `resource/resource-deploy-api.spec.ts:117` | Deploy Resource - RPA success |
| create | happy-path | `resource/resource-deploy-api.spec.ts:140` | Deploy Multiple Resources - Process Definition and Form Success |
| create | happy-path | `resource/resource-deploy-api.spec.ts:176` | Deploy Multiple Resources - All Resource Types Success |
| observe-present-get | happy-path | `resource/resource-get-api.spec.ts:49` | Get Resource - RPA Success 200 |
| observe-present-get | happy-path | `resource/resource-get-content-api.spec.ts:22` | Get Resource Content - RPA Success 200 |
| delete | happy-path | `resource/resource-delete-api.spec.ts:26` | Delete Resource - Success 200 |
| delete | not-found | `resource/resource-delete-api.spec.ts:52` | Delete Resource - Not Found 404 |
| negative-create | unauthorized | `resource/resource-deploy-api.spec.ts:222` | Deploy Resource - Unauthorized 401 |
| negative-create | bad-request | `resource/resource-deploy-api.spec.ts:233` | Deploy Resource - Bad Request 400 |
| negative-get | not-found | `resource/resource-get-api.spec.ts:81` | Get Resource - Not Found 404 |
| negative-get | unauthorized | `resource/resource-get-api.spec.ts:100` | Get Resource - Unauthorized 401 |
| negative-get | not-found | `resource/resource-get-content-api.spec.ts:47` | Get Resource Content - Not Found 404 |
| negative-get | unauthorized | `resource/resource-get-content-api.spec.ts:66` | Get Resource Content - Unauthorized 401 |
| negative-delete | bad-request | `resource/resource-delete-api.spec.ts:70` | Delete Resource - Bad Request 400 - Invalid resourceKey Format |
| negative-delete | bad-request | `resource/resource-delete-api.spec.ts:90` | Delete Resource - Bad Request 400 - Invalid operationReference in Body |
| negative-delete | unauthorized | `resource/resource-delete-api.spec.ts:116` | Delete Resource - Unauthorized 401 |

### `decision-requirements` — 14 tests

- **Prerequisite to create**: deployed-drd
- **Files**: `decision-requirements/decision-requirements-get-json-api.spec.ts`, `decision-requirements/decision-requirements-get-xml-api.spec.ts`, `decision-requirements/decision-requirements-search-api.spec.ts`
- **Observation channel**: GET = 8, Search = 6
- **Form-step counts**: observe-present-get=2, observe-present-search=3, negative-get=6, negative-search=3
- **Variants**: happy-path=5, observe-via-get=8, observe-via-search=6, filter=1, bad-request=4, unauthorized=3, not-found=2

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `decision-requirements/decision-requirements-get-json-api.spec.ts:36` | Get JSON Decision Requirements - Success |
| observe-present-get | happy-path | `decision-requirements/decision-requirements-get-xml-api.spec.ts:33` | Get XML Decision Requirements - Success |
| observe-present-search | happy-path | `decision-requirements/decision-requirements-search-api.spec.ts:37` | Search decision requirements - multiple results - success |
| observe-present-search | happy-path | `decision-requirements/decision-requirements-search-api.spec.ts:65` | Search decision requirements by version success |
| observe-present-search | happy-path | `decision-requirements/decision-requirements-search-api.spec.ts:96` | Search decision requirements by decisionRequirementsName and version success |
| negative-get | not-found | `decision-requirements/decision-requirements-get-json-api.spec.ts:66` | Get JSON Decision Requirements - Not found |
| negative-get | bad-request | `decision-requirements/decision-requirements-get-json-api.spec.ts:83` | Get JSON Decision Requirements - Invalid Value |
| negative-get | unauthorized | `decision-requirements/decision-requirements-get-json-api.spec.ts:100` | Get JSON Decision Requirements - Unauthorized |
| negative-get | not-found | `decision-requirements/decision-requirements-get-xml-api.spec.ts:50` | Get XML Decision Requirements - Not found |
| negative-get | bad-request | `decision-requirements/decision-requirements-get-xml-api.spec.ts:67` | Get XML Decision Requirements - Invalid Value |
| negative-get | unauthorized | `decision-requirements/decision-requirements-get-xml-api.spec.ts:84` | Get XML Decision Requirements - Unauthorized |
| negative-search | unauthorized | `decision-requirements/decision-requirements-search-api.spec.ts:137` | Search decision requirements - unauthorized request |
| negative-search | bad-request | `decision-requirements/decision-requirements-search-api.spec.ts:155` | Search decision requirements - empty result |
| negative-search | bad-request, filter | `decision-requirements/decision-requirements-search-api.spec.ts:188` | Search decision requirements - invalid filter |

## D. Process-Instance Lifecycle & Ops

**Form**: Deploy process (prerequisite) → Create instance → Get/Search instance → Cancel/Migrate/Modify/Resolve-incident → Delete → Observe absence. Batch creators wrap N instances per call.

**Total tests**: 113

### `process-instance` — 113 tests

- **Prerequisite to create**: deployed-process
- **Files**: `process-instance/process-instance-api.spec.ts`, `process-instance/process-instance-business-id-api.spec.ts`, `process-instance/process-instance-business-id-api2.spec.ts`, `process-instance/process-instance-cancel-api.spec.ts`, `process-instance/process-instance-create-batch-to-cancel-api.spec.ts`, `process-instance/process-instance-create-batch-to-delete-api.spec.ts`, `process-instance/process-instance-create-batch-to-migrate-api.spec.ts`, `process-instance/process-instance-create-batch-to-modify-api.spec.ts`, `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts`, `process-instance/process-instance-delete-api.spec.ts`, `process-instance/process-instance-get-api.spec.ts`, `process-instance/process-instance-get-call-hierachy-api.spec.ts`, `process-instance/process-instance-get-sequenceflows-api.spec.ts`, `process-instance/process-instance-get-statistics-api.spec.ts`, `process-instance/process-instance-migrate-api.spec.ts`, `process-instance/process-instance-modify-process-api.spec.ts`, `process-instance/process-instance-resolve-related-incident-api.spec.ts`, `process-instance/process-instance-search-api.spec.ts`, `process-instance/process-instance-search-incidents-api.spec.ts`
- **Observation channel**: GET = 20, Search = 13
- **Form-step counts**: create=19, observe-present-get=7, observe-present-search=9, mutate=5, delete=9, observe-absence=1, aggregate=2, negative-create=16, negative-get=7, negative-search=4, negative-mutate=11, negative-delete=9, negative-aggregate=2, other=12
- **Variants**: happy-path=32, observe-via-get=20, observe-via-search=13, observe-absence=1, pagination-sort=1, filter=19, bad-request=29, unauthorized=16, forbidden=3, not-found=8, conflict=3, unlabeled=6

| form step | variants | file:line | test name |
|--|--|--|--|
| create | happy-path | `process-instance/process-instance-api.spec.ts:22` | Create Process Instance - Success |
| create | happy-path | `process-instance/process-instance-api.spec.ts:51` | Create Process with Variables - Success |
| create | happy-path | `process-instance/process-instance-api.spec.ts:75` | Create Process with Tags - Success |
| create | happy-path | `process-instance/process-instance-api.spec.ts:98` | Create Process Instance by Process Definition Key - Success |
| create | happy-path | `process-instance/process-instance-api.spec.ts:155` | Create Process Instance by Process Definition Key with Variables - Success |
| create | happy-path | `process-instance/process-instance-api.spec.ts:205` | Create Process Instance by Process Definition Key with Tags - Success |
| create | unlabeled | `process-instance/process-instance-api.spec.ts:242` | Create Process Instance with startInstruction |
| create | unlabeled | `process-instance/process-instance-api.spec.ts:268` | Create Process Instance - Failure - Missing process definition id and key |
| create | happy-path | `process-instance/process-instance-create-batch-to-cancel-api.spec.ts:41` | Create a Batch Operation to Cancel Process Instances - Success |
| create | filter | `process-instance/process-instance-create-batch-to-cancel-api.spec.ts:125` | Create a Batch Operation to Cancel Process Instances - With Multiple Filters |
| create | happy-path | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:138` | Create a Batch Operation to Migrate Process Instances - Success |
| create | filter | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:205` | Create a Batch Operation to Migrate Process Instances - With Multiple Filters |
| create | filter | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:282` | Create a Batch Operation to Migrate Process Instances - With Or Filters |
| create | filter | `process-instance/process-instance-create-batch-to-modify-api.spec.ts:123` | Create a Batch Operation to Modify Process Instances - With Multiple Filters |
| create | filter | `process-instance/process-instance-create-batch-to-modify-api.spec.ts:249` | Create a Batch Operation to Modify Process Instances - With Or Filters |
| create | happy-path | `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts:55` | Create a Batch Operation to Resolve Incidents - Success |
| create | filter | `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts:155` | Create a Batch Operation to Resolve Incidents - With Multiple Filters |
| create | filter | `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts:257` | Create a Batch Operation to Resolve Incidents - With Or Filters |
| create | not-found | `process-instance/process-instance-migrate-api.spec.ts:248` | Create Process instance migrate - 404 Not Found - non existing process instance |
| observe-present-get | — | `process-instance/process-instance-business-id-api2.spec.ts:27` | GET process instance includes businessId when set at creation |
| observe-present-get | — | `process-instance/process-instance-business-id-api2.spec.ts:69` | GET process instance returns null businessId when not set at creation |
| observe-present-get | happy-path | `process-instance/process-instance-get-api.spec.ts:28` | Get Process Instance - Success |
| observe-present-get | happy-path | `process-instance/process-instance-get-call-hierachy-api.spec.ts:28` | Get Process Instance Call Hierarchy - Success |
| observe-present-get | — | `process-instance/process-instance-get-call-hierachy-api.spec.ts:130` | Get Process Instance Call Hierarchy - No Items Found |
| observe-present-get | happy-path | `process-instance/process-instance-get-sequenceflows-api.spec.ts:27` | Get Process Instance Sequence Flows - Success |
| observe-present-get | — | `process-instance/process-instance-get-sequenceflows-api.spec.ts:99` | Get Process Instance Sequence Flows - No Items |
| observe-present-search | observe-via-get | `process-instance/process-instance-business-id-api2.spec.ts:116` | Search by businessId returns only the matching process instance |
| observe-present-search | — | `process-instance/process-instance-business-id-api2.spec.ts:164` | Search results include businessId field for all items |
| observe-present-search | pagination-sort | `process-instance/process-instance-business-id-api2.spec.ts:241` | Search results can be sorted by businessId |
| observe-present-search | happy-path | `process-instance/process-instance-search-api.spec.ts:30` | Search Process Instances - Success |
| observe-present-search | filter, happy-path | `process-instance/process-instance-search-api.spec.ts:77` | Search Process Instance With Filter - Success |
| observe-present-search | filter, happy-path | `process-instance/process-instance-search-api.spec.ts:135` | Search Process Instance With Multiple Filters - Success |
| observe-present-search | — | `process-instance/process-instance-search-api.spec.ts:255` | Search Process Instances - No Items Found |
| observe-present-search | happy-path | `process-instance/process-instance-search-incidents-api.spec.ts:35` | Search Process Instances with Multiple Incidents - Success |
| observe-present-search | not-found | `process-instance/process-instance-search-incidents-api.spec.ts:108` | Search Process Instances with Incidents - Not Found |
| mutate | happy-path | `process-instance/process-instance-migrate-api.spec.ts:46` | Process instance migrate - success |
| mutate | happy-path | `process-instance/process-instance-modify-process-api.spec.ts:31` | Modify process instance - success |
| mutate | not-found | `process-instance/process-instance-modify-process-api.spec.ts:229` | Modify process instance - Not Found |
| mutate | happy-path | `process-instance/process-instance-resolve-related-incident-api.spec.ts:82` | Resolve related incidents of a process instance - Success |
| mutate | not-found | `process-instance/process-instance-resolve-related-incident-api.spec.ts:257` | Resolve related incidents of a not existing process instance - Not found |
| delete | happy-path | `process-instance/process-instance-cancel-api.spec.ts:22` | Cancel Process Instance - Success |
| delete | not-found | `process-instance/process-instance-cancel-api.spec.ts:59` | Cancel Process Instance - Not Found |
| delete | not-found | `process-instance/process-instance-cancel-api.spec.ts:105` | Double Cancel Process Instance - Not Found |
| delete | happy-path | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:101` | Delete Batch Process Instance, single Process Instance - Success |
| delete | happy-path | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:172` | Delete Batch Process Instance, multiple Process Instances - Success |
| delete | unlabeled | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:247` | Delete Batch Active Single Process Instance - No instance should be deleted |
| delete | unlabeled | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:347` | Delete Batch Active Multiple Process Instances - No instance should be deleted |
| delete | happy-path | `process-instance/process-instance-delete-api.spec.ts:96` | Delete Single Process Instance - Success |
| delete | not-found | `process-instance/process-instance-delete-api.spec.ts:173` | Delete Single Process Instance - Not Found |
| observe-absence | observe-absence, happy-path | `process-instance/process-instance-business-id-api.spec.ts:193` | Business ID reuse after cancellation - success |
| aggregate | observe-via-get, happy-path | `process-instance/process-instance-get-statistics-api.spec.ts:27` | Get Process Instance Statistics - Success |
| aggregate | observe-via-get | `process-instance/process-instance-get-statistics-api.spec.ts:112` | Get Process Instance Statistics - No Items Found |
| negative-create | bad-request | `process-instance/process-instance-api.spec.ts:286` | Create Process Instance - Failure - Invalid process definition id |
| negative-create | bad-request | `process-instance/process-instance-api.spec.ts:303` | Create Process Instance - Failure - Invalid process definition Id |
| negative-create | unauthorized | `process-instance/process-instance-create-batch-to-cancel-api.spec.ts:27` | Create a Batch Operation to Cancel Process Instances - Unauthorized |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-cancel-api.spec.ts:90` | Create a Batch Operation to Cancel Process Instances With No Filter - Bad Request |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-cancel-api.spec.ts:105` | Create a Batch Operation to Cancel Process Instances - With Invalid Filter - Bad Request |
| negative-create | unauthorized | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:51` | Create a Batch Operation to Migrate Process Instances - Unauthorized |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:72` | Create a Batch Operation to Migrate Process Instances - With No Filter - Bad Request |
| negative-create | bad-request | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:93` | Create a Batch Operation to Migrate Process Instances - With No Migration Instructions - Bad Request |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-migrate-api.spec.ts:112` | Create a Batch Operation to Migrate Process Instances - With Invalid Filter - Bad Request |
| negative-create | unauthorized | `process-instance/process-instance-create-batch-to-modify-api.spec.ts:35` | Create a Batch Operation to Modify Process Instances - Unauthorized |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-modify-api.spec.ts:57` | Create a Batch Operation to Modify Process Instances - With No Filter - Bad Request |
| negative-create | bad-request | `process-instance/process-instance-create-batch-to-modify-api.spec.ts:78` | Create a Batch Operation to Modify Process Instances - With No Instructions - Bad Request |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-modify-api.spec.ts:100` | Create a Batch Operation to Modify Process Instances - With Invalid Filter - Bad Request |
| negative-create | unauthorized | `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts:41` | Create a Batch Operation to Resolve Process Instance Incidents - Unauthorized |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts:120` | Create a Batch Operation to Resolve Incidents With No Filter - Bad Request |
| negative-create | bad-request, filter | `process-instance/process-instance-create-batch-to-resolve-incidents-api.spec.ts:135` | Create a Batch Operation to Resolve Incidents - With Invalid Filter - Bad Request |
| negative-get | not-found | `process-instance/process-instance-get-api.spec.ts:87` | Get Process Instance - Not Found |
| negative-get | bad-request | `process-instance/process-instance-get-api.spec.ts:101` | Get Process Instance - Invalid Key |
| negative-get | unauthorized | `process-instance/process-instance-get-api.spec.ts:112` | Get Process Instance - Unauthorized |
| negative-get | unauthorized | `process-instance/process-instance-get-call-hierachy-api.spec.ts:117` | Get Process Instance Call Hierarchy - Unauthorized |
| negative-get | bad-request | `process-instance/process-instance-get-call-hierachy-api.spec.ts:145` | Get Process Instance Call Hierarchy - Bad Request |
| negative-get | unauthorized | `process-instance/process-instance-get-sequenceflows-api.spec.ts:87` | Get Process Instance Sequence Flows - Unauthorized |
| negative-get | bad-request | `process-instance/process-instance-get-sequenceflows-api.spec.ts:119` | Get Process Instance Sequence Flows - Bad Request |
| negative-search | bad-request, observe-via-get | `process-instance/process-instance-business-id-api2.spec.ts:213` | Search by businessId returns empty when no instance matches |
| negative-search | unauthorized | `process-instance/process-instance-search-api.spec.ts:231` | Search Process Instances - Unauthorized |
| negative-search | bad-request | `process-instance/process-instance-search-api.spec.ts:238` | Search Process Instances - Bad Request - Invalid Payload |
| negative-search | unauthorized | `process-instance/process-instance-search-incidents-api.spec.ts:96` | Search Process Instances with Incidents - Unauthorized |
| negative-mutate | bad-request | `process-instance/process-instance-migrate-api.spec.ts:117` | Process instance migrate - 409 Invalid State |
| negative-mutate | bad-request | `process-instance/process-instance-migrate-api.spec.ts:162` | Process instance migrate - 400 Bad Request - invalid path parameter |
| negative-mutate | bad-request | `process-instance/process-instance-migrate-api.spec.ts:179` | Process instance migrate - 400 Invalid Argument - Missing targetProcessDefinitionKey |
| negative-mutate | bad-request | `process-instance/process-instance-migrate-api.spec.ts:205` | Process instance migrate - 400 Bad Request - Missing mappingInstructions |
| negative-mutate | unauthorized | `process-instance/process-instance-migrate-api.spec.ts:225` | Process instance migrate - Unauthorized |
| negative-mutate | bad-request | `process-instance/process-instance-modify-process-api.spec.ts:138` | Modify process instance - bad request - invalid payload |
| negative-mutate | bad-request | `process-instance/process-instance-modify-process-api.spec.ts:185` | Modify process instance - bad request - path parameter |
| negative-mutate | unauthorized | `process-instance/process-instance-modify-process-api.spec.ts:208` | Modify process instance - Unauthorized |
| negative-mutate | bad-request | `process-instance/process-instance-resolve-related-incident-api.spec.ts:224` | Resolve related incidents with process instance key string value - Bad Request |
| negative-mutate | unauthorized | `process-instance/process-instance-resolve-related-incident-api.spec.ts:242` | Resolve related incidents of a process instance - Unauthorized |
| negative-mutate | forbidden | `process-instance/process-instance-resolve-related-incident-api.spec.ts:275` | Resolve related incidents of a process instance without permissions - Forbidden |
| negative-delete | bad-request | `process-instance/process-instance-cancel-api.spec.ts:74` | Cancel Process Instance - Bad Request - Invalid Key |
| negative-delete | unauthorized | `process-instance/process-instance-cancel-api.spec.ts:91` | Cancel Process Instance - Unauthorized |
| negative-delete | unauthorized | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:451` | Delete Batch Process Instance, single Process Instance - Unauthorized |
| negative-delete | forbidden | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:466` | Delete Batch Process Instance, single Process Instance - Forbidden |
| negative-delete | bad-request, filter | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:487` | Delete Batch Process Instance, no filter - Bad Request |
| negative-delete | bad-request, filter | `process-instance/process-instance-create-batch-to-delete-api.spec.ts:499` | Delete Batch Process Instance, invalid filter - Bad Request |
| negative-delete | unauthorized | `process-instance/process-instance-delete-api.spec.ts:145` | Delete Single Process Instance - Unauthorized |
| negative-delete | forbidden | `process-instance/process-instance-delete-api.spec.ts:156` | Delete Single Process Instance - Forbidden |
| negative-delete | conflict | `process-instance/process-instance-delete-api.spec.ts:190` | Delete Single Process Instance - Conflict |
| negative-aggregate | unauthorized, observe-via-get | `process-instance/process-instance-get-statistics-api.spec.ts:99` | Get Process Instance Statistics - Unauthorized |
| negative-aggregate | bad-request, observe-via-get | `process-instance/process-instance-get-statistics-api.spec.ts:136` | Get Process Instance Statistics - Bad Request |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:38` | Start process instance with Business ID - success |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:55` | Start process instance without Business ID - success |
| other | bad-request | `process-instance/process-instance-business-id-api.spec.ts:70` | Start process instance with Business ID exceeding 256 characters - bad request |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:83` | Start process instance with Business ID of exactly 256 characters - success |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:111` | Start process instance with Business ID when uniqueness enabled - success |
| other | conflict | `process-instance/process-instance-business-id-api.spec.ts:128` | Duplicate root instance with same Business ID - conflict |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:159` | Business ID reuse after instance completes - success |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:219` | Same Business ID across different process definitions - success |
| other | conflict | `process-instance/process-instance-business-id-api.spec.ts:253` | New start with same Business ID while instance is running - conflict |
| other | happy-path | `process-instance/process-instance-business-id-api.spec.ts:297` | Business ID retained after process migration - success |
| other | unlabeled | `process-instance/process-instance-business-id-api2.spec.ts:313` | Child process instance inherits Business ID from parent via call activity |
| other | unlabeled | `process-instance/process-instance-business-id-api2.spec.ts:382` | Child process instance has no Business ID when parent has none |

## E. Batch-Operation Lifecycle

**Form**: Create batch (via batch-creating process-instance APIs, prerequisite) → Get batch → Search batch → Search items → Suspend → Cancel

**Total tests**: 32

### `batch-operation` — 32 tests

- **Prerequisite to create**: running-process-instance(s)
- **Files**: `batch-operation/batch-operation-get-api.spec.ts`, `batch-operation/batch-operation-items-search-api.spec.ts`, `batch-operation/batch-operations-cancel-api-tests.spec.ts`, `batch-operation/batch-operations-search-api.spec.ts`, `batch-operation/batch-operations-suspend-api-tests.spec.ts`
- **Observation channel**: GET = 14, Search = 16
- **Form-step counts**: observe-present-get=1, observe-present-search=7, mutate=6, delete=5, negative-get=2, negative-search=9, negative-mutate=1, negative-delete=1
- **Variants**: happy-path=6, observe-via-get=14, observe-via-search=16, pagination-sort=3, filter=4, bad-request=9, unauthorized=3, not-found=1, unlabeled=2

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `batch-operation/batch-operation-get-api.spec.ts:28` | Get Batch Operation - Success |
| observe-present-search | happy-path | `batch-operation/batch-operation-items-search-api.spec.ts:60` | Search Batch Operation Items - by itemKey [incident key] - Success |
| observe-present-search | happy-path | `batch-operation/batch-operation-items-search-api.spec.ts:159` | Search Batch Operation Items - by process instance key with single canceled process instance - Success |
| observe-present-search | happy-path | `batch-operation/batch-operation-items-search-api.spec.ts:258` | Search Batch Operation Items - by itemKey [process instance key] with multiple canceled process instance - Success |
| observe-present-search | happy-path | `batch-operation/batch-operation-items-search-api.spec.ts:363` | Search Batch Operation Items - by operationType - Success |
| observe-present-search | happy-path | `batch-operation/batch-operations-search-api.spec.ts:38` | Search Batch Operations Success |
| observe-present-search | filter | `batch-operation/batch-operations-search-api.spec.ts:72` | Search Batch Operations Filter By State And Type |
| observe-present-search | pagination-sort | `batch-operation/batch-operations-search-api.spec.ts:105` | Search Batch Operations Cursor Pagination |
| mutate | observe-via-get | `batch-operation/batch-operations-suspend-api-tests.spec.ts:35` | Suspend active batch operation returns 204 and status becomes SUSPENDED, finally resumes |
| mutate | unlabeled | `batch-operation/batch-operations-suspend-api-tests.spec.ts:58` | Suspend batch operation twice fails on second request, finally resumes |
| mutate | observe-via-get | `batch-operation/batch-operations-suspend-api-tests.spec.ts:86` | Suspend finished batch operation returns 404 |
| mutate | observe-via-get | `batch-operation/batch-operations-suspend-api-tests.spec.ts:96` | Suspend batch operation with unknown key returns 404 |
| mutate | observe-via-get | `batch-operation/batch-operations-suspend-api-tests.spec.ts:119` | Suspend batch operation without auth returns 401 |
| mutate | observe-via-get | `batch-operation/batch-operations-suspend-api-tests.spec.ts:136` | Suspend active batch operation returns 204 and status becomes SUSPENDED without resume |
| delete | observe-via-get | `batch-operation/batch-operations-cancel-api-tests.spec.ts:40` | Cancel active batch operation returns 204 and status becomes CANCELED |
| delete | unlabeled | `batch-operation/batch-operations-cancel-api-tests.spec.ts:58` | Cancel batch operation twice fails on second request |
| delete | observe-via-get | `batch-operation/batch-operations-cancel-api-tests.spec.ts:81` | Cancel finished batch operation returns 404 |
| delete | observe-via-get | `batch-operation/batch-operations-cancel-api-tests.spec.ts:93` | Cancel batch operation with unknown key returns 404 |
| delete | observe-via-get | `batch-operation/batch-operations-cancel-api-tests.spec.ts:124` | Cancel batch operation without auth returns 401 |
| negative-get | not-found | `batch-operation/batch-operation-get-api.spec.ts:72` | Get Batch Operation - Not Found |
| negative-get | unauthorized | `batch-operation/batch-operation-get-api.spec.ts:87` | Get Batch Operation - Unauthorized |
| negative-search | unauthorized | `batch-operation/batch-operation-items-search-api.spec.ts:527` | Search Batch Operation Items - Unauthorized Request |
| negative-search | bad-request | `batch-operation/batch-operation-items-search-api.spec.ts:543` | Search Batch Operation Items - Empty Result |
| negative-search | bad-request, filter | `batch-operation/batch-operation-items-search-api.spec.ts:574` | Search Batch Operation Items - Bad Request - invalid filter field |
| negative-search | bad-request, filter | `batch-operation/batch-operation-items-search-api.spec.ts:598` | Search Batch Operation Items - Bad Request - invalid state filter value |
| negative-search | bad-request, filter | `batch-operation/batch-operation-items-search-api.spec.ts:622` | Search Batch Operation Items - Bad Request - invalid itemKey filter value |
| negative-search | bad-request | `batch-operation/batch-operations-search-api.spec.ts:160` | Search Batch Operations Empty Result |
| negative-search | unauthorized | `batch-operation/batch-operations-search-api.spec.ts:186` | Search Batch Operations Unauthorized |
| negative-search | bad-request, pagination-sort | `batch-operation/batch-operations-search-api.spec.ts:194` | Search Batch Operations Invalid Pagination |
| negative-search | bad-request, pagination-sort | `batch-operation/batch-operations-search-api.spec.ts:211` | Search Batch Operations Invalid Sort Field |
| negative-mutate | bad-request, observe-via-get | `batch-operation/batch-operations-suspend-api-tests.spec.ts:104` | Suspend batch operation with invalid key returns 400 |
| negative-delete | bad-request, observe-via-get | `batch-operation/batch-operations-cancel-api-tests.spec.ts:107` | Cancel batch operation with invalid key format returns 400 |

## F. User-Task Lifecycle

**Form**: Deploy process w/ user task (prerequisite) → Create instance → Assign → Update → Search/Get → Get form → Search variables → Complete → Unassign

**Total tests**: 51

### `user-task` — 51 tests

- **Prerequisite to create**: running-process-instance-with-user-task
- **Files**: `user-task/user-task-assign-api-tests.spec.ts`, `user-task/user-task-complete-api-tests.spec.ts`, `user-task/user-task-get-api-tests.spec.ts`, `user-task/user-task-get-form-api-tests.spec.ts`, `user-task/user-task-search-api-tests.spec.ts`, `user-task/user-task-search-variables-api-tests.spec.ts`, `user-task/user-task-unassign-api-tests.spec.ts`, `user-task/user-task-update-api-tests.spec.ts`
- **Observation channel**: GET = 8, Search = 14
- **Form-step counts**: observe-present-get=3, observe-present-search=8, mutate=14, delete=5, negative-get=5, negative-search=5, negative-mutate=9, negative-delete=2
- **Variants**: happy-path=20, observe-via-get=8, observe-via-search=14, pagination-sort=1, filter=4, bad-request=10, unauthorized=8, not-found=6, conflict=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `user-task/user-task-get-api-tests.spec.ts:31` | Get user task - success |
| observe-present-get | happy-path | `user-task/user-task-get-form-api-tests.spec.ts:52` | Get user task form - success |
| observe-present-get | happy-path | `user-task/user-task-get-form-api-tests.spec.ts:115` | Get user task form - success - task with no form |
| observe-present-search | happy-path | `user-task/user-task-search-api-tests.spec.ts:31` | Search user tasks - success |
| observe-present-search | filter | `user-task/user-task-search-api-tests.spec.ts:76` | Search user task - filter by processInstanceKey |
| observe-present-search | filter | `user-task/user-task-search-api-tests.spec.ts:99` | Search user task - filter by processInstanceKey and name |
| observe-present-search | — | `user-task/user-task-search-api-tests.spec.ts:126` | Search user task - find no tasks |
| observe-present-search | happy-path | `user-task/user-task-search-variables-api-tests.spec.ts:35` | Search user task variables - success |
| observe-present-search | pagination-sort, happy-path | `user-task/user-task-search-variables-api-tests.spec.ts:64` | Search user task variables sort desc - success |
| observe-present-search | filter | `user-task/user-task-search-variables-api-tests.spec.ts:160` | Search user task variables - filter by variable name |
| observe-present-search | filter | `user-task/user-task-search-variables-api-tests.spec.ts:196` | Search user task variables - advanced filter by variable name with wildcard |
| mutate | happy-path | `user-task/user-task-assign-api-tests.spec.ts:32` | Assign user task - success |
| mutate | not-found | `user-task/user-task-assign-api-tests.spec.ts:90` | Assign user task - not found |
| mutate | happy-path | `user-task/user-task-assign-api-tests.spec.ts:107` | Double Assign user task - success |
| mutate | observe-via-search | `user-task/user-task-complete-api-tests.spec.ts:33` | Search, complete and verify completion |
| mutate | not-found | `user-task/user-task-complete-api-tests.spec.ts:96` | Complete user task - not found |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:61` | Update user task - success - update all fields |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:83` | Update user task - success - update only dueDate |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:102` | Update user task - success - update only followUpDate |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:121` | Update user task - success - update only candidateUsers |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:140` | Update user task - success - update only candidateGroups |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:159` | Update user task - success - update only priority |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:235` | Update user task - success - with custom action |
| mutate | happy-path | `user-task/user-task-update-api-tests.spec.ts:253` | Update user task - success - verify updated fields |
| mutate | not-found | `user-task/user-task-update-api-tests.spec.ts:356` | Update user task - not found |
| delete | happy-path | `user-task/user-task-unassign-api-tests.spec.ts:32` | Unassign user task - success |
| delete | not-found | `user-task/user-task-unassign-api-tests.spec.ts:82` | Unassign user task - not found |
| delete | happy-path | `user-task/user-task-unassign-api-tests.spec.ts:115` | Double Unassign user task - success |
| delete | happy-path | `user-task/user-task-update-api-tests.spec.ts:178` | Update user task - success - reset dueDate and followUpDate |
| delete | happy-path | `user-task/user-task-update-api-tests.spec.ts:198` | Update user task - success - reset candidateUsers and candidateGroups |
| negative-get | not-found | `user-task/user-task-get-api-tests.spec.ts:51` | Get user task - not found |
| negative-get | unauthorized | `user-task/user-task-get-api-tests.spec.ts:65` | Get user task - unauthorized |
| negative-get | unauthorized | `user-task/user-task-get-form-api-tests.spec.ts:144` | Get user task form - unauthorized |
| negative-get | bad-request | `user-task/user-task-get-form-api-tests.spec.ts:159` | Get user task form - bad request - invalid user task key |
| negative-get | not-found | `user-task/user-task-get-form-api-tests.spec.ts:175` | Get user task form - not found - non existing user task |
| negative-search | unauthorized | `user-task/user-task-search-api-tests.spec.ts:50` | Search user tasks - unauthorized |
| negative-search | bad-request | `user-task/user-task-search-api-tests.spec.ts:60` | Search user tasks - bad request - invalid payload |
| negative-search | unauthorized | `user-task/user-task-search-variables-api-tests.spec.ts:101` | Search user task variables - unauthorized |
| negative-search | bad-request | `user-task/user-task-search-variables-api-tests.spec.ts:119` | Search user task variables - bad request - invalid payload |
| negative-search | bad-request | `user-task/user-task-search-variables-api-tests.spec.ts:143` | Search user task variables - bad request - invalid user task key |
| negative-mutate | bad-request | `user-task/user-task-assign-api-tests.spec.ts:51` | Assign user task - bad request - invalid payload |
| negative-mutate | unauthorized | `user-task/user-task-assign-api-tests.spec.ts:69` | Assign user task - unauthorized |
| negative-mutate | bad-request | `user-task/user-task-complete-api-tests.spec.ts:82` | Complete user task - bad request - invalid payload |
| negative-mutate | unauthorized | `user-task/user-task-complete-api-tests.spec.ts:105` | Complete user task - unauthorized |
| negative-mutate | bad-request | `user-task/user-task-update-api-tests.spec.ts:218` | Update user task - bad request - empty changeset |
| negative-mutate | bad-request | `user-task/user-task-update-api-tests.spec.ts:298` | Update user task - bad request - invalid priority above max |
| negative-mutate | bad-request | `user-task/user-task-update-api-tests.spec.ts:317` | Update user task - bad request - invalid priority below min |
| negative-mutate | unauthorized | `user-task/user-task-update-api-tests.spec.ts:336` | Update user task - unauthorized |
| negative-mutate | conflict | `user-task/user-task-update-api-tests.spec.ts:375` | Update user task - conflict - task already completed |
| negative-delete | unauthorized | `user-task/user-task-unassign-api-tests.spec.ts:64` | Unassign user task - unauthorized |
| negative-delete | bad-request | `user-task/user-task-unassign-api-tests.spec.ts:99` | Unassign user task - bad request - invalid user task key |

## G. Job Lifecycle & Stats

**Form**: Deploy process w/ job (prerequisite) → Activate → Complete / Fail / Error / Update → Search jobs → Aggregate (5 statistics endpoints)

**Total tests**: 57

### `job` — 57 tests

- **Prerequisite to create**: running-process-instance-with-job
- **Files**: `job/el-header-basic-tests.spec.ts`, `job/el-header-cross-task-tests.spec.ts`, `job/el-header-element-types-tests.spec.ts`, `job/el-header-merge-conflict-tests.spec.ts`, `job/job-api-tests.spec.ts`, `job/job-completion-api-tests.spec.ts`, `job/job-error-api-tests.spec.ts`, `job/job-failure-api-tests.spec.ts`, `job/job-statistics-by-type-api-tests.spec.ts`, `job/job-statistics-by-worker-api-tests.spec.ts`, `job/job-statistics-error-metrics-job-type-api-tests.spec.ts`, `job/job-statistics-global-api-tests.spec.ts`, `job/job-statistics-time-series-job-type-api-tests.spec.ts`, `job/job-update-api-tests.spec.ts`, `job/job-worker-statistics-test-setup.spec.ts`
- **Observation channel**: GET = 26, Search = 4
- **Form-step counts**: create=1, observe-present-search=2, mutate=9, aggregate=7, negative-search=2, negative-mutate=6, negative-aggregate=19, other=11
- **Variants**: happy-path=14, observe-via-get=26, observe-via-search=4, pagination-sort=1, filter=2, bad-request=23, unauthorized=6, forbidden=5, not-found=5, conflict=2, unlabeled=9

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `job/job-worker-statistics-test-setup.spec.ts:15` | Setup - Create jobs with different workers and types |
| observe-present-search | — | `job/job-api-tests.spec.ts:160` | Search Jobs - no criteria |
| observe-present-search | pagination-sort | `job/job-api-tests.spec.ts:185` | Search Jobs - sorted by field 'type' |
| mutate | happy-path | `job/job-completion-api-tests.spec.ts:52` | Complete Job - success |
| mutate | not-found | `job/job-completion-api-tests.spec.ts:66` | Complete Job - not found |
| mutate | happy-path | `job/job-error-api-tests.spec.ts:45` | Throw Error for Job - success |
| mutate | not-found | `job/job-error-api-tests.spec.ts:60` | Throw Error for Job - not found |
| mutate | happy-path | `job/job-failure-api-tests.spec.ts:44` | Fail Job - success |
| mutate | not-found | `job/job-failure-api-tests.spec.ts:61` | Fail Job - Job not found |
| mutate | unlabeled | `job/job-failure-api-tests.spec.ts:90` | Fail Job - 409 |
| mutate | happy-path | `job/job-update-api-tests.spec.ts:44` | Update Job - success |
| mutate | not-found | `job/job-update-api-tests.spec.ts:60` | Update Job - not found |
| aggregate | observe-via-get, happy-path | `job/job-statistics-by-type-api-tests.spec.ts:60` | Get Job Statistics By Type - success |
| aggregate | observe-via-get, happy-path | `job/job-statistics-by-worker-api-tests.spec.ts:59` | Get Job Statistics By Worker - success |
| aggregate | observe-via-get, happy-path | `job/job-statistics-error-metrics-job-type-api-tests.spec.ts:58` | Get error metrics for a job type - success |
| aggregate | observe-via-get, happy-path | `job/job-statistics-global-api-tests.spec.ts:59` | Get Job Statistics - success |
| aggregate | observe-via-get, happy-path | `job/job-statistics-global-api-tests.spec.ts:87` | Get Job Statistics with jobtype - success |
| aggregate | observe-via-get, happy-path | `job/job-statistics-time-series-job-type-api-tests.spec.ts:61` | Get time-series metrics for a job type - success |
| aggregate | observe-via-get, happy-path | `job/job-statistics-time-series-job-type-api-tests.spec.ts:211` | Get time-series metrics for a job type with no resolution parameter - Success |
| negative-search | unauthorized | `job/job-api-tests.spec.ts:221` | Search Jobs - Unauthorized |
| negative-search | bad-request | `job/job-api-tests.spec.ts:231` | Search Jobs - invalid request |
| negative-mutate | bad-request | `job/job-completion-api-tests.spec.ts:84` | Complete Job - invalid request |
| negative-mutate | conflict | `job/job-completion-api-tests.spec.ts:101` | Complete Job - conflict 409 |
| negative-mutate | bad-request | `job/job-error-api-tests.spec.ts:76` | Throw Error for Job - invalid request |
| negative-mutate | conflict | `job/job-error-api-tests.spec.ts:89` | Throw Error for Job - conflict 409 |
| negative-mutate | bad-request | `job/job-failure-api-tests.spec.ts:77` | Fail Job - invalid request |
| negative-mutate | bad-request | `job/job-update-api-tests.spec.ts:79` | Update Job - invalid request |
| negative-aggregate | bad-request, filter, observe-via-get | `job/job-statistics-by-type-api-tests.spec.ts:144` | Get Job Statistics By Type With Wrong Filter Parameter - Bad Request |
| negative-aggregate | bad-request, filter, observe-via-get | `job/job-statistics-by-type-api-tests.spec.ts:163` | Get Job Statistics By Type With Missing Required From Filter Parameter - Bad Request |
| negative-aggregate | bad-request, observe-via-get, happy-path | `job/job-statistics-by-type-api-tests.spec.ts:178` | Get Job Statistics By Type With Not Existing Job Type - Success Empty Result |
| negative-aggregate | unauthorized, observe-via-get | `job/job-statistics-by-type-api-tests.spec.ts:206` | Get Job Statistics By Type - Unauthorized |
| negative-aggregate | forbidden, bad-request, observe-via-get | `job/job-statistics-by-type-api-tests.spec.ts:219` | Get Job Statistics By Type - Forbidden, Empty Result, 200 |
| negative-aggregate | not-found, bad-request, observe-via-get, happy-path | `job/job-statistics-by-worker-api-tests.spec.ts:98` | Get Job Statistics By Worker with non existing job type - success, empty result |
| negative-aggregate | bad-request, observe-via-get | `job/job-statistics-by-worker-api-tests.spec.ts:127` | Get Job Statistics By Worker no jobType parameter - Bad Request |
| negative-aggregate | unauthorized, observe-via-get | `job/job-statistics-by-worker-api-tests.spec.ts:142` | Get Job Statistics By Worker - Unauthorized |
| negative-aggregate | forbidden, bad-request, observe-via-get | `job/job-statistics-by-worker-api-tests.spec.ts:156` | Get Job Statistics By Worker  - Forbidden, empty result |
| negative-aggregate | bad-request, observe-via-get | `job/job-statistics-error-metrics-job-type-api-tests.spec.ts:129` | Get error metrics for a job type without jobType - Bad Request |
| negative-aggregate | forbidden, bad-request, observe-via-get | `job/job-statistics-error-metrics-job-type-api-tests.spec.ts:144` | Get error metrics for a job type - Forbidden, empty result |
| negative-aggregate | bad-request, observe-via-get | `job/job-statistics-global-api-tests.spec.ts:115` | Get Job Statistics no from parameter - Bad Request |
| negative-aggregate | bad-request, observe-via-get | `job/job-statistics-global-api-tests.spec.ts:131` | Get Job Statistics no to parameter - Bad Request |
| negative-aggregate | unauthorized, observe-via-get | `job/job-statistics-global-api-tests.spec.ts:143` | Get Job Statistics - Unauthorized |
| negative-aggregate | forbidden, bad-request, observe-via-get | `job/job-statistics-global-api-tests.spec.ts:156` | Get Job Statistics - Forbidden, Empty Result, 200 |
| negative-aggregate | unauthorized, observe-via-get | `job/job-statistics-time-series-job-type-api-tests.spec.ts:151` | Get time-series metrics for a job type - Unauthorized |
| negative-aggregate | bad-request, observe-via-get, happy-path | `job/job-statistics-time-series-job-type-api-tests.spec.ts:168` | Get time-series metrics for a not existing job type - Success, empty result |
| negative-aggregate | bad-request, observe-via-get | `job/job-statistics-time-series-job-type-api-tests.spec.ts:195` | Get time-series metrics for a job type with no jobtype parameter - Bad Request |
| negative-aggregate | forbidden, bad-request, observe-via-get | `job/job-statistics-time-series-job-type-api-tests.spec.ts:299` | Get time-series metrics for a job type - Forbidden, empty result |
| other | unlabeled | `job/el-header-basic-tests.spec.ts:45` | As a developer, I can define headers on a start EL and verify the job worker receives them |
| other | unlabeled | `job/el-header-basic-tests.spec.ts:83` | As a developer, I can define headers on an end EL and verify they are delivered after the main task completes |
| other | unlabeled | `job/el-header-cross-task-tests.spec.ts:47` | As a developer, I can verify that EL headers defined on a Call Activity do not appear in the EL jobs of the called process |
| other | unlabeled | `job/el-header-element-types-tests.spec.ts:46` | As a developer, I can define a start EL with headers on an Exclusive Gateway and verify the job worker receives them |
| other | unlabeled | `job/el-header-element-types-tests.spec.ts:64` | As a developer, I can define start and end EL headers on an Embedded Subprocess and verify both are delivered correctly |
| other | unlabeled | `job/el-header-merge-conflict-tests.spec.ts:33` | As a developer, I can verify that when a header key is defined on both the base element and the EL, the EL value takes precedence |
| other | unlabeled | `job/job-api-tests.spec.ts:60` | Activate Jobs - only required fields |
| other | unauthorized | `job/job-api-tests.spec.ts:110` | Activate Jobs - unauthorized |
| other | bad-request | `job/job-api-tests.spec.ts:121` | Activate Jobs - invalid type |
| other | bad-request | `job/job-api-tests.spec.ts:133` | Activate Jobs - invalid timeout |
| other | bad-request | `job/job-api-tests.spec.ts:144` | Activate Jobs - invalid maxJobsToActivate |

## H. Incident Lifecycle

**Form**: Deploy process + failing job (prerequisite) → Incident raised → Get incident → Search → Resolve → Statistics (by definition / by error)

**Total tests**: 28

### `incident` — 28 tests

- **Prerequisite to create**: running-process-instance-with-failing-job
- **Files**: `incident/get-process-instance-statistics-by-definition-api-tests.spec.ts`, `incident/get-process-instance-statistics-by-error-api-tests.spec.ts`, `incident/incident-get-api.spec.ts`, `incident/incident-resolve-api.spec.ts`, `incident/incident-search-api.spec.ts`
- **Observation channel**: GET = 14, Search = 9
- **Form-step counts**: observe-present-get=1, observe-present-search=4, mutate=5, aggregate=3, negative-get=3, negative-search=3, negative-mutate=2, negative-aggregate=7
- **Variants**: happy-path=11, observe-via-get=14, observe-via-search=9, pagination-sort=4, filter=4, bad-request=9, unauthorized=5, forbidden=2, not-found=2

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `incident/incident-get-api.spec.ts:42` | Get Incident Success |
| observe-present-search | happy-path | `incident/incident-search-api.spec.ts:45` | Search Incidents Success |
| observe-present-search | happy-path | `incident/incident-search-api.spec.ts:73` | Search Incidents within multiple process instances Success |
| observe-present-search | filter, happy-path | `incident/incident-search-api.spec.ts:105` | Search Incidents With IncidentKey Filter Success |
| observe-present-search | pagination-sort | `incident/incident-search-api.spec.ts:336` | Search Incident Pagination Limit 1 |
| mutate | happy-path | `incident/incident-resolve-api.spec.ts:46` | Resolve Incident success |
| mutate | not-found | `incident/incident-resolve-api.spec.ts:144` | Resolve Incident - not found |
| mutate | happy-path | `incident/incident-resolve-api.spec.ts:195` | Resolve Incident with a job - success |
| mutate | filter, observe-via-search, happy-path | `incident/incident-search-api.spec.ts:142` | Search Incidents With Error Type Filter Success |
| mutate | filter, observe-via-search, happy-path | `incident/incident-search-api.spec.ts:204` | Search Incidents With Error Type Filter and Process Instance Key Success |
| aggregate | observe-via-get, happy-path | `incident/get-process-instance-statistics-by-definition-api-tests.spec.ts:78` | Get Process Instance Statistics By Definition - Success |
| aggregate | observe-via-get, happy-path | `incident/get-process-instance-statistics-by-error-api-tests.spec.ts:83` | Get Statistics For Process Instances with errors - Success |
| aggregate | pagination-sort, observe-via-get | `incident/get-process-instance-statistics-by-error-api-tests.spec.ts:138` | Get Process Instance Statistics By activeInstancesWithErrorCount sort ASC by error message |
| negative-get | unauthorized | `incident/incident-get-api.spec.ts:74` | Get Incidents Unauthorized |
| negative-get | not-found | `incident/incident-get-api.spec.ts:97` | Get Incidents Not Found |
| negative-get | bad-request | `incident/incident-get-api.spec.ts:111` | Get Incidents Invalid Value |
| negative-search | unauthorized | `incident/incident-search-api.spec.ts:274` | Search Incidents Unauthorized |
| negative-search | bad-request, filter | `incident/incident-search-api.spec.ts:287` | Search Incidents Invalid Filter |
| negative-search | bad-request, happy-path | `incident/incident-search-api.spec.ts:305` | Search Incidents Empty Result success |
| negative-mutate | bad-request | `incident/incident-resolve-api.spec.ts:161` | Resolve Incident - bad request |
| negative-mutate | unauthorized | `incident/incident-resolve-api.spec.ts:178` | Resolve Incident - unauthorized |
| negative-aggregate | unauthorized, observe-via-get | `incident/get-process-instance-statistics-by-definition-api-tests.spec.ts:182` | Get Process Instance Statistics By Definition - Unauthorized |
| negative-aggregate | bad-request, observe-via-get | `incident/get-process-instance-statistics-by-definition-api-tests.spec.ts:203` | Get Process Instance Statistics By Definition - Bad Request |
| negative-aggregate | forbidden, bad-request, observe-via-get | `incident/get-process-instance-statistics-by-definition-api-tests.spec.ts:224` | Get Process Instance Statistics By Definition - Forbidden, empty result, 200 |
| negative-aggregate | bad-request, pagination-sort, observe-via-get | `incident/get-process-instance-statistics-by-error-api-tests.spec.ts:197` | Get Process Instance Statistics By Error with negative page limit - Bad Request |
| negative-aggregate | bad-request, pagination-sort, observe-via-get | `incident/get-process-instance-statistics-by-error-api-tests.spec.ts:221` | Get Process Instance Statistics By Error Invalid Sort Field - Bad Request |
| negative-aggregate | unauthorized, observe-via-get | `incident/get-process-instance-statistics-by-error-api-tests.spec.ts:245` | Get Process Instance Statistics By Error - Unauthorized |
| negative-aggregate | forbidden, bad-request, observe-via-get | `incident/get-process-instance-statistics-by-error-api-tests.spec.ts:259` | Get Process Instance Statistics By Error - Forbidden, 200, empty result |

## I. Decision-Instance Lifecycle

**Form**: Deploy DRD/DMN (prerequisite) → Evaluate → Get instance → Search → Delete (single + batch) → Search (Observe Absence)

**Total tests**: 23

### `decision-instance` — 23 tests

- **Prerequisite to create**: deployed-decision
- **Files**: `decision-instance/decision-instance-batch-delete-api.spec.ts`, `decision-instance/decision-instance-delete-api.spec.ts`, `decision-instance/decision-instances-get-api.spec.ts`, `decision-instance/decision-instances-search-api.spec.ts`
- **Observation channel**: GET = 4, Search = 9
- **Form-step counts**: observe-present-get=1, observe-present-search=6, delete=3, negative-create=5, negative-get=3, negative-search=3, negative-delete=2
- **Variants**: happy-path=3, observe-via-get=4, observe-via-search=9, filter=7, bad-request=6, unauthorized=4, forbidden=2, not-found=3, unlabeled=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `decision-instance/decision-instances-get-api.spec.ts:38` | Get Decision Instance - Success |
| observe-present-search | happy-path | `decision-instance/decision-instances-search-api.spec.ts:37` | Search decision instances - multiple results - success |
| observe-present-search | filter | `decision-instance/decision-instances-search-api.spec.ts:65` | Search decision instances - filter by state |
| observe-present-search | filter | `decision-instance/decision-instances-search-api.spec.ts:99` | Search decision instances - filter by decisionEvaluationInstanceKey |
| observe-present-search | filter | `decision-instance/decision-instances-search-api.spec.ts:139` | Search decision instances - filter by processInstanceKey |
| observe-present-search | filter | `decision-instance/decision-instances-search-api.spec.ts:175` | Search decision instances - filter by decisionDefinitionId |
| observe-present-search | filter | `decision-instance/decision-instances-search-api.spec.ts:212` | Search decision by multiple filters: processInstanceKey and decisionDefinitionId |
| delete | unlabeled | `decision-instance/decision-instance-batch-delete-api.spec.ts:72` | Delete Decision Instance With Batch |
| delete | happy-path | `decision-instance/decision-instance-delete-api.spec.ts:88` | Delete Decision Instance - Success |
| delete | not-found | `decision-instance/decision-instance-delete-api.spec.ts:144` | Delete Decision Instance - Not Found |
| negative-create | bad-request, filter | `decision-instance/decision-instance-batch-delete-api.spec.ts:174` | Create a Batch Operation to Delete Decision Instances With No Filter - Bad Request |
| negative-create | bad-request, filter | `decision-instance/decision-instance-batch-delete-api.spec.ts:186` | Create a Batch Operation to Delete Decision Instances - With Invalid Filter - Bad Request |
| negative-create | not-found, bad-request | `decision-instance/decision-instance-batch-delete-api.spec.ts:203` | Create a Batch Operation to Delete Decision Instances With Non-Existing Keys - 200 but batch operation is empty |
| negative-create | unauthorized | `decision-instance/decision-instance-batch-delete-api.spec.ts:262` | Create a Batch Operation to Delete Decision Instances - Unauthorized |
| negative-create | forbidden | `decision-instance/decision-instance-batch-delete-api.spec.ts:278` | Create a Batch Operation to Delete Decision Instances - Forbidden |
| negative-get | not-found | `decision-instance/decision-instances-get-api.spec.ts:73` | Get Decision Instance - Not found |
| negative-get | unauthorized | `decision-instance/decision-instances-get-api.spec.ts:90` | Get Decision Instances - Unauthorized |
| negative-get | bad-request | `decision-instance/decision-instances-get-api.spec.ts:109` | Get Decision Instance - Bad Request |
| negative-search | bad-request | `decision-instance/decision-instances-search-api.spec.ts:252` | Search decision instances - empty result |
| negative-search | bad-request | `decision-instance/decision-instances-search-api.spec.ts:284` | Search decision instances - invalid request - bad request |
| negative-search | unauthorized | `decision-instance/decision-instances-search-api.spec.ts:308` | Search decision instances - unauthorized request |
| negative-delete | forbidden | `decision-instance/decision-instance-delete-api.spec.ts:64` | Delete Decision Instance - Forbidden |
| negative-delete | unauthorized | `decision-instance/decision-instance-delete-api.spec.ts:128` | Delete Decision Instance - Unauthorized |

## J/K/L. Observation-only

**Form**: Perform an action elsewhere (prerequisite) → Get / Search to observe

**Total tests**: 66

### `element-instance` — 35 tests

- **Prerequisite to create**: running-process-instance
- **Files**: `element-instance/element-instance-ad-hoc-activities-api.spec.ts`, `element-instance/element-instance-get-api.spec.ts`, `element-instance/element-instance-search-api.spec.ts`, `element-instance/element-instance-search-incident-api.spec.ts`, `element-instance/element-instance-update-api.spec.ts`
- **Observation channel**: GET = 6, Search = 24
- **Form-step counts**: observe-present-get=1, observe-present-search=14, mutate=2, negative-get=3, negative-search=10, other=5
- **Variants**: happy-path=7, observe-via-get=6, observe-via-search=24, pagination-sort=5, filter=11, bad-request=8, unauthorized=4, forbidden=1, not-found=3, unlabeled=5

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `element-instance/element-instance-get-api.spec.ts:36` | Get Element Instance - Success |
| observe-present-search | happy-path | `element-instance/element-instance-search-api.spec.ts:54` | Search Element Instances - Success |
| observe-present-search | pagination-sort | `element-instance/element-instance-search-api.spec.ts:75` | Search Element Instances - Page Limit 1 |
| observe-present-search | pagination-sort | `element-instance/element-instance-search-api.spec.ts:101` | Search Element Instances - Sort by state ASC |
| observe-present-search | filter | `element-instance/element-instance-search-api.spec.ts:132` | Search Element Instances - filter by processDefinitionId |
| observe-present-search | filter | `element-instance/element-instance-search-api.spec.ts:164` | Search Element Instances - filter by elementName |
| observe-present-search | filter | `element-instance/element-instance-search-api.spec.ts:196` | Search Element Instances - filter by type |
| observe-present-search | filter | `element-instance/element-instance-search-api.spec.ts:226` | Search Element Instances - filter by processDefinitionKey |
| observe-present-search | filter | `element-instance/element-instance-search-api.spec.ts:258` | Search Element Instances - filter by processInstanceKey |
| observe-present-search | filter | `element-instance/element-instance-search-api.spec.ts:290` | Search Element Instances - Multiple Filters |
| observe-present-search | happy-path | `element-instance/element-instance-search-incident-api.spec.ts:115` | Search for incidents of a specific element instance - Multiple Results - Success |
| observe-present-search | filter, happy-path | `element-instance/element-instance-search-incident-api.spec.ts:145` | Search for incidents of a specific element instance - filtered by errorType - Single Result - Success |
| observe-present-search | filter, happy-path | `element-instance/element-instance-search-incident-api.spec.ts:180` | Search for incidents of a specific element instance - filtered by processDefinitionKey and state - Single Result - Success |
| observe-present-search | happy-path | `element-instance/element-instance-search-incident-api.spec.ts:219` | Search for incidents of a specific element instance - ascending order by errorMessage - Success |
| observe-present-search | not-found | `element-instance/element-instance-search-incident-api.spec.ts:347` | Search for incidents of a specific element instance - Not Existing Element Instance Key - Not Found |
| mutate | unlabeled | `element-instance/element-instance-update-api.spec.ts:41` | Update Element Instance - so that process ends with the "end without extra" end event |
| mutate | unlabeled | `element-instance/element-instance-update-api.spec.ts:93` | Update Element Instance - local update overrides global variable |
| negative-get | unauthorized | `element-instance/element-instance-get-api.spec.ts:73` | Get Element Instance - Unauthorized |
| negative-get | not-found | `element-instance/element-instance-get-api.spec.ts:83` | Get Element Instance - Not Found |
| negative-get | bad-request | `element-instance/element-instance-get-api.spec.ts:93` | Get Element Instance - Invalid Key Format |
| negative-search | bad-request, filter | `element-instance/element-instance-search-api.spec.ts:321` | Search Element Instances - Invalid Filter |
| negative-search | bad-request, pagination-sort | `element-instance/element-instance-search-api.spec.ts:338` | Search Element Instances - Invalid Sort Field |
| negative-search | bad-request, pagination-sort | `element-instance/element-instance-search-api.spec.ts:359` | Search Element Instances - with invalid pagination parameters |
| negative-search | unauthorized | `element-instance/element-instance-search-api.spec.ts:379` | Search Element Instances - Unauthorized |
| negative-search | bad-request, happy-path | `element-instance/element-instance-search-incident-api.spec.ts:266` | Search for incidents of a specific element instance - Empty Result - Success |
| negative-search | bad-request, filter | `element-instance/element-instance-search-incident-api.spec.ts:298` | Search for incidents of a specific element instance - Wrong Filter Value - Bad Request |
| negative-search | bad-request, filter | `element-instance/element-instance-search-incident-api.spec.ts:323` | Search for incidents of a specific element instance - Wrong Filter Field - Bad Request |
| negative-search | unauthorized | `element-instance/element-instance-search-incident-api.spec.ts:368` | Search for incidents of a specific element instance - Unauthorized |
| negative-search | forbidden | `element-instance/element-instance-search-incident-api.spec.ts:385` | Search for incidents of a specific element instance - Forbidden |
| negative-search | bad-request, pagination-sort | `element-instance/element-instance-search-incident-api.spec.ts:437` | Search for incidents of a specific element instance - with invalid pagination parameters |
| other | unlabeled | `element-instance/element-instance-ad-hoc-activities-api.spec.ts:77` | Activate AdHoc Activities SucceedsWithValidElements |
| other | unlabeled | `element-instance/element-instance-ad-hoc-activities-api.spec.ts:108` | Activate AdHoc Activities SucceedsWithVariablesAndCancel |
| other | unlabeled | `element-instance/element-instance-ad-hoc-activities-api.spec.ts:143` | Activate AdHoc Activities FailsWithMissingElements |
| other | unauthorized, observe-via-get | `element-instance/element-instance-ad-hoc-activities-api.spec.ts:171` | Activate AdHoc Activities ReturnsUnauthorizedWithoutAuth |
| other | not-found, observe-via-get | `element-instance/element-instance-ad-hoc-activities-api.spec.ts:205` | Activate AdHoc Activities ReturnsNotFoundForRandomKey |

### `audit-log` — 17 tests

- **Prerequisite to create**: any-prior-action
- **Files**: `audit-log/audit-log-get-api-tests.spec.ts`, `audit-log/audit-log-search-api-tests.spec.ts`
- **Observation channel**: GET = 4, Search = 11
- **Form-step counts**: observe-present-get=1, observe-present-search=4, negative-get=3, negative-search=7, parameterized=2
- **Variants**: happy-path=2, observe-via-get=4, observe-via-search=11, pagination-sort=6, filter=4, bad-request=5, unauthorized=2, forbidden=2, not-found=1, data-driven=2

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `audit-log/audit-log-get-api-tests.spec.ts:27` | Get Audit Logs Success |
| observe-present-search | happy-path | `audit-log/audit-log-search-api-tests.spec.ts:232` | Search Audit Logs Success |
| observe-present-search | pagination-sort | `audit-log/audit-log-search-api-tests.spec.ts:277` | Search Audit Logs with page limit |
| observe-present-search | pagination-sort, filter | `audit-log/audit-log-search-api-tests.spec.ts:302` | Search Audit Logs - Filter by operationType and sort by timestamp DESC |
| observe-present-search | pagination-sort, filter | `audit-log/audit-log-search-api-tests.spec.ts:342` | Search Audit Logs - Filter by actorType, sort by actorId, with page limit |
| negative-get | not-found | `audit-log/audit-log-get-api-tests.spec.ts:76` | Get Audit Logs - Not Found |
| negative-get | unauthorized | `audit-log/audit-log-get-api-tests.spec.ts:91` | Get Audit Logs - Unauthorized |
| negative-get | forbidden | `audit-log/audit-log-get-api-tests.spec.ts:98` | Get Audit Logs - Forbidden |
| negative-search | bad-request, filter | `audit-log/audit-log-search-api-tests.spec.ts:250` | Search Audit Logs - Filter by actorId - Empty result |
| negative-search | bad-request, pagination-sort | `audit-log/audit-log-search-api-tests.spec.ts:387` | Search Audit Logs - Invalid sort field |
| negative-search | bad-request, pagination-sort | `audit-log/audit-log-search-api-tests.spec.ts:408` | Search Audit Logs - Null sort field |
| negative-search | bad-request, filter | `audit-log/audit-log-search-api-tests.spec.ts:425` | Search Audit Logs - Invalid filter field |
| negative-search | unauthorized | `audit-log/audit-log-search-api-tests.spec.ts:443` | Search Audit Logs - Unauthorized |
| negative-search | bad-request, pagination-sort | `audit-log/audit-log-search-api-tests.spec.ts:454` | Search Audit Logs with negative page limit - Bad Request |
| negative-search | forbidden | `audit-log/audit-log-search-api-tests.spec.ts:475` | Search Audit Logs - No granted permissions |
| parameterized | data-driven | `audit-log/audit-log-search-api-tests.spec.ts:131` | <parameterized: description> |
| parameterized | data-driven | `audit-log/audit-log-search-api-tests.spec.ts:204` | <parameterized: description> |

### `variable` — 14 tests

- **Prerequisite to create**: running-process-instance
- **Files**: `variable/variable-get-api.spec.ts`, `variable/variable-search-api.spec.ts`
- **Observation channel**: GET = 4, Search = 10
- **Form-step counts**: observe-present-get=1, observe-present-search=6, negative-get=3, negative-search=4
- **Variants**: happy-path=2, observe-via-get=4, observe-via-search=10, pagination-sort=5, filter=3, bad-request=4, unauthorized=2, not-found=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | happy-path | `variable/variable-get-api.spec.ts:29` | Get Variable Success |
| observe-present-search | happy-path | `variable/variable-search-api.spec.ts:36` | Search Variables Success |
| observe-present-search | filter | `variable/variable-search-api.spec.ts:63` | Search Variables With Name Filter |
| observe-present-search | filter | `variable/variable-search-api.spec.ts:97` | Search Variables With Multiple Filters |
| observe-present-search | pagination-sort | `variable/variable-search-api.spec.ts:133` | Search Variables Pagination Limit 1 |
| observe-present-search | pagination-sort | `variable/variable-search-api.spec.ts:167` | Search Variables Sort by Name ASC |
| observe-present-search | pagination-sort | `variable/variable-search-api.spec.ts:280` | Search Variables with 0 pagination parameters |
| negative-get | not-found | `variable/variable-get-api.spec.ts:63` | Get Variable Not Found |
| negative-get | bad-request | `variable/variable-get-api.spec.ts:78` | Get Variable Invalid Key |
| negative-get | unauthorized | `variable/variable-get-api.spec.ts:90` | Get Variable Unauthorized |
| negative-search | unauthorized | `variable/variable-search-api.spec.ts:207` | Search Variables Unauthorized |
| negative-search | bad-request, filter | `variable/variable-search-api.spec.ts:220` | Search Variables Invalid Filter |
| negative-search | bad-request, pagination-sort | `variable/variable-search-api.spec.ts:238` | Search Variables Invalid Sort Field |
| negative-search | bad-request, pagination-sort | `variable/variable-search-api.spec.ts:260` | Search Variables with invalid pagination parameters |

## M. Messaging/Signals

**Form**: Deploy process with catch event (prerequisite) → Publish/Correlate/Broadcast → Search subscriptions / correlated messages

**Total tests**: 29

### `message` — 22 tests

- **Prerequisite to create**: deployed-process-with-message-catch-event
- **Files**: `message/correlate-message-api-tests.spec.ts`, `message/publish-message-api-tests.spec.ts`, `message/search-correlated-message-subscriptions-api-tests.spec.ts`, `message/search-message-subscription-api-tests.spec.ts`
- **Observation channel**: GET = 0, Search = 13
- **Form-step counts**: create=1, observe-present-search=6, mutate=2, negative-create=3, negative-search=7, negative-mutate=3
- **Variants**: happy-path=4, observe-via-search=13, pagination-sort=2, filter=3, bad-request=9, unauthorized=4, forbidden=1, not-found=1, unlabeled=2

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `message/publish-message-api-tests.spec.ts:21` | Publish Message |
| observe-present-search | happy-path | `message/search-correlated-message-subscriptions-api-tests.spec.ts:130` | Search Message Subscriptions - by message key, single result - 200 Success |
| observe-present-search | happy-path | `message/search-correlated-message-subscriptions-api-tests.spec.ts:175` | Search Message Subscriptions - multiple result - 200 Success |
| observe-present-search | happy-path | `message/search-correlated-message-subscriptions-api-tests.spec.ts:214` | Search Message Subscriptions - no result - 200 Success |
| observe-present-search | filter, happy-path | `message/search-correlated-message-subscriptions-api-tests.spec.ts:245` | Search Message Subscriptions - multiple filter - 200 Success |
| observe-present-search | pagination-sort | `message/search-correlated-message-subscriptions-api-tests.spec.ts:463` | Search Message Subscriptions - Pagination 0 |
| observe-present-search | — | `message/search-message-subscription-api-tests.spec.ts:65` | Search Message Flow |
| mutate | not-found | `message/correlate-message-api-tests.spec.ts:63` | Correlate Message Not found |
| mutate | unlabeled | `message/correlate-message-api-tests.spec.ts:94` | Correlate Message Flow |
| negative-create | unauthorized | `message/publish-message-api-tests.spec.ts:40` | Publish Message Unauthorized |
| negative-create | bad-request | `message/publish-message-api-tests.spec.ts:50` | Publish Message Bad Request |
| negative-create | bad-request | `message/publish-message-api-tests.spec.ts:58` | Publish Message Invalid Tenant |
| negative-search | bad-request, filter | `message/search-correlated-message-subscriptions-api-tests.spec.ts:322` | Search Message Subscriptions - invalid filter field - 400 Bad Request |
| negative-search | bad-request, filter | `message/search-correlated-message-subscriptions-api-tests.spec.ts:343` | Search Message Subscriptions - invalid filter value - 400 Bad Request |
| negative-search | unauthorized | `message/search-correlated-message-subscriptions-api-tests.spec.ts:365` | Search Message Subscriptions - 401 Unauthorized |
| negative-search | forbidden, bad-request | `message/search-correlated-message-subscriptions-api-tests.spec.ts:380` | Search Message Subscriptions - Forbidden search - 200 Empty Results |
| negative-search | bad-request, pagination-sort | `message/search-correlated-message-subscriptions-api-tests.spec.ts:440` | Search Message Subscriptions - Negative pagination - 400 Bad Request |
| negative-search | bad-request | `message/search-message-subscription-api-tests.spec.ts:34` | Search Message Subscriptions By Invalid Name |
| negative-search | unauthorized | `message/search-message-subscription-api-tests.spec.ts:57` | Search Subscriptions Unauthorized |
| negative-mutate | unauthorized | `message/correlate-message-api-tests.spec.ts:47` | Correlate Message Unauthorized |
| negative-mutate | bad-request | `message/correlate-message-api-tests.spec.ts:55` | Correlate Message Bad Request |
| negative-mutate | bad-request | `message/correlate-message-api-tests.spec.ts:78` | Correlate Message Invalid Tenant |

### `signal` — 5 tests

- **Prerequisite to create**: deployed-process-with-signal-catch-event
- **Files**: `signal/signal-broadcast-api.spec.ts`
- **Observation channel**: GET = 0, Search = 0
- **Form-step counts**: create=3, negative-create=2
- **Variants**: happy-path=2, bad-request=1, unauthorized=1, unlabeled=1

| form step | variants | file:line | test name |
|--|--|--|--|
| create | happy-path | `signal/signal-broadcast-api.spec.ts:45` | Broadcast Signal Success WithRequiredFieldsOnly |
| create | happy-path | `signal/signal-broadcast-api.spec.ts:71` | Broadcast Signal Success WithAllFields |
| create | unlabeled | `signal/signal-broadcast-api.spec.ts:116` | Broadcast Signal BadRequest ForMissingName |
| negative-create | unauthorized | `signal/signal-broadcast-api.spec.ts:100` | Broadcast Signal Unauthorized |
| negative-create | bad-request | `signal/signal-broadcast-api.spec.ts:131` | Broadcast Signal BadRequest ForInvalidNameType |

### `message-subscriptions` — 2 tests

- **Prerequisite to create**: deployed-process-with-message-catch-event
- **Files**: `message-subscriptions/mcp-message-subscription-search.spec.ts`
- **Observation channel**: GET = 0, Search = 2
- **Form-step counts**: observe-present-search=1, negative-search=1
- **Variants**: observe-via-search=2, unauthorized=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-search | — | `message-subscriptions/mcp-message-subscription-search.spec.ts:40` | MCP Message Subscription Search Flow |
| negative-search | unauthorized | `message-subscriptions/mcp-message-subscription-search.spec.ts:32` | Search MCP Subscriptions Unauthorized |

## N. Engine Evaluation

**Form**: Submit expression / conditional → Receive result (stateless, no entity persisted)

**Total tests**: 16

### `conditional` — 13 tests

- **Prerequisite to create**: none
- **Files**: `conditional/conditional-evaluation-api-tests.spec.ts`
- **Observation channel**: GET = 1, Search = 0
- **Form-step counts**: evaluate=13
- **Variants**: happy-path=1, observe-via-get=1, bad-request=5, unauthorized=1, unlabeled=6

| form step | variants | file:line | test name |
|--|--|--|--|
| evaluate | happy-path | `conditional/conditional-evaluation-api-tests.spec.ts:48` | Evaluate Conditional - Single Match Success |
| evaluate | unlabeled | `conditional/conditional-evaluation-api-tests.spec.ts:72` | Evaluate Conditional - Multiple Conditions Match |
| evaluate | unlabeled | `conditional/conditional-evaluation-api-tests.spec.ts:99` | Evaluate Conditional - Partial Match |
| evaluate | bad-request, observe-via-get | `conditional/conditional-evaluation-api-tests.spec.ts:125` | Evaluate Conditional - No Match Returns Empty List |
| evaluate | unlabeled | `conditional/conditional-evaluation-api-tests.spec.ts:148` | Evaluate Conditional With Tenant ID |
| evaluate | unlabeled | `conditional/conditional-evaluation-api-tests.spec.ts:170` | Evaluate Conditional By Process Definition Key |
| evaluate | unauthorized | `conditional/conditional-evaluation-api-tests.spec.ts:229` | Evaluate Conditional - Unauthorized |
| evaluate | bad-request | `conditional/conditional-evaluation-api-tests.spec.ts:240` | Evaluate Conditional - Bad Request Missing Variables |
| evaluate | bad-request | `conditional/conditional-evaluation-api-tests.spec.ts:251` | Evaluate Conditional - Invalid Tenant |
| evaluate | bad-request | `conditional/conditional-evaluation-api-tests.spec.ts:261` | Evaluate Conditional - Invalid Process Definition Key |
| evaluate | unlabeled | `conditional/conditional-evaluation-api-tests.spec.ts:273` | Evaluate Conditional - Unsupported Media Type |
| evaluate | bad-request | `conditional/conditional-evaluation-api-tests.spec.ts:285` | Evaluate Conditional - Invalid JSON |
| evaluate | unlabeled | `conditional/conditional-evaluation-api-tests.spec.ts:294` | Evaluate Conditional - Verify Response Schema |

### `expression` — 3 tests

- **Prerequisite to create**: none
- **Files**: `expression/expression-api-tests.spec.ts`
- **Observation channel**: GET = 0, Search = 0
- **Form-step counts**: parameterized=3
- **Variants**: data-driven=3

| form step | variants | file:line | test name |
|--|--|--|--|
| parameterized | data-driven | `expression/expression-api-tests.spec.ts:207` | <parameterized: tc.description> |
| parameterized | data-driven | `expression/expression-api-tests.spec.ts:225` | <parameterized: tc.description> |
| parameterized | data-driven | `expression/expression-api-tests.spec.ts:249` | <parameterized: tc.description> |

## O. System/Admin

**Form**: Read system state (auth, license, cluster, clock, metrics) or perform admin action (pin/reset clock)

**Total tests**: 22

### `optimize` — 6 tests

- **Prerequisite to create**: none
- **Files**: `optimize/default-config.spec.ts`
- **Observation channel**: GET = 0, Search = 0
- **Form-step counts**: observe-present-search=1, other=5
- **Variants**: filter=1, unlabeled=5

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-search | filter | `optimize/default-config.spec.ts:56` | should export all variables when no scope filter is configured |
| other | unlabeled | `optimize/default-config.spec.ts:106` | should export all variables when exportRootVariables=true and exportLocalVariables=true |
| other | unlabeled | `optimize/default-config.spec.ts:134` | should classify a variable as root-scope when scopeKey equals processInstanceKey |
| other | unlabeled | `optimize/default-config.spec.ts:157` | should classify a variable as local-scope when scopeKey differs from processInstanceKey |
| other | unlabeled | `optimize/default-config.spec.ts:187` | should export whitelisted local variables from each multi-instance iteration |
| other | unlabeled | `optimize/default-config.spec.ts:216` | should propagate call activity child variables to root scope in the parent process |

### `authentication` — 4 tests

- **Prerequisite to create**: authenticated-user
- **Files**: `authentication-api-tests.spec.ts`
- **Observation channel**: GET = 4, Search = 0
- **Form-step counts**: observe-present-get=3, negative-get=1
- **Variants**: observe-via-get=4, unauthorized=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | — | `authentication-api-tests.spec.ts:53` | Get Current User |
| observe-present-get | — | `authentication-api-tests.spec.ts:109` | Get Current User With Group, Tenants and Authorization |
| observe-present-get | — | `authentication-api-tests.spec.ts:210` | Get Current User Without Group, Tenants and Authorization |
| negative-get | unauthorized | `authentication-api-tests.spec.ts:101` | Get Current User Unauthorized |

### `usage-metrics` — 4 tests

- **Prerequisite to create**: metered-activity
- **Files**: `usage-metrics/usage-metrics-api.spec.ts`
- **Observation channel**: GET = 4, Search = 0
- **Form-step counts**: aggregate=1, negative-aggregate=3
- **Variants**: happy-path=1, observe-via-get=4, bad-request=1, unauthorized=1, forbidden=1

| form step | variants | file:line | test name |
|--|--|--|--|
| aggregate | observe-via-get, happy-path | `usage-metrics/usage-metrics-api.spec.ts:165` | Get Usage Metrics Success |
| negative-aggregate | bad-request, observe-via-get | `usage-metrics/usage-metrics-api.spec.ts:190` | Get Usage Metrics - Invalid date format |
| negative-aggregate | unauthorized, observe-via-get | `usage-metrics/usage-metrics-api.spec.ts:211` | Get Usage Metrics - Unauthorized |
| negative-aggregate | forbidden, observe-via-get | `usage-metrics/usage-metrics-api.spec.ts:321` | Get Usage Metrics - User with no granted authorization |

### `cluster` — 3 tests

- **Prerequisite to create**: none
- **Files**: `cluster-api-tests.spec.ts`
- **Observation channel**: GET = 3, Search = 0
- **Form-step counts**: observe-present-get=2, negative-get=1
- **Variants**: observe-via-get=3, unauthorized=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | — | `cluster-api-tests.spec.ts:19` | Get Cluster Topology |
| observe-present-get | — | `cluster-api-tests.spec.ts:42` | Get Cluster Status |
| negative-get | unauthorized | `cluster-api-tests.spec.ts:37` | Get Cluster Topology - Unauthorized |

### `clock` — 3 tests

- **Prerequisite to create**: none
- **Files**: `clock/clock-pin-api.spec.ts`, `clock/clock-reset-api.spec.ts`
- **Observation channel**: GET = 0, Search = 0
- **Form-step counts**: create=1, delete=1, negative-create=1
- **Variants**: bad-request=1, unlabeled=2

| form step | variants | file:line | test name |
|--|--|--|--|
| create | unlabeled | `clock/clock-pin-api.spec.ts:48` | Pin clock to a fixed instant |
| delete | unlabeled | `clock/clock-reset-api.spec.ts:44` | Reset clock |
| negative-create | bad-request | `clock/clock-pin-api.spec.ts:77` | Pin clock - bad request |

### `license` — 2 tests

- **Prerequisite to create**: none
- **Files**: `license-api-tests.spec.ts`
- **Observation channel**: GET = 2, Search = 0
- **Form-step counts**: observe-present-get=1, negative-get=1
- **Variants**: observe-via-get=2, unauthorized=1

| form step | variants | file:line | test name |
|--|--|--|--|
| observe-present-get | — | `license-api-tests.spec.ts:14` | Get License |
| negative-get | unauthorized | `license-api-tests.spec.ts:35` | Get License Unauthorized |

