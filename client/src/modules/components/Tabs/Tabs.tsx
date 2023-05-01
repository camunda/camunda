/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, ComponentPropsWithoutRef, ReactNode} from 'react';
import classnames from 'classnames';

import {ButtonGroup, Button} from 'components';
import {isReactElement} from 'services';

interface TabsProps<T extends string | number>
  extends Omit<ComponentPropsWithoutRef<'div'>, 'onChange'> {
  value?: T;
  onChange?: (value: T) => void;
  showButtons?: boolean;
}

interface TabProps extends Omit<ComponentPropsWithoutRef<'div'>, 'title'> {
  value?: string | number;
  title?: ReactNode;
  disabled?: boolean;
}

export default function Tabs<T extends string | number>({
  value = 0 as T,
  onChange = () => {},
  children,
  showButtons = true,
  className,
}: TabsProps<T>) {
  const [selected, setSelected] = useState<T>(value);

  useEffect(() => {
    setSelected(value);
  }, [value]);

  const tabs = React.Children.toArray(children).filter(isReactElement<TabProps>);

  return (
    <div className={classnames('Tabs', className)}>
      {showButtons && (
        <ButtonGroup>
          {tabs.map(({props: {value, title, disabled}}, idx) => {
            const valueOrIndex = getValueOrIndex(value, idx) as T;
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

Tabs.Tab = ({children, className}: TabProps): JSX.Element => {
  return <div className={classnames('Tab', className)}>{children}</div>;
};

function getValueOrIndex(value: number | string | undefined, idx: number): string | number {
  return typeof value !== 'undefined' ? value : idx;
}
