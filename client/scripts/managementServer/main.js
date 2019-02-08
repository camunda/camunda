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

socket.onmessage = function(evt) {
  const content = JSON.parse(evt.data);

  const entry = document.createElement('p');
  entry.innerHTML = content.data;

  let logContainer;
  if (content.type === 'docker') {
    let subType = entry.textContent.split('|')[0].trim();

    if (subType === 'cambpm' || subType === 'elasticsearch') {
      logContainer = document.querySelector('#' + subType);
      const regex = new RegExp(subType + '\\s*\\|\\s', 'g');
      entry.textContent = entry.textContent.replace(regex, '');
    } else {
      logContainer = document.querySelector('#cambpm');
    }
  } else {
    logContainer = document.querySelector('#' + content.type);
  }

  logContainer.appendChild(entry);
};
