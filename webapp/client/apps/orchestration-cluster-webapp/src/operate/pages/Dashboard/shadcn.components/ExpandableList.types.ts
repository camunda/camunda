/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type React from 'react';

type ExpandableListRow = {
	id: string;
	content: React.ReactNode;
	name: React.ReactNode;
	activeCount?: number;
	incidentsCount: number;
};

type ExpandableListVariantProps = {
	header: string;
	rows: ExpandableListRow[];
	renderExpansion: (row: ExpandableListRow) => React.ReactNode;
	isPending: boolean;
};

type ExpandableListVariantComponent = React.FC<ExpandableListVariantProps>;

export type {ExpandableListRow, ExpandableListVariantProps, ExpandableListVariantComponent};
