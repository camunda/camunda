/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {AuditLogTable} from './AuditLogTable';
import {AuditLogFilters} from './Filters';
import {useEffect} from 'react';
import {PAGE_TITLE} from 'modules/constants';
import {Container} from 'App/AuditLog/styled';

const AuditLog: React.FC = observer(() => {
  useEffect(() => {
    document.title = PAGE_TITLE.AUDIT_LOG;
  }, []);

  return (
    <Container data-testid="audit-log-page">
      <VisuallyHiddenH1>Audit Log</VisuallyHiddenH1>
      <AuditLogFilters />
      <AuditLogTable />
    </Container>
  );
});

export {AuditLog};
