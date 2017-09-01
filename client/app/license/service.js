import {$window} from 'view-utils';
import {get, post} from 'request';
import {addNotification} from 'notifications';
import {formatDate} from 'utils';

export function checkLicense() {
  return get('/api/license/validate')
    .then(response => response.json());
}

export function uploadLicense(key) {
  return post('/api/license/validate-and-store', key, {
    headers: {
      'Content-Type': 'text/plain'
    }
  })
  .then(response => response.json());
}

export function checkLicenseAndNotifyIfExpiresSoon() {
  return checkLicense()
    .then(({validUntil, unlimited}) => {
      if (unlimited) {
        return;
      }

      const validUntilDate = new Date(validUntil);
      const validUntilTime = validUntilDate.getTime();
      const now = Date.now();
      const daysDiff = Math.ceil((validUntilTime - now) / (24 * 60 * 60 * 1000));

      if (daysDiff <= 10) {
        addNotification({
          type: 'warning',
          status: 'License soon expires',
          text: `${daysDiff} day${daysDiff > 1 ? 's': ''} left.
          License expires ${formatDate(validUntilDate)}.`
        });
      }
    })
    .catch(function() {
      if (process.env.NODE_ENV === 'production') {
        $window.location.pathname = '/license.html';
      }
    });
}
