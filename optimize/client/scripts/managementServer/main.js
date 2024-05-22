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

document
  .getElementById('generateDataButton')
  .addEventListener('click', () => fetch('api/generateNewData'));

const socket = new WebSocket('ws://' + window.location.host);

socket.onmessage = function (evt) {
  const content = JSON.parse(evt.data);

  const entry = document.createElement('p');
  entry.innerHTML = content.data;

  let logContainer;
  if (content.type === 'docker') {
    let subType = entry.textContent.split('|')[0].trim();

    if (['cambpm', 'elasticsearch', 'zeebe'].includes(subType)) {
      logContainer = document.querySelector('#' + subType);
      const regex = new RegExp(subType + '\\s*\\|\\s', 'g');
      entry.textContent = entry.textContent.replace(regex, '');
    } else {
      logContainer = document.querySelector('#cambpm');
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
