import { isAdmin, unauthorized } from '../auth';
import { json, notFound, badRequest, conflict, randomUUID } from '../utils';

interface Env {
  DB: D1Database;
  ADMIN_TOKEN: string;
}

export async function listEntries(req: Request, env: Env, giveawayId: string): Promise<Response> {
  if (!isAdmin(req, env)) return unauthorized();
  const giveaway = await env.DB.prepare('SELECT id FROM giveaways WHERE id = ?')
    .bind(giveawayId)
    .first();
  if (!giveaway) return notFound('Giveaway not found');
  const { results } = await env.DB.prepare(
    'SELECT id, name, email, created_at FROM entries WHERE giveaway_id = ? ORDER BY created_at ASC'
  )
    .bind(giveawayId)
    .all();
  return json(results);
}

export async function submitEntry(req: Request, env: Env, giveawayId: string): Promise<Response> {
  const giveaway = await env.DB.prepare('SELECT id, active FROM giveaways WHERE id = ?')
    .bind(giveawayId)
    .first<{ id: string; active: number }>();
  if (!giveaway) return notFound('Giveaway not found');

  const adminRequest = isAdmin(req, env);
  if (!adminRequest && !giveaway.active)
    return conflict('This giveaway is not currently accepting entries');

  const body = await req.json<{ name?: string; email?: string }>();
  if (!body.name?.trim()) return badRequest('name is required');
  if (!body.email?.trim() || !body.email.includes('@'))
    return badRequest('valid email is required');

  const id = randomUUID();
  try {
    await env.DB.prepare(
      'INSERT OR IGNORE INTO entries (id, giveaway_id, name, email) VALUES (?, ?, ?, ?)'
    )
      .bind(id, giveawayId, body.name.trim(), body.email.trim().toLowerCase())
      .run();
  } catch {
    return json({ error: 'Failed to save entry' }, 500);
  }

  return json({ name: body.name.trim() }, 201);
}

export async function deleteEntry(req: Request, env: Env, entryId: string): Promise<Response> {
  if (!isAdmin(req, env)) return unauthorized();
  const entry = await env.DB.prepare('SELECT id FROM entries WHERE id = ?').bind(entryId).first();
  if (!entry) return notFound('Entry not found');
  await env.DB.prepare('DELETE FROM entries WHERE id = ?').bind(entryId).run();
  return new Response(null, { status: 204 });
}
