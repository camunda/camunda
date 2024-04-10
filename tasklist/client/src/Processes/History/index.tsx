/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
