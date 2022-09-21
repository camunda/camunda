/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {Overlay, Container, Label, Spinner} from './styled';
import {MODIFICATION_HEADER_HEIGHT, FOOTER_HEIGHT} from 'modules/constants';

type Props = {
  label: string;
};

const ModificationLoadingOverlay: React.FC<Props> = observer(({label}) => {
  return (
    <Overlay
      $modificationHeaderHeight={
        modificationsStore.isModificationModeEnabled
          ? MODIFICATION_HEADER_HEIGHT
          : 0
      }
      $footerHeight={
        modificationsStore.isModificationModeEnabled ? 0 : FOOTER_HEIGHT
      }
    >
      <Container>
        <Label>{label}</Label>
        <Spinner />
      </Container>
    </Overlay>
  );
});

export {ModificationLoadingOverlay};
