/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {Link} from 'modules/components/Link';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {mapToCellEntityKeyData} from 'modules/utils/operationsLog';

type Props = {
  item: AuditLog;
  processDefinitionName?: string | null;
  decisionDefinitionName?: string | null;
};

const CellEntityKey: React.FC<Props> = ({
  item,
  processDefinitionName,
  decisionDefinitionName,
}) => {
  const clientConfig = getClientConfig();
  const {link, linkLabel, label, name} = mapToCellEntityKeyData(
    item,
    processDefinitionName,
    decisionDefinitionName,
    clientConfig?.tasklistUrl ?? undefined,
  );

  return (
    <div>
      <div>
        {link ? (
          <Link to={link} title={linkLabel} aria-label={linkLabel}>
            {label}
          </Link>
        ) : (
          label
        )}
      </div>
      {item.entityDescription?.trim() || <em>{name}</em>}
    </div>
  );
};

export {CellEntityKey};
