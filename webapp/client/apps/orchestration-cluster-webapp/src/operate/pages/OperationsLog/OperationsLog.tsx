/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {OperationsLogSearch} from './operationsLog.schema';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {PageContainer} from './styled';

const OperationsLog: React.FC<OperationsLogSearch> = (search) => {
	return (
		<PageContainer>
			<Filters search={search} />
			<InstancesTable search={search} />
		</PageContainer>
	);
};

export {OperationsLog};
