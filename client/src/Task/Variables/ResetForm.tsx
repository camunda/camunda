/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {useForm} from 'react-final-form';

type Props = {
  isAssigned: boolean;
};

const ResetForm: React.FC<Props> = ({isAssigned}) => {
  const form = useForm();

  useEffect(() => {
    if (!form.getState().submitting) {
      form.reset();
    }
  }, [isAssigned, form]);

  return null;
};

export {ResetForm};
