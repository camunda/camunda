/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Header} from '../Header';
import {SummaryDataValue} from '../styled';

type Props = {count: number; onButtonClick: () => void};

const MultiIncidents: React.FC<Props> = ({count, onButtonClick}) => {
  return (
    <>
      <Header
        title="Incidents"
        variant="error"
        button={{
          title: 'Show incidents',
          label: 'View',
          onClick: onButtonClick,
        }}
      />
      <SummaryDataValue>{`${count} incidents occurred`}</SummaryDataValue>
    </>
  );
};

export {MultiIncidents};
