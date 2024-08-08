/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

let currentlyActive = document.querySelector('#backend');
let currentlyActiveButton = document.querySelector('.tabs .buttons .active');

function onClickHandler(evt) {
  currentlyActiveButton.classList.remove('active');
  currentlyActiveButton = evt.target;
  currentlyActiveButton.classList.add('active');

  currentlyActive.classList.remove('active');
  currentlyActive = document.getElementById(evt.target.getAttribute('data-topic'));
  currentlyActive.classList.add('active');
}

const buttons = document.querySelectorAll('.tabs .buttons button');
for (let i = 0; i < buttons.length; i++) {
  buttons[i].addEventListener('click', onClickHandler);
}

const socket = new WebSocket('ws://' + window.location.host);

socket.onmessage = function (evt) {
  const content = JSON.parse(evt.data);

  const entry = document.createElement('p');
  entry.innerHTML = content.data;

  let logContainer;
  if (content.type === 'docker') {
    let subType = entry.textContent.split('|')[0].trim();

    if (['elasticsearch', 'zeebe', 'keycloak', 'identity'].includes(subType)) {
      logContainer = document.querySelector('#' + subType);
      const regex = new RegExp(subType + '\\s*\\|\\s', 'g');
      entry.textContent = entry.textContent.replace(regex, '');
    }
  } else {
    logContainer = document.querySelector('#' + content.type);
  }

  const scrolledToBottom =
    logContainer.scrollTop >= logContainer.scrollHeight - logContainer.offsetHeight;
  logContainer.appendChild(entry);
  if (logContainer.classList.contains('active') && scrolledToBottom) {
    logContainer.scrollBy(0, Number.MAX_SAFE_INTEGER);
  }
};
