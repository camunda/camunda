/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Anchor} from 'modules/components/Anchor/styled';
import {Container, Text, InfoIcon, CloseIcon, Button} from './styled';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useState} from 'react';

type Props = {
  type: 'Input' | 'Output';
  text: string;
};

const IOMappingInfoBanner: React.FC<Props> = ({type, text}) => {
  const [isVisible, setIsVisible] = useState(true);

  if (!isVisible || getStateLocally()?.[`hide${type}MappingsHelperBanner`]) {
    return null;
  }

  return (
    <Container>
      <InfoIcon />
      <Text>
        {text}{' '}
        <Anchor
          href="https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings"
          target="_blank"
        >
          Learn more
        </Anchor>
      </Text>

      <Button
        aria-label="Close"
        onClick={() => {
          setIsVisible(false);
          storeStateLocally({[`hide${type}MappingsHelperBanner`]: true});
        }}
      >
        <CloseIcon />
      </Button>
    </Container>
  );
};

export {IOMappingInfoBanner};
