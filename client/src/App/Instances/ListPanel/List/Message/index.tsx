/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';
import Table from 'modules/components/Table';
import {StatusMessage} from 'modules/components/StatusMessage';
import {getFilters} from 'modules/utils/filter';
import {useLocation} from 'react-router-dom';
import EmptyMessage from '../../../EmptyMessage';

const {TBody, TD} = Table;

type Props = {
  type: 'error' | 'empty';
};

const Message: React.FC<Props> = ({type}) => {
  const location = useLocation();
  const filters = getFilters(location.search);

  const getEmptyListMessage = () => {
    const {active, incidents, completed, canceled} = filters;

    let msg = 'There are no Instances matching this filter set';

    if (!active && !incidents && !completed && !canceled) {
      msg += '\n To see some results, select at least one Instance state';
    }

    return msg;
  };

  return (
    <TBody>
      <Styled.EmptyTR>
        <TD colSpan={6}>
          {type === 'error' && (
            <EmptyMessage
              message={
                <StatusMessage variant="error">
                  Instances could not be fetched
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

export {Message};
