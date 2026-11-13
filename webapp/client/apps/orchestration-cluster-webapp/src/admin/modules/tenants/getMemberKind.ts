/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TenantTab} from './detailSearchSchema';

type MemberKind = 'users' | 'groups' | 'roles' | 'mappingRules' | 'clients';

function getMemberKind(tab: TenantTab): MemberKind {
	return tab === 'mapping-rules' ? 'mappingRules' : tab;
}

export {getMemberKind};
