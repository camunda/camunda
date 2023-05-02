/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Field} from 'react-final-form';
import {observer} from 'mobx-react';
import {Title, WarningFilled} from './styled';
import {CheckmarkOutline} from '@carbon/react/icons';
import {Checkbox} from 'modules/components/Carbon/Checkbox';

const InstancesStatesFormGroup: React.FC = observer(() => {
  return (
    <div>
      <Title>Instances States</Title>
      <Field name="evaluated" component="input" type="checkbox">
        {({input}) => (
          <Checkbox
            input={input}
            labelText="Evaluated"
            Icon={CheckmarkOutline}
          />
        )}
      </Field>
      <Field name="failed" component="input" type="checkbox">
        {({input}) => (
          <Checkbox input={input} labelText="Failed" Icon={WarningFilled} />
        )}
      </Field>
    </div>
  );
});

export {InstancesStatesFormGroup};
