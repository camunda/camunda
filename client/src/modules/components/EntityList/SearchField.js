import React, {useState, useRef} from 'react';
import {Button, Icon, Input} from 'components';
import classnames from 'classnames';

import './SearchField.scss';

export default function SearchField({value, onChange = () => {}}) {
  const [open, setOpen] = useState(false);
  const input = useRef();

  return (
    <div className="SearchField">
      <Button
        icon
        onClick={() => {
          setOpen(!open);
          onChange('');

          if (!open && input.current) {
            input.current.focus();
          }
        }}
      >
        <Icon type="search" className={classnames({hidden: open})} />
        <Icon type="search-reset" className={classnames({hidden: !open})} />
      </Button>
      <Input
        className={classnames({open})}
        value={value}
        onChange={evt => onChange(evt.target.value)}
        ref={input}
      />
    </div>
  );
}
