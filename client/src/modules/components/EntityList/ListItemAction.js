import React from 'react';
import {Icon, Dropdown, Button} from 'components';

import './ListItemAction.scss';

export default function ListItemAction({actions = [], singleAction}) {
  if (!actions || actions.length === 0) {
    return <div className="ListItemAction" />;
  }

  if (singleAction) {
    const {icon, action, text} = actions[0];
    return (
      <Button
        icon
        className="ListItemAction"
        onClick={evt => {
          evt.preventDefault();
          action(evt);
        }}
        title={text}
      >
        <Icon type={icon} />
      </Button>
    );
  }

  return (
    <Dropdown className="ListItemAction" icon label={<Icon type="context-menu" />}>
      {actions.map(({action, icon, text}, idx) => (
        <Dropdown.Option onClick={action} key={idx}>
          <Icon type={icon} /> {text}
        </Dropdown.Option>
      ))}
    </Dropdown>
  );
}
