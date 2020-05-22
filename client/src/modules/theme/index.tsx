/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const theme = {
  colors: {
    blue: '#4d90ff',
    green: '#10d070',
    red: '#ff3d3d',
    orange: '#ffa533',
    ui: ['#f2f3f5', '#f7f8fa', '#b0bac7', '#fdfdfe', '#d8dce3', '#62626e'],
    item: {odd: '#fdfdfe', even: '#f9fafc'},
    text: {
      button: 'rgba(69, 70, 78, 0.9)',
      copyrightNotice: 'rgba(98, 98, 110, 0.9)',
    },
    overlay: 'rgba(255, 255, 255, 0.75)',
  },
} as const;

export {theme};
