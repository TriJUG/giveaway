#!/usr/bin/env node
// Minimal mock API server for local TUI testing.
// Usage: node mock-server.js [port]  (default: 3001)
//
// Then in another terminal:
//   cd packages/tui
//   TRIJUG_API_URL=http://localhost:3001 TRIJUG_ADMIN_TOKEN=test ./gradlew run

const http = require('http');
const PORT = parseInt(process.argv[2] || '3001', 10);

// ── In-memory state ───────────────────────────────────────────────────────────

const giveaways = [
  {
    id: 'mock-giveaway-1',
    name: 'March 2026 Meetup',
    date: '2026-03-28',
    active: 1,
    created_at: '2026-03-01T10:00:00',
  },
];

const entries = [
  {
    id: 'e1',
    giveaway_id: 'mock-giveaway-1',
    name: 'Alice Johnson',
    email: 'alice@example.com',
    created_at: '2026-03-28T18:01:00',
  },
  {
    id: 'e2',
    giveaway_id: 'mock-giveaway-1',
    name: 'Bob Smith',
    email: 'bob@example.com',
    created_at: '2026-03-28T18:02:00',
  },
  {
    id: 'e3',
    giveaway_id: 'mock-giveaway-1',
    name: 'Venkataraman Subramaniam',
    email: 'venkatar@example.com',
    created_at: '2026-03-28T18:03:00',
  },
  {
    id: 'e4',
    giveaway_id: 'mock-giveaway-1',
    name: 'Dave Martinez',
    email: 'dave@example.com',
    created_at: '2026-03-28T18:04:00',
  },
  {
    id: 'e5',
    giveaway_id: 'mock-giveaway-1',
    name: 'Priya Chandrasekaran',
    email: 'priya@example.com',
    created_at: '2026-03-28T18:05:00',
  },
  {
    id: 'e6',
    giveaway_id: 'mock-giveaway-1',
    name: 'Frank Garcia',
    email: 'frank@example.com',
    created_at: '2026-03-28T18:06:00',
  },
  {
    id: 'e7',
    giveaway_id: 'mock-giveaway-1',
    name: 'Grace Lee',
    email: 'grace@example.com',
    created_at: '2026-03-28T18:07:00',
  },
  {
    id: 'e8',
    giveaway_id: 'mock-giveaway-1',
    name: 'Ananthakrishnamachari Balasubramaniam',
    email: 'ananth@example.com',
    created_at: '2026-03-28T18:08:00',
  },
  {
    id: 'e9',
    giveaway_id: 'mock-giveaway-1',
    name: 'Henry Wilson',
    email: 'henry@example.com',
    created_at: '2026-03-28T18:09:00',
  },
  {
    id: 'e10',
    giveaway_id: 'mock-giveaway-1',
    name: 'Lakshmi Narayanaswamy',
    email: 'lakshmi@example.com',
    created_at: '2026-03-28T18:10:00',
  },
  {
    id: 'e11',
    giveaway_id: 'mock-giveaway-1',
    name: 'Ed',
    email: 'ed@example.com',
    created_at: '2026-03-28T18:11:00',
  },
  {
    id: 'e12',
    giveaway_id: 'mock-giveaway-1',
    name: 'Carol Williams',
    email: 'carol@example.com',
    created_at: '2026-03-28T18:12:00',
  },
  {
    id: 'e13',
    giveaway_id: 'mock-giveaway-1',
    name: 'Raghunathan Krishnamurthy',
    email: 'raghu@example.com',
    created_at: '2026-03-28T18:13:00',
  },
  {
    id: 'e14',
    giveaway_id: 'mock-giveaway-1',
    name: 'Sue Park',
    email: 'sue@example.com',
    created_at: '2026-03-28T18:14:00',
  },
  {
    id: 'e15',
    giveaway_id: 'mock-giveaway-1',
    name: 'Balakrishna Venkatanarasimharaju',
    email: 'bala@example.com',
    created_at: '2026-03-28T18:15:00',
  },
];

const selections = [];

let idCounter = 100;
const uuid = () => `mock-${++idCounter}-${Date.now()}`;
const now = () => new Date().toISOString().replace('T', ' ').substring(0, 19);

const SEED_NAMES = [
  ['Alice Johnson', 'alice@example.com'],
  ['Bob Smith', 'bob@example.com'],
  ['Venkataraman Subramaniam', 'venkatar@example.com'],
  ['Dave Martinez', 'dave@example.com'],
  ['Priya Chandrasekaran', 'priya@example.com'],
  ['Frank Garcia', 'frank@example.com'],
  ['Grace Lee', 'grace@example.com'],
  ['Ananthakrishnamachari Balasubramaniam', 'ananth@example.com'],
  ['Henry Wilson', 'henry@example.com'],
  ['Lakshmi Narayanaswamy', 'lakshmi@example.com'],
  ['Ed', 'ed@example.com'],
  ['Carol Williams', 'carol@example.com'],
  ['Raghunathan Krishnamurthy', 'raghu@example.com'],
  ['Sue Park', 'sue@example.com'],
  ['Balakrishna Venkatanarasimharaju', 'bala@example.com'],
];

