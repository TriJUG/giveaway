import { isAdmin, unauthorized } from '../auth';
import { json, notFound, badRequest, randomUUID } from '../utils';

interface Env {
  DB: D1Database;
  ADMIN_TOKEN: string;
}

export async function listSelections(
  req: Request,
  env: Env,
  giveawayId: string
): Promise<Response> {
  if (!isAdmin(req, env)) return unauthorized();
  const giveaway = await env.DB.prepare('SELECT id FROM giveaways WHERE id = ?')
    .bind(giveawayId)
    .first();
  if (!giveaway) return notFound('Giveaway not found');
  const { results } = await env.DB.prepare(
    `
    SELECT s.id, s.entry_id, e.name, e.email, s.kept, s.selected_at
    FROM selections s
    JOIN entries e ON e.id = s.entry_id
    WHERE s.giveaway_id = ?
    ORDER BY s.selected_at ASC
  `
  )
    .bind(giveawayId)
    .all();
  return json(results);
}

export async function recordSelection(
  req: Request,
  env: Env,
  giveawayId: string
): Promise<Response> {
  if (!isAdmin(req, env)) return unauthorized();
  const giveaway = await env.DB.prepare('SELECT id FROM giveaways WHERE id = ?')
    .bind(giveawayId)
    .first();
  if (!giveaway) return notFound('Giveaway not found');

  const body = await req.json<{ entry_id?: string; kept?: boolean }>();
  if (!body.entry_id) return badRequest('entry_id is required');
  if (body.kept === undefined) return badRequest('kept is required');

  const entry = await env.DB.prepare('SELECT id FROM entries WHERE id = ? AND giveaway_id = ?')
    .bind(body.entry_id, giveawayId)
    .first();
  if (!entry) return notFound('Entry not found in this giveaway');

  const id = randomUUID();
  await env.DB.prepare(
    'INSERT INTO selections (id, giveaway_id, entry_id, kept) VALUES (?, ?, ?, ?)'
  )
    .bind(id, giveawayId, body.entry_id, body.kept ? 1 : 0)
    .run();

  const row = await env.DB.prepare('SELECT * FROM selections WHERE id = ?').bind(id).first();
  return json(row, 201);
}
