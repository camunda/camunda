/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Api, User} from '@carbon/react/icons';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';

type Props = React.SVGProps<SVGSVGElement> & {auditLog: AuditLog};

const ActorIcon: React.FC<Props> = ({auditLog, ...rest}) => {
	switch (auditLog.actorType) {
		case 'USER':
			return <User {...rest} />;
		case 'CLIENT':
			return <Api {...rest} />;
		default:
			return null;
	}
};

export {ActorIcon};
