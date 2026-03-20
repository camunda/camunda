document.addEventListener('DOMContentLoaded', function() {
  var logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', function() {
      fetch('/logout', {
        method: 'POST',
        credentials: 'same-origin'
      }).then(function(response) {
        if (response.status === 204) {
          // Basic auth mode — no IdP logout needed
          window.location.href = '/';
        } else if (response.ok) {
          // OIDC mode — gatekeeper returns JSON with IdP logout URL
          return response.json().then(function(data) {
            if (data.url) {
              window.location.href = data.url;
            } else {
              window.location.href = '/';
            }
          });
        } else {
          window.location.href = '/';
        }
      }).catch(function() {
        window.location.href = '/';
      });
    });
  }
});
