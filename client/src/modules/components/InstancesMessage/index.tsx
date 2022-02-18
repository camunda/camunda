/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';
import Table from 'modules/components/Table';
import {StatusMessage} from 'modules/components/StatusMessage';
import {EmptyMessage} from 'modules/components/EmptyMessage';

const {TBody, TD} = Table;

type Props = {
  type: 'error' | 'empty';
  areInstanceStateFiltersApplied?: boolean;
};

const InstancesMessage: React.FC<Props> = ({
  type,
  areInstanceStateFiltersApplied,
}) => {
  const getEmptyListMessage = () => {
    let msg = 'There are no Instances matching this filter set';

    if (!areInstanceStateFiltersApplied) {
      msg += '\n To see some results, select at least one Instance state';
    }

    return msg;
  };

  return (
    <TBody>
      <Styled.EmptyTR>
        <TD colSpan="100%">
          {type === 'error' && (
            <EmptyMessage
              message={
                <StatusMessage variant="error">
                  Data could not be fetched
                </StatusMessage>
              }
              data-testid="error-message-instances-list"
            />
          )}
          {type === 'empty' && (
            <EmptyMessage
              message={getEmptyListMessage()}
              data-testid="empty-message-instances-list"
            />
          )}
        </TD>
      </Styled.EmptyTR>
    </TBody>
  );
};

export {InstancesMessage};
