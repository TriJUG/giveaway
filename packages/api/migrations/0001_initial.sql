CREATE TABLE IF NOT EXISTS giveaways (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  date        TEXT NOT NULL,
  active      INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS entries (
  id          TEXT PRIMARY KEY,
  giveaway_id TEXT NOT NULL REFERENCES giveaways(id),
  name        TEXT NOT NULL,
  email       TEXT NOT NULL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(giveaway_id, email)
);

CREATE TABLE IF NOT EXISTS selections (
  id          TEXT PRIMARY KEY,
  giveaway_id TEXT NOT NULL REFERENCES giveaways(id),
  entry_id    TEXT NOT NULL REFERENCES entries(id),
  kept        INTEGER NOT NULL,
  selected_at TEXT NOT NULL DEFAULT (datetime('now'))
);
