document.addEventListener('DOMContentLoaded', function() {
  document.getElementById('random-joke-btn').addEventListener('click', function() {
    fetch('/api/jokes/random', { credentials: 'same-origin' })
      .then(function(response) {
        if (!response.ok) throw new Error('Failed to fetch joke');
        return response.json();
      })
      .then(function(joke) {
        document.getElementById('random-joke-setup').textContent = joke.setup;
        document.getElementById('random-joke-punchline').textContent = joke.punchline;
        document.getElementById('random-joke-category').textContent = joke.category;
        document.getElementById('random-joke-container').style.display = 'block';
      })
      .catch(function(err) {
        console.error('Error fetching random joke:', err);
      });
  });
});
