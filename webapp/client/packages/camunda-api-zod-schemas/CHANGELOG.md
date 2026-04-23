# Changelog

## v0.0.59

### 🚀 Enhancements

- Sync new properties to `MessageSubscription` that result in support for MCP Tools ([#51241](https://github.com/camunda/camunda/issues/51241))
  - `messageSubscriptionType` enum
  - `extensionProperties` (is a new loosely typed record)
  - `processDefinitionName`
  - `processDefinitionVersion`
  - `toolName`
  - `inboundConnectorType`

### 🩹 Fixes

- Mark `elementInstanceKey` and `correlationKey` as `nullable` in `CorrelatedMessageSubscription`
- Mark `elementInstanceKey`, `correlationKey`, `processDefinitionKey`, and `processInstanceKey` as `nullable` in `MessageSubscription`

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.58

### 🚀 Enhancements

- Support $like on all enum advanced filters
- Add decision instance batch deletion endpoint ([#51063](https://github.com/camunda/camunda/issues/51063))

### ❤️ Contributors

- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v0.0.57

### 🚀 Enhancements

- support $like search on elementName/elementId in element instance API ([#50744](https://github.com/camunda/camunda/issues/50744))

### ❤️ Contributors

- Aleksander Dytko ([@aleksander-dytko](https://github.com/aleksander-dytko))

## v0.0.56

### 🚀 Enhancements

- add process instance batch deletion to schema ([#50581](https://github.com/camunda/camunda/issues/50581))

### ❤️ Contributors

- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v0.0.55

### 🚀 Enhancements

- Copy 8.9 defs into 8.10 ([#49454](https://github.com/camunda/camunda/issues/49454))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.54

### 🚀 Enhancements

- Updated audit-log schema to support `processDefinitionId` filtering. ([#49391](https://github.com/camunda/camunda/issues/49391))

### ❤️ Contributors

- Daniel Kelemen ([@danielkelemen](https://github.com/danielkelemen))

## v0.0.53

### 🚀 Enhancements

- Switch `queryVariablesByUserTask` endpoint to `/user-tasks/{key}/effective-variables/search` API ([#46967](https://github.com/camunda/camunda/issues/46967))

### ❤️ Contributors

- Eddie Tsedeke ([@tsedekey](https://github.com/tsedekey))
- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.52

### 🩹 Fixes

- Remove `annotation` field from Audit Log schema ([#47668](https://github.com/camunda/camunda/issues/47668))

### ❤️ Contributors

- Luca Arienti ([@arienzIT](https://github.com/arienzIT))

## v0.0.51

### 🩹 Fixes

- make resourceId optional ([#47656](https://github.com/camunda/camunda/issues/47656))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.50

### 🩹 Fixes

- fix authorization types ([#47546](https://github.com/camunda/camunda/issues/47546))
- fix group/role types ([#47546](https://github.com/camunda/camunda/issues/47546))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.49

### 🩹 Fixes

- fix permission enum mistake ([#47543](https://github.com/camunda/camunda/issues/47543))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.48

### 🩹 Fixes

- fix mappingRuleId naming ([#47500](https://github.com/camunda/camunda/issues/47500))
- add query items to role, group and tenant queries ([#47500](https://github.com/camunda/camunda/issues/47500))
- add missing resource types ([#47500](https://github.com/camunda/camunda/issues/47500))
- remove unnecessary mapping rule schema ([#47500](https://github.com/camunda/camunda/issues/47500))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.47

### 🚀 Enhancements

- add `relatedEntityKey` filter to Audit Log ([#45201](https://github.com/camunda/camunda/issues/45201))

### ❤️ Contributors

- Luca Arienti ([@arienzIT](https://github.com/arienzIT))

## v0.0.46

### 🚀 Enhancements

- extend zod schemas ([#46832](https://github.com/camunda/camunda/issues/46832))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.45

### 🩹 Fixes

- make jobKey nullable in incident schema ([#46640](https://github.com/camunda/camunda/issues/46640))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.44

### 🩹 Fixes

- fix process instance query type ([#46537](https://github.com/camunda/camunda/issues/46537))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.43

- Add `DELETE_DECISION_INSTANCE` to `batchOperationTypeSchema` ([#45988](https://github.com/camunda/camunda/issues/45988))

### ❤️ Contributors

- Omran Abazeed ([@omranAbazeed](https://github.com/omranAbazeed))

## v0.0.42

### 🚀 Enhancements

- fix audit log shape ([#46339](https://github.com/camunda/camunda/issues/46339))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.41

### 🚀 Enhancements

- fix 8.9 defs and mark optional fields as nullable ([#46339](https://github.com/camunda/camunda/issues/46339))
- add missing 8.9 defs ([#463100](https://github.com/camunda/camunda/issues/463100))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.40

### 🚀 Enhancements

Accidental empty release

## v0.0.39

### 🚀 Enhancements

- update zod schema for audit log endpoint with `agentElementId` ([#45500](https://github.com/camunda/camunda/issues/45500))

### ❤️ Contributors

- Luca Arienti ([@arienzIT](https://github.com/arienzIT))

## v0.0.38

### 🚀 Enhancements

- update zod schema for audit log endpoint with related entity fields and description ([#45669](https://github.com/camunda/camunda/pull/45669))

### ❤️ Contributors

- Daniel Kelemen ([@danielkelemen](https://github.com/danielkelemen))

## v0.0.37

### 🚀 Enhancements

- update zod schema for version statistics endpoint ([#45418](https://github.com/camunda/camunda/pull/45418))

### ❤️ Contributors

- Eddie Tsedeke ([@tsedekey](https://github.com/tsedekey))

## v0.0.36

### 🚀 Enhancements

- Allow null processDefinitionName in incident statistics schema ([#44864](https://github.com/camunda/camunda/pull/44864))

### ❤️ Contributors

- Eddie Tsedeke ([@tsedekey](https://github.com/tsedekey))

## v0.0.35

### 🚀 Enhancements

- add process-instance modification request to 8.9 API schema ([#44723](https://github.com/camunda/camunda/pull/44723))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.34

### 🚀 Enhancements

- add incident statistics API schemas for v8.9 ([#44463](https://github.com/camunda/camunda/pull/44463))

### ❤️ Contributors

- Eddie Tsedeke ([@tsedekey](https://github.com/tsedekey))

## v0.0.33

### 🚀 Enhancements

- update resource deletion request and response schema ([#31690](https://github.com/camunda/camunda/issues/31690))

## v0.0.32

### 🚀 Enhancements

- add `operationType` field to `batchOperationItem` schema ([#42145](https://github.com/camunda/camunda/pull/42145))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.31

### 🚀 Enhancements

- add `isLatestVersion` filter for searching decision definitions ([#44093](https://github.com/camunda/camunda/pull/44093))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.30

### 🚀 Enhancements

- add optional `actorType` and `actorId` fields to batch operation schema ([#44029](https://github.com/camunda/camunda/pull/44029))
- add `actorId` as a sortable field for batch operations query ([#44029](https://github.com/camunda/camunda/pull/44029))

### 🩹 Fixes

- update batch operation methods (`cancel`, `suspend`, `resume`) to use POST instead of PUT ([#44029](https://github.com/camunda/camunda/pull/44029))
- remove `INCOMPLETED` from batch operation state enum ([#44029](https://github.com/camunda/camunda/pull/44029))

### ❤️ Contributors

- Omran Abazid ([@OmranAbazid](https://github.com/OmranAbazid))

## v0.0.29

### 🚀 Enhancements

- add user task audit log query ([#43973](https://github.com/camunda/camunda/pull/43973))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.28

### 🚀 Enhancements

- add process instance deletion v2 endpoint ([#43318](https://github.com/camunda/camunda/pull/43318))

## v0.0.27

### 🩹 Fixes

- fix exports ([#42145](https://github.com/camunda/camunda/pull/42145))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.26

### 🩹 Fixes

- add missing exports ([#42145](https://github.com/camunda/camunda/pull/42145))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.25

### 🚀 Enhancements

- add process definitions statistic v2 queries ([#42145](https://github.com/camunda/camunda/pull/42145))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.24

### 🚀 Enhancements

- update pd variable filter schema ([#42275](https://github.com/camunda/camunda/pull/42275))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.23

### 🚀 Enhancements

- update pi variable filer schema ([#42253](https://github.com/camunda/camunda/pull/42253))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.22

### 🚀 Enhancements

- support filtering BO items by operation type ([#42106](https://github.com/camunda/camunda/pull/42106))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.21

### 🚀 Enhancements

- add processedDate to sortFields in queryBatchOperationItemsRequestBodySchema ([#42081](https://github.com/camunda/camunda/pull/42081))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.20

### 🚀 Enhancements

- add Zod schemas for audit log entities and operations ([#41866](https://github.com/camunda/camunda/pull/41866))

### ❤️ Contributors

- Daniel Kelemen ([@danielkelemen](https://github.com/danielkelemen))

## v0.0.19

### 🩹 Fixes

- always send truncateValues when defineds ([#41390](https://github.com/camunda/camunda/pull/41390))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.18

### 🚀 Enhancements

- add full variable config to fetch variables typedefs ([#41390](https://github.com/camunda/camunda/pull/41390))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.17

### 🚀 Enhancements

- add process instance incident resolution to zod schema ([#41107](https://github.com/camunda/camunda/pull/41107))

### ❤️ Contributors

- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v0.0.16

### 🚀 Enhancements

- update decision-instances search with more advanced filters ([#40895](https://github.com/camunda/camunda/pull/40895))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.15

### 🩹 Fixes

- update process definition filtering types ([#40663](https://github.com/camunda/camunda/pull/40663))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.14

### 🩹 Fixes

- update process instance filtering and item types ([#40663](https://github.com/camunda/camunda/pull/40663))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.13

### 🩹 Fixes

- add missing element instance type ([#40368](https://github.com/camunda/camunda/pull/40368))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.12

### 🩹 Fixes

- fix search element instances endpoint filters schema ([#40132](https://github.com/camunda/camunda/pull/40132))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.11

### 🚀 Enhancements

- add root rootDecisionDefinitionKey to decision instance ([#37363](https://github.com/camunda/camunda/pull/37363))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.10

### 🩹 Fixes

- process-instance-incidents-search request body aliases wrong schema ([#39753](https://github.com/camunda/camunda/pull/39753))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.9

### 🚀 Enhancements

- use aliased incidents search request/response types ([de6fa21](https://github.com/camunda/camunda/commit/b6e683e959fdb7727cf53363e3eba95ec8a77646))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.8

### 🚀 Enhancements

- add query for element instance incident search ([#39686](https://github.com/camunda/camunda/pull/39686))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.7

### 🩹 Fixes

- remove impossible decision instance states ([#39686](https://github.com/camunda/camunda/pull/39686))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

## v0.0.6

### 🚀 Enhancements

- add message subscription endpoints ([#39031](https://github.com/camunda/camunda/pull/39031))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.5

### 🩹 Fixes

- update batch operation schema types ([#38331](https://github.com/camunda/camunda/pull/38331))

### ❤️ Contributors

- Yuliia Saienko ([@juliasaienko](https://github.com/juliasaienko))

## v0.0.4

### 🩹 Fixes

- rename decisionInstanceSchema properties to match the api spec ([#38357](https://github.com/camunda/camunda/pull/38357))

### ❤️ Contributors

- Christoph Fricke ([@christoph-fricke](https://github.com/christoph-fricke))

## v0.0.3

### 🩹 Fixes

- handle partially completed and failed batch operation states ([#37634](https://github.com/camunda/camunda/pull/37907))

### ❤️ Contributors

- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v0.0.2

### 🩹 Fixes

- incident resolutiona and cancellation batch operations schemas ([#37634](https://github.com/camunda/camunda/pull/37634))

### ❤️ Contributors

- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v0.0.1

### 🚀 Enhancements

- Migrate @vzeta/camunda-api-zod-schemas to @camunda/camunda-api-zod-schemas ([#36991](https://github.com/camunda/camunda/pull/36991)), ([#37073](https://github.com/camunda/camunda/pull/37073)), ([#37504](https://github.com/camunda/camunda/pull/37504))

### ❤️ Contributors

- Vinicius Goulart ([@vsgoulart](https://github.com/vsgoulart))

# Old Changelog

## v2.0.12

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.11...v2.1.0)

### 🚀 Enhancements

- Add process instance modification endpoint ([#55](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/55))

### ❤️ Contributors

- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v2.0.11

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.10...v2.1.0)

### 🚀 Enhancements

- Align authentication schema with renamed API response property ([#54](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/54))

### ❤️ Contributors

- Thorben Lindhauer ([@ThorbenLindhauer](https://github.com/ThorbenLindhauer))

## v2.0.10

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.9...v2.0.10)

### 🚀 Enhancements

- Decision instance search endpoint extention ([#51](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/51))

### ❤️ Contributors

- Yuliia Saienko <yuliia.saienko-ext@camunda.com>

## v2.0.9

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.8...v2.0.9)

### 🩹 Fixes

- Remove userKey and userId from user references ([7d05814](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/7d05814))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.8

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.7...v2.0.8)

### 🩹 Fixes

- Fix process definition advanced filter ([d2b3ed0](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/d2b3ed0))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.7

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.6...v2.0.7)

### 🚀 Enhancements

- Add process definition advanced filters ([ddafaf1](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/ddafaf1))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.6

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.5...v2.0.6)

### 🚀 Enhancements

- Update process definition shape and filtering ([d92f505](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/d92f505))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.5

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.4...v2.0.5)

### 🚀 Enhancements

- Add scopeKey filter to element instance history query ([a7958c5](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/a7958c5))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.4

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.3...v2.0.4)

### 🚀 Enhancements

- Add advanced filtering to state in user tasks ([#49](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/49))

### 🩹 Fixes

- Naming and unnecessary exports ([3fea1a5](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/3fea1a5))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>
- Vítor Tavares ([@vitorwtavares](https://github.com/vitorwtavares))

## v2.0.3

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.2...v2.0.3)

### 🩹 Fixes

- Add email to current user ([89232e5](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/89232e5))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.2

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.1...v2.0.2)

### 🩹 Fixes

- Fix current user response format ([c6638e9](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/c6638e9))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>

## v2.0.1

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v2.0.0...v2.0.1)

### 🩹 Fixes

- Rename batchOperationId -> batchOperationKey ([#47](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/47))

### 🏡 Chore

- Fix formatting ([d2d1fa5](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/d2d1fa5))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>
- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v2.0.0

[compare changes](https://github.com/vsgoulart/camunda-api-zod-schemas/compare/v1.2.0...v2.0.0)

### ⚠️ Breaking Changes

- Bump to zod v4 ([c7f0cc5](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/c7f0cc5))
- Remove cjs support ([9df4d63](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/9df4d63))

### 🚀 Enhancements

- Add batch operation endpoints ([#44](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/44))

### 💅 Refactors

- Replace biome with prettier ([9733d7b](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/9733d7b))

### ❤️ Contributors

- Vinicius Goulart <vinicius.goulart@camunda.com>
- Patrick Dehn ([@pedesen](https://github.com/pedesen))

## v1.1.0

### ⚠️ Breaking Changes

- Temporarily revert to Zod v3

### 🚀 Enhancements

- Temporarily add CJS support

### 🩹 Fixes

- Fixes inconsistency in `getProcessInstanceSequenceFlowsResponseBodySchema` name

### ❤️ Contributors

- Vinicius Goulart
- Patrick Dehn

## v1.0.0

### ⚠️ Breaking Changes

- Migrate to Zod v4
- Add Camunda unified version to export path. `@vzeta/camunda-api-zod-schemas` -> `@vzeta/camunda-api-zod-schemas/8.8`

### 🚀 Enhancements

- Implement all missing endpoints and definitions from the [Camunda API](docs.camunda.io/docs/8.8/apis-tools/camunda-api-rest/specifications/camunda-8-rest-api/)

### ❤️ Contributors

- Vinicius Goulart
- Alexandre Bremard
- Patrick Dehn
- Sebastian Stamm

## v0.0.1

### 🏡 Chore

- Bootstrap project ([0236e68](https://github.com/vsgoulart/camunda-api-zod-schemas/commit/0236e68))

### 🤖 CI

- Add renovate.json ([#1](https://github.com/vsgoulart/camunda-api-zod-schemas/pull/1))

### ❤️ Contributors

- Vinicius Goulart