function seedEntries(giveawayId) {
  const baseTime = new Date();
  SEED_NAMES.forEach(([name, email], i) => {
    const t = new Date(baseTime.getTime() + i * 60000);
    const ts = t.toISOString().replace('T', ' ').substring(0, 19);
    entries.push({ id: uuid(), giveaway_id: giveawayId, name, email, created_at: ts });
  });
}

// ── Router ────────────────────────────────────────────────────────────────────

function route(method, path, body) {
  // GET /api/giveaways/active
  if (method === 'GET' && path === '/api/giveaways/active') {
    const g = giveaways.find((g) => g.active);
    if (!g) return [404, { error: 'No active giveaway' }];
    return [200, { id: g.id, name: g.name, date: g.date }];
  }

  // GET /api/giveaways
  if (method === 'GET' && path === '/api/giveaways') {
    return [200, giveaways];
  }

  // POST /api/giveaways
  if (method === 'POST' && path === '/api/giveaways') {
    if (body.active) giveaways.forEach((g) => (g.active = 0));
    const g = {
      id: uuid(),
      name: body.name,
      date: body.date,
      active: body.active ? 1 : 0,
      created_at: now(),
    };
    giveaways.push(g);
    seedEntries(g.id);
    return [201, g];
  }

  // PATCH /api/giveaways/:id
  const patchMatch = path.match(/^\/api\/giveaways\/([^/]+)$/);
  if (method === 'PATCH' && patchMatch) {
    const g = giveaways.find((g) => g.id === patchMatch[1]);
    if (!g) return [404, { error: 'Not found' }];
    if (body.active === true) giveaways.forEach((g) => (g.active = 0));
    if (body.name !== undefined) g.name = body.name;
    if (body.date !== undefined) g.date = body.date;
    if (body.active !== undefined) g.active = body.active ? 1 : 0;
    return [200, g];
  }

  // GET /api/giveaways/:id/entries
  const entriesMatch = path.match(/^\/api\/giveaways\/([^/]+)\/entries$/);
  if (method === 'GET' && entriesMatch) {
    return [200, entries.filter((e) => e.giveaway_id === entriesMatch[1])];
  }

  // POST /api/giveaways/:id/entries
  if (method === 'POST' && entriesMatch) {
    const existing = entries.find(
      (e) => e.giveaway_id === entriesMatch[1] && e.email === body.email?.toLowerCase()
    );
    if (existing) return [201, { name: existing.name }]; // silent dedup
    const e = {
      id: uuid(),
      giveaway_id: entriesMatch[1],
      name: body.name,
      email: body.email.toLowerCase(),
      created_at: now(),
    };
    entries.push(e);
    return [201, { name: e.name }];
  }

  // DELETE /api/entries/:id
  const deleteMatch = path.match(/^\/api\/entries\/([^/]+)$/);
  if (method === 'DELETE' && deleteMatch) {
    const idx = entries.findIndex((e) => e.id === deleteMatch[1]);
    if (idx === -1) return [404, { error: 'Not found' }];
    entries.splice(idx, 1);
    return [204, null];
  }

  // GET /api/giveaways/:id/selections
  const selectionsMatch = path.match(/^\/api\/giveaways\/([^/]+)\/selections$/);
  if (method === 'GET' && selectionsMatch) {
    const gid = selectionsMatch[1];
    const result = selections
      .filter((s) => s.giveaway_id === gid)
      .map((s) => {
        const entry = entries.find((e) => e.id === s.entry_id) || {};
        return { ...s, name: entry.name, email: entry.email };
      });
    return [200, result];
  }

  // POST /api/giveaways/:id/selections
  if (method === 'POST' && selectionsMatch) {
    const s = {
      id: uuid(),
      giveaway_id: selectionsMatch[1],
      entry_id: body.entry_id,
      kept: body.kept ? 1 : 0,
      selected_at: now(),
    };
    selections.push(s);
    return [201, s];
  }

  return [404, { error: 'Not found' }];
}

// ── Server ────────────────────────────────────────────────────────────────────

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  let body = '';
  req.on('data', (chunk) => (body += chunk));
  req.on('end', () => {
    const parsed = body ? JSON.parse(body) : {};
    const [status, data] = route(req.method, path, parsed);

    const headers = {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    if (req.method === 'OPTIONS') {
      res.writeHead(204, headers);
      res.end();
      return;
    }

    console.log(`${req.method} ${path} → ${status}`);
    res.writeHead(status, headers);
    res.end(data === null ? '' : JSON.stringify(data));
  });
});

server.listen(PORT, () => {
  console.log(`Mock API running at http://localhost:${PORT}`);
  console.log('');
  console.log('To run the TUI against this mock:');
  console.log(`  cd packages/tui`);
  console.log(`  TRIJUG_API_URL=http://localhost:${PORT} TRIJUG_ADMIN_TOKEN=test ./gradlew run`);
  console.log('');
  console.log(`Seeded with ${giveaways.length} giveaway(s) and ${entries.length} entries.`);
});
