/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate, type Path} from 'react-router-dom';
import {Tag} from '@carbon/react';
import {cn} from './cn';
import {Nav, Button} from './styled';

type Props = {
  className?: string;
  label: string;
  items: Array<{
    key: string;
    title: string;
    label: string;
    selected: boolean;
    to: Partial<Path>;
    visible?: boolean;
    tagText?: string | number;
  }>;
};

const TabListNav: React.FC<Props> = ({className, label, items}) => {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Nav className={cn(className, 'cds--tabs')}>
      <div className="cds--tab--list" aria-label={label}>
        {items.map(({key, title, label, selected, to, visible, tagText}) => {
          const isHidden = visible === false;
          return (
            <Button
              key={key}
              type="button"
              role="link"
              aria-label={label}
              aria-current={selected ? 'page' : undefined}
              className={cn('cds--tabs__nav-item', 'cds--tabs__nav-link', {
                hidden: isHidden,
                ['cds--tabs__nav-item--selected']: selected,
              })}
              hidden={isHidden}
              aria-hidden={isHidden}
              onClick={() =>
                navigate({
                  ...location,
                  ...to,
                })
              }
            >
              <div className="cds--tabs__nav-item-label-wrapper">
                <span className="cds--tabs__nav-item-label">{title}</span>
                {tagText !== undefined && (
                  <Tag size="sm" type="red">
                    {tagText}
                  </Tag>
                )}
              </div>
            </Button>
          );
        })}
      </div>
    </Nav>
  );
};

export {TabListNav};
