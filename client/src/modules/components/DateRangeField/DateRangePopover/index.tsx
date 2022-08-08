/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TextField} from 'modules/components/TextField';
import {Dash, Footer, FieldContainer, Popover} from './styled';
import {Field, Form} from 'react-final-form';
import {Button} from 'modules/components/Button';

type Props = {
  referenceElement: HTMLElement;
  initialValues: {fromDate: string; toDate: string};
  onCancel: () => void;
  onOutsideClick?: (event: MouseEvent) => void;
  onApply: ({fromDate, toDate}: {fromDate?: string; toDate?: string}) => void;
};

const DateRangePopover: React.FC<Props> = ({
  referenceElement,
  initialValues,
  onCancel,
  onApply,
  onOutsideClick,
}) => {
  return (
    <Popover
      referenceElement={referenceElement}
      offsetOptions={{offset: [0, 10]}}
      placement="right"
      onOutsideClick={onOutsideClick}
    >
      <Form onSubmit={onApply} initialValues={initialValues}>
        {({handleSubmit}) => (
          <form onSubmit={handleSubmit}>
            <FieldContainer>
              <Field name="fromDate">
                {({input}) => (
                  <TextField
                    {...input}
                    type="text"
                    label="From"
                    shouldDebounceError={false}
                    placeholder="YYYY-MM-DD hh:mm:ss"
                    autoFocus
                  />
                )}
              </Field>
              <Dash>&ndash;</Dash>
              <Field name="toDate">
                {({input}) => (
                  <TextField
                    {...input}
                    type="text"
                    label="To"
                    shouldDebounceError={false}
                    placeholder="YYYY-MM-DD hh:mm:ss"
                  />
                )}
              </Field>
            </FieldContainer>
            <Footer>
              <Button
                color="secondary"
                size="medium"
                title="Cancel"
                onClick={onCancel}
              >
                Cancel
              </Button>
              <Button type="submit" color="primary" size="medium" title="Apply">
                Apply
              </Button>
            </Footer>
          </form>
        )}
      </Form>
    </Popover>
  );
};

export {DateRangePopover};
