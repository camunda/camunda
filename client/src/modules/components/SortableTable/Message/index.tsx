/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as Styled from './styled';
import Table from 'modules/components/Table';
import {StatusMessage} from 'modules/components/StatusMessage';
import {EmptyMessage} from 'modules/components/EmptyMessage';

const {TBody, TD} = Table;

type Props = {
  type: 'error' | 'empty';
  children?: React.ReactNode;
};

const Message: React.FC<Props> = ({type, children}) => {
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
              message={children}
              data-testid="empty-message-instances-list"
            />
          )}
        </TD>
      </Styled.EmptyTR>
    </TBody>
  );
};

export {Message};
