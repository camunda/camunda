/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Header} from '../Header';
import {SummaryText} from '../styled';

type Props = {count: number; onButtonClick: () => void};

const MultiIncidents: React.FC<Props> = ({count, onButtonClick}) => {
  const labelId = 'metadata-popover-incidents-title';
  return (
    <section aria-labelledby={labelId}>
      <Header
        title="Incidents"
        titleId={labelId}
        variant="error"
        button={{
          title: 'Show incidents',
          label: 'View',
          onClick: onButtonClick,
        }}
      />
      <SummaryText>{`${count} incidents occurred`}</SummaryText>
    </section>
  );
};

export {MultiIncidents};
