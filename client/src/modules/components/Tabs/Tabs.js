/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';

import {ButtonGroup, Button} from 'components';

export default function Tabs({
  value = 0,
  onChange = () => {},
  children,
  showButtons = true,
  className,
}) {
  const [selected, setSelected] = useState(value);

  useEffect(() => {
    setSelected(value);
  }, [value]);

  const tabs = React.Children.toArray(children);

  return (
    <div className={classnames('Tabs', className)}>
      {showButtons && (
        <ButtonGroup>
          {tabs.map(({props: {value, title, disabled}}, idx) => {
            const valueOrIndex = getValueOrIndex(value, idx);
            return (
              <Button
                key={idx}
                active={selected === valueOrIndex}
                onClick={() => {
                  onChange(valueOrIndex);
                  setSelected(valueOrIndex);
                }}
                disabled={disabled}
              >
                {title}
              </Button>
            );
          })}
        </ButtonGroup>
      )}
      {tabs.find((child, idx) => getValueOrIndex(child.props.value, idx) === selected)}
    </div>
  );
}

Tabs.Tab = ({children, className}) => {
  return <div className={classnames('Tab', className)}>{children}</div>;
};

function getValueOrIndex(value, idx) {
  return typeof value !== 'undefined' ? value : idx;
}
