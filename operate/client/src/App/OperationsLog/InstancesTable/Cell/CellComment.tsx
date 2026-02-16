/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {Information} from '@carbon/react/icons';
import {Button} from '@carbon/react';
import type {DetailsModalState} from 'modules/components/OperationsLogDetailsModal';

type Props = {
  item: AuditLog;
  setDetailsModal: (state: DetailsModalState) => void;
};

const CellComment: React.FC<Props> = ({item, setDetailsModal}: Props) => {
  return (
    <Button
      kind="ghost"
      size="sm"
      tooltipPosition="left"
      iconDescription="Open details"
      aria-label="Open details"
      onClick={() => setDetailsModal({isOpen: true, auditLog: item})}
      hasIconOnly
      renderIcon={Information}
    />
  );
};

export {CellComment};
