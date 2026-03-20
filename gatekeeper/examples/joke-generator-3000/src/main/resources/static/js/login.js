document.getElementById('login-form').addEventListener('submit', function(e) {
  e.preventDefault();
  var form = e.target;
  var data = new URLSearchParams(new FormData(form));
  fetch('/login', {
    method: 'POST',
    body: data,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    credentials: 'same-origin'
  }).then(function(response) {
    if (response.ok) {
      window.location.href = '/jokes';
    } else {
      document.getElementById('login-error-js').style.display = 'block';
    }
  }).catch(function() {
    document.getElementById('login-error-js').style.display = 'block';
  });
});
