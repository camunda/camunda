/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useProcessInstances} from 'modules/queries/useProcessInstances';
import {Container} from './styled';

const Instances: React.FC = () => {
  const {data: processInstances} = useProcessInstances();

  return (
    <Container>
      <ul>
        {processInstances?.map((instance) => (
          <li key={instance.id}>{instance.process.bpmnProcessId}</li>
        ))}
      </ul>
    </Container>
  );
};

export {Instances};
