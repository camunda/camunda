/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react-lite';
import {Toggle} from '@carbon/react';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import {Container} from './styled';

type Props = {
  onAutoSelectToggle?: (state: boolean) => void;
};
const Options: React.FC<Props> = observer(({onAutoSelectToggle}) => {
  return (
    <Container aria-label="Options">
      <Toggle
        id="toggle-auto-select-task"
        data-testid="toggle-auto-select-task"
        size="sm"
        labelText="Auto-select first available task"
        aria-label="Auto-select first available task"
        hideLabel
        labelA="Off"
        labelB="On"
        toggled={autoSelectNextTaskStore.enabled}
        onToggle={(state) => {
          autoSelectNextTaskStore.toggle();
          onAutoSelectToggle?.(!state);
        }}
      />
    </Container>
  );
});

export {Options};
