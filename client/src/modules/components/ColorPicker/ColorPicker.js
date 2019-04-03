/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './ColorPicker.scss';

const colorsAmount = 16;

export const ColorPicker = ({onChange, className, selectedColor = ColorPicker.dark.steelBlue}) => {
  const handleChange = ({target}) => onChange(target.getAttribute('color'));
  const colors = [...Object.values(ColorPicker.dark), ...Object.values(ColorPicker.light)];

  return (
    <div className={classnames('ColorPicker', className)}>
      <div className="colorsContainer">
        {colors.map(color => (
          <div
            key={color}
            className={classnames('color', {active: color === selectedColor})}
            color={color}
            style={{backgroundColor: color}}
            onClick={handleChange}
          />
        ))}
      </div>
    </div>
  );
};

ColorPicker.dark = {
  cherry: '#B80000',
  red: '#DB3E00',
  yellow: '#FCCB00',
  green: '#008B02',
  teal: '#009688',
  paleGreen: '#00d0a3',
  blue: '#00bcd4',
  steelBlue: '#1991c8'
};

ColorPicker.light = {
  cherry: '#EB9694',
  red: '#FAD0C3',
  yellow: '#FEF3BD',
  green: '#C1E1C5',
  teal: '#b3e5e1',
  paleGreen: '#b5eee2',
  blue: '#b3e0e5',
  steelBlue: '#b3d5e5'
};

ColorPicker.getColors = amount => {
  const allColors = [
    ...Object.values(ColorPicker.dark).reverse(),
    ...Object.values(ColorPicker.light)
  ];
  if (amount > colorsAmount)
    return [...allColors, ...new Array(amount - colorsAmount).fill(ColorPicker.dark.steelBlue)];

  return allColors.slice(0, amount);
};

export default ColorPicker;
