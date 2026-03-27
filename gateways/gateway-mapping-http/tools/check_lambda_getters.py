#!/usr/bin/env python3
import re
with open("gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/search/SearchQueryFilterMapper.java") as fp:
    content = fp.read()
targets = ["toJobFilter","toRoleFilter","toGroupFilter","toTenantFilter","toMappingRuleFilter","toDecisionDefinitionFilter","toDecisionRequirementsFilter","toElementInstanceFilter","toDecisionInstanceFilter","toUserTaskFilter","toUserTaskVariableFilter","toVariableFilter","toClusterVariableFilter","toUserFilter","toIncidentFilter","toBatchOperationFilter","toBatchOperationItemFilter","toAuthorizationFilter","toAuditLogFilter","toUserTaskAuditLogFilter","toMessageSubscriptionFilter","toCorrelatedMessageSubscriptionFilter","toProcessInstanceFilter"]
for t in targets:
    m = re.search(r"(?:public )?static \S+ " + t + r"\b.*?\n  \}", content, re.DOTALL)
    if m:
        body = m.group()
        fgets = re.findall(r"\bf\.get\w+\(\)", body)
        tgets = re.findall(r"\bt\.get\w+\(\)", body)
        if fgets or tgets:
            print(f"{t}: f.get({len(fgets)}), t.get({len(tgets)})")
