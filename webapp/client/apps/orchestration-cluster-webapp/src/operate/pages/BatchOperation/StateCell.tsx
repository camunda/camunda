/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationItem} from '@camunda/camunda-api-zod-schemas/8.11';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator';

type Props = {
	item: BatchOperationItem;
};

const StateCell: React.FC<Props> = ({item}) => <BatchStateIndicator state={item.state} ariaLabelPrefix="Item status" />;

export {StateCell};
