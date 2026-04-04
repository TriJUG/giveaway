const API = ['localhost', '127.0.0.1'].includes(window.location.hostname)
  ? 'http://localhost:3001/api'
  : 'https://giveaway-api.trijug.dev/api';

let activeGiveaway = null;

async function init() {
  try {
    const res = await fetch(`${API}/giveaways/active`);
    if (res.status === 404) {
      show('closed');
      return;
    }
    if (!res.ok) throw new Error('Failed to load');
    activeGiveaway = await res.json();
    document.getElementById('giveaway-name').textContent = activeGiveaway.name;
    show('form-section');
  } catch {
    show('closed');
  }
}

function show(sectionId) {
  ['loading', 'closed', 'form-section', 'success-section'].forEach((id) => {
    document.getElementById(id).style.display = id === sectionId ? '' : 'none';
  });
}

document.getElementById('entry-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('name').value.trim();
  const email = document.getElementById('email').value.trim();
  const msg = document.getElementById('message');
  const btn = document.getElementById('submit-btn');

  msg.style.display = 'none';
  msg.className = 'message';

  if (!name) {
    showMessage('Please enter your name.', 'error');
    return;
  }
  if (!email || !email.includes('@')) {
    showMessage('Please enter a valid email.', 'error');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Entering…';

  try {
    const res = await fetch(`${API}/giveaways/${activeGiveaway.id}/entries`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email }),
    });

    if (res.ok || res.status === 201) {
      show('success-section');
      return;
    }

    const data = await res.json().catch(() => ({}));
    showMessage(data.error || 'Something went wrong. Please try again.', 'error');
  } catch {
    showMessage('Network error. Please try again.', 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Enter Giveaway';
  }
});

function showMessage(text, type) {
  const msg = document.getElementById('message');
  msg.textContent = text;
  msg.className = `message ${type}`;
  msg.style.display = 'block';
}

init();
