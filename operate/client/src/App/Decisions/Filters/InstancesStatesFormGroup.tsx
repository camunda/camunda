/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {observer} from 'mobx-react';
import {Title, WarningFilled} from 'modules/components/FiltersPanel/styled';
import {CheckmarkOutline} from '@carbon/react/icons';
import {Checkbox} from 'modules/components/Checkbox';

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
