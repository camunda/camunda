# Request Validation Coverage

Generated: 2025-09-05T06:07:46.918Z
Spec Commit: 3445d1d86c2ad361858dc12e734eeb6197e426a5

Total scenarios: 736

Scenario kinds generated this run: 14
Average kind coverage per operation: 23.9%
Operations with full kind coverage: 0/107

Kind coverage % = (# kinds present for operation / total scenario kinds this run) * 100.

Average applicable kind coverage (ops with applicability): 41.5%

| OperationId | Method | Path | Total | KindCov% | AppKindCov% | ApplicableKinds | PresentKinds | additional-prop-general | constraint-violation | enum-violation | missing-body | missing-required | missing-required-combo | oneof-ambiguous | oneof-cross-bleed | oneof-none-match | param-missing | param-type-mismatch | type-mismatch | union | unique-items-violation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| activateAdHocSubProcessActivities | POST | /element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation | 6 | 29% | 57.1% | 7 | 4 | 1 |  |  | 1 | 1 |  |  |  |  |  |  | 3 |  |  |
| activateJobs | POST | /jobs/activation | 16 | 36% | 50% | 10 | 5 | 1 |  |  | 1 | 3 | 4 |  |  |  |  |  | 7 |  |  |
| assignClientToGroup | PUT | /groups/{groupId}/clients/{clientId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| assignClientToTenant | PUT | /tenants/{tenantId}/clients/{clientId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| assignGroupToTenant | PUT | /tenants/{tenantId}/groups/{groupId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| assignMappingRuleToGroup | PUT | /groups/{groupId}/mapping-rules/{mappingRuleId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| assignMappingRuleToTenant | PUT | /tenants/{tenantId}/mapping-rules/{mappingRuleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| assignRoleToClient | PUT | /roles/{roleId}/clients/{clientId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| assignRoleToGroup | PUT | /roles/{roleId}/groups/{groupId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| assignRoleToMappingRule | PUT | /roles/{roleId}/mapping-rules/{mappingRuleId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| assignRoleToTenant | PUT | /tenants/{tenantId}/roles/{roleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| assignRoleToUser | PUT | /roles/{roleId}/users/{username} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| assignUserTask | POST | /user-tasks/{userTaskKey}/assignment | 3 | 21% | 60% | 5 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |
| assignUserToGroup | PUT | /groups/{groupId}/users/{username} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| broadcastSignal | POST | /signals/broadcast | 6 | 29% | 40% | 10 | 4 | 1 |  |  | 1 | 1 |  |  |  |  |  |  | 3 |  |  |
| cancelProcessInstance | POST | /process-instances/{processInstanceKey}/cancellation | 8 | 29% | 66.7% | 6 | 4 | 1 | 3 |  | 1 |  |  |  |  |  |  |  | 3 |  |  |
| cancelProcessInstancesBatchOperation | POST | /process-instances/cancellation | 8 | 43% | 35.3% | 17 | 6 | 1 | 2 |  | 1 | 1 |  |  |  |  |  |  | 1 |  | 2 |
| completeJob | POST | /jobs/{jobKey}/completion | 3 | 21% | 33.3% | 9 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |
| completeUserTask | POST | /user-tasks/{userTaskKey}/completion | 3 | 21% | 50% | 6 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |
| correlateMessage | POST | /messages/correlation | 10 | 36% | 45.5% | 11 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 5 |  |  |
| createAdminUser | POST | /setup/user | 3 | 21% | 75% | 4 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |
| createAuthorization | POST | /authorizations | 38 | 36% | 50% | 10 | 5 | 1 |  |  | 1 | 9 | 20 |  |  |  |  |  | 7 |  |  |
| createDocument | POST | /documents | 2 | 7% | 100% | 1 | 1 |  |  |  |  |  |  |  |  |  |  | 2 |  |  |  |
| createDocumentLink | POST | /documents/{documentId}/links | 7 | 36% | 83.3% | 6 | 5 | 1 |  |  | 1 |  |  |  |  |  | 1 | 2 | 2 |  |  |
| createDocuments | POST | /documents/batch | 1 | 7% | 100% | 1 | 1 |  |  |  |  |  |  |  |  |  |  | 1 |  |  |  |
| createElementInstanceVariables | PUT | /element-instances/{elementInstanceKey}/variables | 10 | 36% | 62.5% | 8 | 5 | 1 | 3 |  | 1 | 2 |  |  |  |  |  |  | 3 |  |  |
| createGroup | POST | /groups | 10 | 36% | 83.3% | 6 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 5 |  |  |
| createMappingRule | POST | /mapping-rules | 16 | 29% | 57.1% | 7 | 4 | 1 |  |  | 1 | 5 |  |  |  |  |  |  | 9 |  |  |
| createProcessInstance | POST | /process-instances | 6 | 43% | 33.3% | 18 | 6 |  |  |  | 1 |  |  | 1 | 1 | 1 |  |  | 1 | 1 |  |
| createRole | POST | /roles | 10 | 36% | 83.3% | 6 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 5 |  |  |
| createTenant | POST | /tenants | 14 | 43% | 85.7% | 7 | 6 | 1 | 4 |  | 1 | 2 | 1 |  |  |  |  |  | 5 |  |  |
| createUser | POST | /users | 3 | 21% | 75% | 4 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |
| deleteDocument | DELETE | /documents/{documentId} | 1 | 7% | 50% | 2 | 1 |  |  |  |  |  |  |  |  |  |  | 1 |  |  |  |
| deleteGroup | DELETE | /groups/{groupId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| deleteMappingRule | DELETE | /mapping-rules/{mappingRuleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| deleteResource | POST | /resources/{resourceKey}/deletion | 8 | 29% | 66.7% | 6 | 4 | 1 | 3 |  | 1 |  |  |  |  |  |  |  | 3 |  |  |
| deleteRole | DELETE | /roles/{roleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| evaluateDecision | POST | /decision-definitions/evaluation | 7 | 50% | 50% | 14 | 7 | 1 |  |  | 1 |  |  | 1 | 1 | 1 |  |  | 1 | 1 |  |
| failJob | POST | /jobs/{jobKey}/failure | 5 | 21% | 42.9% | 7 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 3 |  |  |
| getDocument | GET | /documents/{documentId} | 4 | 21% | 100% | 3 | 3 |  |  |  |  |  |  |  |  |  | 1 | 2 | 1 |  |  |
| getGroup | GET | /groups/{groupId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| getMappingRule | GET | /mapping-rules/{mappingRuleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| getProcessDefinitionStatistics | POST | /process-definitions/{processDefinitionKey}/statistics/element-instances | 5 | 29% | 23.5% | 17 | 4 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  | 2 |
| getRole | GET | /roles/{roleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| getUsageMetrics | GET | /system/usage-metrics | 7 | 21% | 100% | 3 | 3 |  |  |  |  |  |  |  |  |  | 2 | 3 | 2 |  |  |
| migrateProcessInstance | POST | /process-instances/{processInstanceKey}/migration | 17 | 43% | 50% | 12 | 6 | 1 | 3 |  | 1 | 4 | 1 |  |  |  |  |  | 7 |  |  |
| migrateProcessInstancesBatchOperation | POST | /process-instances/migration | 18 | 50% | 38.9% | 18 | 7 | 1 | 2 |  | 1 | 6 | 1 |  |  |  |  |  | 5 |  | 2 |
| modifyProcessInstance | POST | /process-instances/{processInstanceKey}/modification | 8 | 29% | 26.7% | 15 | 4 | 1 | 3 |  | 1 |  |  |  |  |  |  |  | 3 |  |  |
| modifyProcessInstancesBatchOperation | POST | /process-instances/modification | 16 | 50% | 38.9% | 18 | 7 | 1 | 2 |  | 1 | 4 | 1 |  |  |  |  |  | 5 |  | 2 |
| pinClock | PUT | /clock | 6 | 29% | 80% | 5 | 4 | 1 |  |  | 1 | 1 |  |  |  |  |  |  | 3 |  |  |
| publishMessage | POST | /messages/publication | 10 | 36% | 45.5% | 11 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 5 |  |  |
| resolveIncident | POST | /incidents/{incidentKey}/resolution | 8 | 29% | 66.7% | 6 | 4 | 1 | 3 |  | 1 |  |  |  |  |  |  |  | 3 |  |  |
| resolveIncidentsBatchOperation | POST | /process-instances/incident-resolution | 8 | 43% | 35.3% | 17 | 6 | 1 | 2 |  | 1 | 1 |  |  |  |  |  |  | 1 |  | 2 |
| searchAuthorizations | POST | /authorizations/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchBatchOperationItems | POST | /batch-operation-items/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchBatchOperations | POST | /batch-operations/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchClientsForGroup | POST | /groups/{groupId}/clients/search | 9 | 29% | 23.5% | 17 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchClientsForRole | POST | /roles/{roleId}/clients/search | 9 | 29% | 23.5% | 17 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchClientsForTenant | POST | /tenants/{tenantId}/clients/search | 8 | 29% | 25% | 16 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchDecisionDefinitions | POST | /decision-definitions/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchDecisionInstances | POST | /decision-instances/search | 12 | 29% | 26.7% | 15 | 4 | 1 |  | 9 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchDecisionRequirements | POST | /decision-requirements/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchElementInstances | POST | /element-instances/search | 10 | 29% | 26.7% | 15 | 4 | 1 |  | 7 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchGroupIdsForTenant | POST | /tenants/{tenantId}/groups/search | 8 | 29% | 25% | 16 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchGroups | POST | /groups/search | 7 | 29% | 26.7% | 15 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchGroupsForRole | POST | /roles/{roleId}/groups/search | 9 | 29% | 23.5% | 17 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchIncidents | POST | /incidents/search | 12 | 29% | 26.7% | 15 | 4 | 1 |  | 9 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchJobs | POST | /jobs/search | 7 | 29% | 26.7% | 15 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchMappingRule | POST | /mapping-rules/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchMappingRulesForGroup | POST | /groups/{groupId}/mapping-rules/search | 9 | 29% | 23.5% | 17 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchMappingRulesForRole | POST | /roles/{roleId}/mapping-rules/search | 9 | 29% | 23.5% | 17 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchMappingsForTenant | POST | /tenants/{tenantId}/mapping-rules/search | 8 | 29% | 25% | 16 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchMessageSubscriptions | POST | /message-subscriptions/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchProcessDefinitions | POST | /process-definitions/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchProcessInstanceIncidents | POST | /process-instances/{processInstanceKey}/incidents/search | 8 | 29% | 25% | 16 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchProcessInstances | POST | /process-instances/search | 10 | 36% | 31.3% | 16 | 5 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  | 2 |
| searchRoles | POST | /roles/search | 7 | 29% | 26.7% | 15 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchRolesForGroup | POST | /groups/{groupId}/roles/search | 8 | 29% | 23.5% | 17 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchRolesForTenant | POST | /tenants/{tenantId}/roles/search | 7 | 29% | 25% | 16 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchTenants | POST | /tenants/search | 7 | 29% | 26.7% | 15 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchUsers | POST | /users/search | 7 | 29% | 26.7% | 15 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchUsersForGroup | POST | /groups/{groupId}/users/search | 8 | 29% | 23.5% | 17 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchUsersForRole | POST | /roles/{roleId}/users/search | 8 | 29% | 23.5% | 17 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 2 |  |  |
| searchUsersForTenant | POST | /tenants/{tenantId}/users/search | 7 | 29% | 25% | 16 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchUserTasks | POST | /user-tasks/search | 8 | 29% | 26.7% | 15 | 4 | 1 |  | 5 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchUserTaskVariables | POST | /user-tasks/{userTaskKey}/variables/search | 7 | 29% | 25% | 16 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| searchVariables | POST | /variables/search | 7 | 29% | 26.7% | 15 | 4 | 1 |  | 4 | 1 |  |  |  |  |  |  |  | 1 |  |  |
| throwJobError | POST | /jobs/{jobKey}/error | 6 | 29% | 57.1% | 7 | 4 | 1 |  |  | 1 | 1 |  |  |  |  |  |  | 3 |  |  |
| unassignClientFromGroup | DELETE | /groups/{groupId}/clients/{clientId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| unassignClientFromTenant | DELETE | /tenants/{tenantId}/clients/{clientId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| unassignGroupFromTenant | DELETE | /tenants/{tenantId}/groups/{groupId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| unassignMappingRuleFromGroup | DELETE | /groups/{groupId}/mapping-rules/{mappingRuleId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| unassignMappingRuleFromTenant | DELETE | /tenants/{tenantId}/mapping-rules/{mappingRuleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| unassignRoleFromClient | DELETE | /roles/{roleId}/clients/{clientId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| unassignRoleFromGroup | DELETE | /roles/{roleId}/groups/{groupId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| unassignRoleFromMappingRule | DELETE | /roles/{roleId}/mapping-rules/{mappingRuleId} | 2 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |
| unassignRoleFromTenant | DELETE | /tenants/{tenantId}/roles/{roleId} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| unassignRoleFromUser | DELETE | /roles/{roleId}/users/{username} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| unassignUserFromGroup | DELETE | /groups/{groupId}/users/{username} | 1 | 7% | 33.3% | 3 | 1 |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |
| updateAuthorization | PUT | /authorizations/{authorizationKey} | 38 | 36% | 45.5% | 11 | 5 | 1 |  |  | 1 | 9 | 20 |  |  |  |  |  | 7 |  |  |
| updateGroup | PUT | /groups/{groupId} | 11 | 36% | 62.5% | 8 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 6 |  |  |
| updateJob | PATCH | /jobs/{jobKey} | 12 | 36% | 55.6% | 9 | 5 | 1 | 3 |  | 1 | 2 |  |  |  |  |  |  | 5 |  |  |
| updateMappingRule | PUT | /mapping-rules/{mappingRuleId} | 13 | 29% | 44.4% | 9 | 4 | 1 |  |  | 1 | 3 |  |  |  |  |  |  | 8 |  |  |
| updateRole | PUT | /roles/{roleId} | 11 | 36% | 62.5% | 8 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 6 |  |  |
| updateTenant | PUT | /tenants/{tenantId} | 10 | 36% | 71.4% | 7 | 5 | 1 |  |  | 1 | 2 | 1 |  |  |  |  |  | 5 |  |  |
| updateUser | PUT | /users/{username} | 3 | 21% | 60% | 5 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |
| updateUserTask | PATCH | /user-tasks/{userTaskKey} | 3 | 21% | 37.5% | 8 | 3 | 1 |  |  | 1 |  |  |  |  |  |  |  | 1 |  |  |

Missing kinds per operation:
- activateAdHocSubProcessActivities: constraint-violation, enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- activateJobs: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignClientToGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignClientToTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignGroupToTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignMappingRuleToGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignMappingRuleToTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignRoleToClient: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignRoleToGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignRoleToMappingRule: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignRoleToTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignRoleToUser: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignUserTask: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- assignUserToGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- broadcastSignal: constraint-violation, enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- cancelProcessInstance: enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- cancelProcessInstancesBatchOperation: enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union
- completeJob: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- completeUserTask: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- correlateMessage: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createAdminUser: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createAuthorization: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createDocument: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, type-mismatch, union, unique-items-violation
- createDocumentLink: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, union, unique-items-violation
- createDocuments: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, type-mismatch, union, unique-items-violation
- createElementInstanceVariables: enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createGroup: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createMappingRule: constraint-violation, enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createProcessInstance: additional-prop-general, constraint-violation, enum-violation, missing-required, missing-required-combo, param-missing, param-type-mismatch, unique-items-violation
- createRole: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createTenant: enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- createUser: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- deleteDocument: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, type-mismatch, union, unique-items-violation
- deleteGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- deleteMappingRule: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- deleteResource: enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- deleteRole: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- evaluateDecision: constraint-violation, enum-violation, missing-required, missing-required-combo, param-missing, param-type-mismatch, unique-items-violation
- failJob: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- getDocument: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, union, unique-items-violation
- getGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- getMappingRule: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- getProcessDefinitionStatistics: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union
- getRole: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- getUsageMetrics: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, union, unique-items-violation
- migrateProcessInstance: enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- migrateProcessInstancesBatchOperation: enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union
- modifyProcessInstance: enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- modifyProcessInstancesBatchOperation: enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union
- pinClock: constraint-violation, enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- publishMessage: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- resolveIncident: enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- resolveIncidentsBatchOperation: enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union
- searchAuthorizations: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchBatchOperationItems: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchBatchOperations: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchClientsForGroup: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchClientsForRole: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchClientsForTenant: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchDecisionDefinitions: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchDecisionInstances: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchDecisionRequirements: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchElementInstances: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchGroupIdsForTenant: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchGroups: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchGroupsForRole: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchIncidents: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchJobs: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchMappingRule: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchMappingRulesForGroup: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchMappingRulesForRole: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchMappingsForTenant: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchMessageSubscriptions: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchProcessDefinitions: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchProcessInstanceIncidents: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchProcessInstances: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union
- searchRoles: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchRolesForGroup: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchRolesForTenant: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchTenants: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchUsers: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchUsersForGroup: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchUsersForRole: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchUsersForTenant: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchUserTasks: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchUserTaskVariables: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- searchVariables: constraint-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- throwJobError: constraint-violation, enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignClientFromGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignClientFromTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignGroupFromTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignMappingRuleFromGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignMappingRuleFromTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignRoleFromClient: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignRoleFromGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignRoleFromMappingRule: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignRoleFromTenant: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignRoleFromUser: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- unassignUserFromGroup: additional-prop-general, constraint-violation, enum-violation, missing-body, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateAuthorization: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateGroup: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateJob: enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateMappingRule: constraint-violation, enum-violation, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateRole: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateTenant: constraint-violation, enum-violation, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateUser: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation
- updateUserTask: constraint-violation, enum-violation, missing-required, missing-required-combo, oneof-ambiguous, oneof-cross-bleed, oneof-none-match, param-missing, param-type-mismatch, union, unique-items-violation

Endpoint coverage: 107/145 (73.8%) have at least one scenario.

True Gaps Summary (applicable missing kinds aggregated):
| Kind | MissingOps | ApplicableOps | Missing% | SampleMissingOps |
| --- | --- | --- | --- | --- |
| body-top-type-mismatch | 74 | 74 | 100.0% | activateAdHocSubProcessActivities, activateJobs, assignUserTask, broadcastSignal, cancelProcessInstance |
| param-missing | 65 | 68 | 95.6% | activateAdHocSubProcessActivities, assignClientToGroup, assignClientToTenant, assignGroupToTenant, assignMappingRuleToGroup |
| nested-additional-prop | 56 | 56 | 100.0% | activateAdHocSubProcessActivities, broadcastSignal, cancelProcessInstancesBatchOperation, completeJob, completeUserTask |
| allof-conflict | 51 | 51 | 100.0% | activateJobs, broadcastSignal, cancelProcessInstancesBatchOperation, correlateMessage, createAuthorization |
| allof-missing-required | 51 | 51 | 100.0% | activateJobs, broadcastSignal, cancelProcessInstancesBatchOperation, correlateMessage, createAuthorization |
| format-invalid | 50 | 50 | 100.0% | activateJobs, broadcastSignal, cancelProcessInstancesBatchOperation, correlateMessage, createProcessInstance |
| constraint-violation | 42 | 54 | 77.8% | activateJobs, broadcastSignal, correlateMessage, createProcessInstance, evaluateDecision |
| oneof-multi-ambiguous | 42 | 42 | 100.0% | cancelProcessInstancesBatchOperation, createProcessInstance, evaluateDecision, getProcessDefinitionStatistics, migrateProcessInstancesBatchOperation |
| oneof-ambiguous | 40 | 42 | 95.2% | cancelProcessInstancesBatchOperation, getProcessDefinitionStatistics, migrateProcessInstancesBatchOperation, modifyProcessInstance, modifyProcessInstancesBatchOperation |
| oneof-cross-bleed | 40 | 42 | 95.2% | cancelProcessInstancesBatchOperation, getProcessDefinitionStatistics, migrateProcessInstancesBatchOperation, modifyProcessInstance, modifyProcessInstancesBatchOperation |
| oneof-none-match | 40 | 42 | 95.2% | cancelProcessInstancesBatchOperation, getProcessDefinitionStatistics, migrateProcessInstancesBatchOperation, modifyProcessInstance, modifyProcessInstancesBatchOperation |
| union | 40 | 42 | 95.2% | cancelProcessInstancesBatchOperation, getProcessDefinitionStatistics, migrateProcessInstancesBatchOperation, modifyProcessInstance, modifyProcessInstancesBatchOperation |
| param-type-mismatch | 39 | 45 | 86.7% | assignClientToGroup, assignClientToTenant, assignGroupToTenant, assignMappingRuleToGroup, assignMappingRuleToTenant |
| enum-violation | 9 | 43 | 20.9% | cancelProcessInstancesBatchOperation, completeJob, createAuthorization, createProcessInstance, getProcessDefinitionStatistics |
| discriminator-mismatch | 2 | 2 | 100.0% | completeJob, createProcessInstance |
| discriminator-structure-mismatch | 2 | 2 | 100.0% | completeJob, createProcessInstance |
| additional-prop-general | 1 | 74 | 1.4% | createProcessInstance |
| unique-items-violation | 1 | 7 | 14.3% | createProcessInstance |
| missing-body | 0 | 74 | 0.0% |  |
| missing-required | 0 | 24 | 0.0% |  |
| missing-required-combo | 0 | 14 | 0.0% |  |
| type-mismatch | 0 | 104 | 0.0% |  |

<details><summary>Full per-operation True Gaps list</summary>
- activateAdHocSubProcessActivities: body-top-type-mismatch, nested-additional-prop, param-missing
- activateJobs: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid
- assignClientToGroup: param-missing, param-type-mismatch
- assignClientToTenant: param-missing, param-type-mismatch
- assignGroupToTenant: param-missing, param-type-mismatch
- assignMappingRuleToGroup: param-missing, param-type-mismatch
- assignMappingRuleToTenant: param-missing, param-type-mismatch
- assignRoleToClient: param-missing, param-type-mismatch
- assignRoleToGroup: param-missing, param-type-mismatch
- assignRoleToMappingRule: param-missing, param-type-mismatch
- assignRoleToTenant: param-missing, param-type-mismatch
- assignRoleToUser: param-missing, param-type-mismatch
- assignUserTask: body-top-type-mismatch, param-missing
- assignUserToGroup: param-missing, param-type-mismatch
- broadcastSignal: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop
- cancelProcessInstance: body-top-type-mismatch, param-missing
- cancelProcessInstancesBatchOperation: allof-conflict, allof-missing-required, body-top-type-mismatch, enum-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- completeJob: body-top-type-mismatch, discriminator-mismatch, discriminator-structure-mismatch, enum-violation, nested-additional-prop, param-missing
- completeUserTask: body-top-type-mismatch, nested-additional-prop, param-missing
- correlateMessage: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop
- createAdminUser: body-top-type-mismatch
- createAuthorization: allof-conflict, allof-missing-required, body-top-type-mismatch, enum-violation, nested-additional-prop
- createDocumentLink: body-top-type-mismatch
- createElementInstanceVariables: body-top-type-mismatch, nested-additional-prop, param-missing
- createGroup: body-top-type-mismatch
- createMappingRule: allof-conflict, allof-missing-required, body-top-type-mismatch
- createProcessInstance: additional-prop-general, allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, discriminator-mismatch, discriminator-structure-mismatch, enum-violation, format-invalid, nested-additional-prop, oneof-multi-ambiguous, unique-items-violation
- createRole: body-top-type-mismatch
- createTenant: body-top-type-mismatch
- createUser: body-top-type-mismatch
- deleteDocument: param-missing
- deleteGroup: param-missing, param-type-mismatch
- deleteMappingRule: param-missing, param-type-mismatch
- deleteResource: body-top-type-mismatch, param-missing
- deleteRole: param-missing, param-type-mismatch
- evaluateDecision: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-multi-ambiguous
- failJob: body-top-type-mismatch, format-invalid, nested-additional-prop, param-missing
- getGroup: param-missing, param-type-mismatch
- getMappingRule: param-missing, param-type-mismatch
- getProcessDefinitionStatistics: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, enum-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- getRole: param-missing, param-type-mismatch
- migrateProcessInstance: allof-conflict, allof-missing-required, body-top-type-mismatch, format-invalid, nested-additional-prop, param-missing
- migrateProcessInstancesBatchOperation: allof-conflict, allof-missing-required, body-top-type-mismatch, enum-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- modifyProcessInstance: allof-conflict, allof-missing-required, body-top-type-mismatch, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- modifyProcessInstancesBatchOperation: allof-conflict, allof-missing-required, body-top-type-mismatch, enum-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- pinClock: body-top-type-mismatch
- publishMessage: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop
- resolveIncident: body-top-type-mismatch, param-missing
- resolveIncidentsBatchOperation: allof-conflict, allof-missing-required, body-top-type-mismatch, enum-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchAuthorizations: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchBatchOperationItems: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchBatchOperations: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchClientsForGroup: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchClientsForRole: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchClientsForTenant: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchDecisionDefinitions: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchDecisionInstances: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchDecisionRequirements: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchElementInstances: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchGroupIdsForTenant: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchGroups: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchGroupsForRole: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchIncidents: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchJobs: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchMappingRule: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchMappingRulesForGroup: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchMappingRulesForRole: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchMappingsForTenant: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchMessageSubscriptions: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchProcessDefinitions: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchProcessInstanceIncidents: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchProcessInstances: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchRoles: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchRolesForGroup: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchRolesForTenant: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchTenants: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchUsers: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchUsersForGroup: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchUsersForRole: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, param-type-mismatch, union
- searchUsersForTenant: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchUserTasks: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- searchUserTaskVariables: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, param-missing, union
- searchVariables: allof-conflict, allof-missing-required, body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, oneof-ambiguous, oneof-cross-bleed, oneof-multi-ambiguous, oneof-none-match, union
- throwJobError: body-top-type-mismatch, nested-additional-prop, param-missing
- unassignClientFromGroup: param-missing, param-type-mismatch
- unassignClientFromTenant: param-missing, param-type-mismatch
- unassignGroupFromTenant: param-missing, param-type-mismatch
- unassignMappingRuleFromGroup: param-missing, param-type-mismatch
- unassignMappingRuleFromTenant: param-missing, param-type-mismatch
- unassignRoleFromClient: param-missing, param-type-mismatch
- unassignRoleFromGroup: param-missing, param-type-mismatch
- unassignRoleFromMappingRule: param-missing, param-type-mismatch
- unassignRoleFromTenant: param-missing, param-type-mismatch
- unassignRoleFromUser: param-missing, param-type-mismatch
- unassignUserFromGroup: param-missing, param-type-mismatch
- updateAuthorization: allof-conflict, allof-missing-required, body-top-type-mismatch, enum-violation, nested-additional-prop, param-missing
- updateGroup: body-top-type-mismatch, param-missing, param-type-mismatch
- updateJob: body-top-type-mismatch, format-invalid, nested-additional-prop, param-missing
- updateMappingRule: allof-conflict, allof-missing-required, body-top-type-mismatch, param-missing, param-type-mismatch
- updateRole: body-top-type-mismatch, param-missing, param-type-mismatch
- updateTenant: body-top-type-mismatch, param-missing
- updateUser: body-top-type-mismatch, param-missing
- updateUserTask: body-top-type-mismatch, constraint-violation, format-invalid, nested-additional-prop, param-missing
</details>

Applicable missing kinds are structurally possible for that operation and should be prioritized.