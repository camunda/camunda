/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

let translationObject = {};

document.querySelector('form').addEventListener('submit', validateAndStoreLicense);

function validateAndStoreLicense(event) {
  event.preventDefault();

  var licenseKey = document.querySelector('textarea').value;

  var request = new XMLHttpRequest();
  request.open('POST', 'api/license/validate-and-store');
  request.setRequestHeader('Content-Type', 'text/plain');
  request.addEventListener('readystatechange', function(event) {
    if (event.target.readyState === 4) {
      var response = JSON.parse(event.target.response);
      var alertBox = document.querySelector('.alert');

      alertBox.style.display = 'block';

      if (response.errorMessage) {
        alertBox.setAttribute('class', 'alert alert-danger');
        alertBox.textContent = response.errorMessage;
      } else {
        var message = translationObject.license.licensedFor + ' ' + response.customerId + '.';

        if (!response.unlimited) {
          var date = new Date(response.validUntil);

          message += ' ' + translationObject.license.validUntil + ' ' + date.toUTCString() + '.';
        }

        message += ' ' + translationObject.license.redirectMessage;

        alertBox.innerHTML = message;
        alertBox.setAttribute('class', 'alert alert-success');

        window.setTimeout(function() {
          window.location.href = './';
        }, 10000);
      }
    }
  });

  request.send(licenseKey);
}

function validateLicense() {
  var request = new XMLHttpRequest();
  request.open('GET', 'api/license/validate');
  request.addEventListener('readystatechange', function(event) {
    if (event.target.readyState === 4) {
      var response = JSON.parse(event.target.response);
      var alertBox = document.querySelector('.alert');

      alertBox.style.display = 'block';

      if (response.errorMessage) {
        alertBox.setAttribute('class', 'alert alert-danger');
        alertBox.textContent = response.errorMessage;
      } else {
        var message = translationObject.license.licensedFor + ' ' + response.customerId + '.';

        if (!response.unlimited) {
          var date = new Date(response.validUntil);

          message += ' ' + translationObject.license.validUntil + ' ' + date.toUTCString() + '.';
        }

        alertBox.innerHTML = message;
        alertBox.setAttribute('class', 'alert alert-success');
      }
    }
  });

  request.send();
}

function renderFooter() {
  const versionRequest = new XMLHttpRequest();
  versionRequest.open('GET', 'api/meta/version');
  versionRequest.send();
  const footerInfo = document.querySelector('footer');

  versionRequest.addEventListener('readystatechange', function(event) {
    if (event.target.readyState === 4) {
      var response = JSON.parse(event.target.response);
      footerInfo.innerText =
        '© Camunda Services GmbH ' +
        new Date().getFullYear() +
        ', ' +
        translationObject.footer.rightsReserved +
        ' | ' +
        response.optimizeVersion;
    }
  });
}

function addFormText() {
  const textArea = document.querySelector('form textArea');
  const textAreaLabel = document.querySelector('form label');
  const submitButton = document.querySelector('button[type="submit"]');
  textArea.setAttribute('placeholder', translationObject.license.enterLicense);
  submitButton.textContent = translationObject.license.submit;
  textAreaLabel.textContent = translationObject.license.licenseKey;
}

function getLanguage() {
  const nav = window.navigator;
  const browserLang = (
    (Array.isArray(nav.languages)
      ? nav.languages[0]
      : nav.language || nav.browserLanguage || nav.systemLanguage || nav.userLanguage) || ''
  ).split('-');

  return browserLang[0].toLowerCase();
}

var translationRequest = new XMLHttpRequest();
translationRequest.open('GET', 'api/localization?localeCode=' + getLanguage());
translationRequest.addEventListener('readystatechange', function(event) {
  if (event.target.readyState === 4) {
    translationObject = JSON.parse(event.target.response);
    addFormText();
    validateLicense();
    renderFooter();
  }
});
translationRequest.send();
