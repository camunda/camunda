/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Overlay, Container, Label, Spinner} from './styled';

type Props = {
  label: string;
};

const ModificationLoadingOverlay: React.FC<Props> = ({label}) => {
  return (
    <Overlay>
      <Container>
        <Label>{label}</Label>
        <Spinner />
      </Container>
    </Overlay>
  );
};

export {ModificationLoadingOverlay};
