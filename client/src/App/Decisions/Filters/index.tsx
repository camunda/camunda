/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Field, Form} from 'react-final-form';
import {CollapsablePanel} from './CollapsablePanel';
import {FormElement} from './styled';

const Filters: React.FC = () => {
  return (
    <CollapsablePanel header="Filters">
      <Form onSubmit={() => {}}>
        {({handleSubmit, form}) => (
          <FormElement onSubmit={handleSubmit}>
            <h3>Decision</h3>
            <Field name="name">
              {({input}) => (
                <label htmlFor={input.name}>
                  Name
                  <select
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  >
                    <option value="1">Decision 1</option>
                    <option value="2">Decision 2</option>
                    <option value="3">Decision 3</option>
                  </select>
                </label>
              )}
            </Field>
            <Field name="version">
              {({input}) => (
                <label htmlFor={input.name}>
                  Version
                  <select
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  >
                    <option value="1">Version 1</option>
                    <option value="2">Version 2</option>
                    <option value="3">Version 3</option>
                  </select>
                </label>
              )}
            </Field>
            <h3>Instance States</h3>
            <Field name="completed">
              {({input}) => (
                <label htmlFor={input.name}>
                  <input
                    type="checkbox"
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  />
                  Completed
                </label>
              )}
            </Field>
            <Field name="failed">
              {({input}) => (
                <label htmlFor={input.name}>
                  <input
                    type="checkbox"
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  />
                  Failed
                </label>
              )}
            </Field>
            <Field name="decisionInstanceId">
              {({input}) => (
                <label htmlFor={input.name}>
                  Decision Instance Id(s)
                  <textarea
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  />
                </label>
              )}
            </Field>
            <Field name="processInstanceId">
              {({input}) => (
                <label htmlFor={input.name}>
                  Process Instance Id
                  <input
                    type="text"
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  />
                </label>
              )}
            </Field>
            <Field name="evaluationDate">
              {({input}) => (
                <label htmlFor={input.name}>
                  Evaluation Date
                  <input
                    type="text"
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                  />
                </label>
              )}
            </Field>
            <button type="submit">Submit</button>
            <button type="reset" onClick={form.reset}>
              Reset
            </button>
          </FormElement>
        )}
      </Form>
    </CollapsablePanel>
  );
};

export {Filters};
