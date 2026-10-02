/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type Props = {
	title: string;
	caseId: string;
};

const CaseCell: React.FC<Props> = ({title, caseId}) => {
	return (
		<div className="flex min-w-0 flex-col gap-0.5">
			<span className="truncate font-medium text-neutral-foreground-strong">{title}</span>
			<span className="truncate font-mono text-xs text-neutral-foreground-subtle">{caseId}</span>
		</div>
	);
};

export {CaseCell};
