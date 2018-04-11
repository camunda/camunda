import React from 'react';
import Button from './Button';

const Dropdown = props => <div>{props.children}</div>;
Dropdown.Option = props => <Button {...props}>{props.children}</Button>;

export default Dropdown;
