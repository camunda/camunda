/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useProcessInstances} from 'modules/queries/useProcessInstances';
import {
  Container,
  Message,
  Header,
  Item,
  ItemContainer,
  ProcessInstanceStateIcon,
} from './styled';
import {BodyCompact, BodyLong, Label} from 'modules/components/FontTokens';
import capitalize from 'lodash/capitalize';
import {formatDate} from 'modules/utils/formatDate';
import {Skeleton} from './Skeleton';
import {match} from 'ts-pattern';
import {Stack, Layer} from '@carbon/react';

const History: React.FC = () => {
  const {data: processInstances, status} = useProcessInstances();

  return (
    <Container>
      <Label $color="secondary" as={Header}>
        History
      </Label>
      <ItemContainer>
        {match({status})
          .with({status: 'loading'}, () => <Skeleton />)
          .with({status: 'error'}, () => (
            <Layer>
              <Stack gap={3} as={Message}>
                <BodyCompact $variant="02">
                  Oops! Something went wrong while fetching the history
                </BodyCompact>
                <BodyLong $color="secondary">
                  Please check your internet connection and try again.
                </BodyLong>
              </Stack>
            </Layer>
          ))
          .with(
            {
              status: 'success',
            },
            () =>
              processInstances === undefined ||
              processInstances.length === 0 ? (
                <Layer>
                  <Stack gap={3} as={Message}>
                    <BodyCompact $variant="02">
                      No history entries found
                    </BodyCompact>
                    <BodyLong $color="secondary">
                      There is no history to display. Start a new process to see
                      it here.
                    </BodyLong>
                  </Stack>
                </Layer>
              ) : (
                processInstances.map(({id, process, creationDate, state}) => (
                  <Stack key={id} gap={3} as={Item}>
                    <BodyCompact $showEllipsisOnOverflow>
                      {process.name ?? process.bpmnProcessId}
                    </BodyCompact>
                    <BodyCompact $color="secondary" $showEllipsisOnOverflow>
                      {id}
                    </BodyCompact>
                    <Label $color="secondary">
                      {formatDate(creationDate)} - {capitalize(state)}
                      <ProcessInstanceStateIcon state={state} size={16} />
                    </Label>
                  </Stack>
                ))
              ),
          )
          .exhaustive()}
      </ItemContainer>
    </Container>
  );
};

export {History};
