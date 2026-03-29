import { isAdmin, unauthorized } from '../auth';
import { json, notFound, badRequest, randomUUID, isValidDate } from '../utils';

interface Env {
  DB: D1Database;
  ADMIN_TOKEN: string;
}

export async function listGiveaways(_req: Request, env: Env): Promise<Response> {
  const { results } = await env.DB.prepare(
    'SELECT id, name, date, active, created_at FROM giveaways ORDER BY date DESC'
  ).all();
  return json(results);
}

export async function getActiveGiveaway(_req: Request, env: Env): Promise<Response> {
  const row = await env.DB.prepare(
    'SELECT id, name, date FROM giveaways WHERE active = 1 LIMIT 1'
  ).first();
  if (!row) return notFound('No active giveaway');
  return json(row);
}

export async function createGiveaway(req: Request, env: Env): Promise<Response> {
  if (!isAdmin(req, env)) return unauthorized();
  const body = await req.json<{ name?: string; date?: string; active?: boolean }>();
  if (!body.name?.trim()) return badRequest('name is required');
  if (!body.date || !isValidDate(body.date)) return badRequest('date must be YYYY-MM-DD');

  const id = randomUUID();
  if (body.active) {
    await env.DB.prepare('UPDATE giveaways SET active = 0').run();
  }
  await env.DB.prepare('INSERT INTO giveaways (id, name, date, active) VALUES (?, ?, ?, ?)')
    .bind(id, body.name.trim(), body.date, body.active ? 1 : 0)
    .run();

  const row = await env.DB.prepare('SELECT * FROM giveaways WHERE id = ?').bind(id).first();
  return json(row, 201);
}

export async function updateGiveaway(req: Request, env: Env, id: string): Promise<Response> {
  if (!isAdmin(req, env)) return unauthorized();
  const existing = await env.DB.prepare('SELECT id FROM giveaways WHERE id = ?').bind(id).first();
  if (!existing) return notFound();

  const body = await req.json<{ name?: string; date?: string; active?: boolean }>();
  if (body.date !== undefined && !isValidDate(body.date))
    return badRequest('date must be YYYY-MM-DD');

  if (body.active === true) {
    await env.DB.prepare('UPDATE giveaways SET active = 0').run();
  }

  const fields: string[] = [];
  const values: unknown[] = [];
  if (body.name !== undefined) {
    fields.push('name = ?');
    values.push(body.name.trim());
  }
  if (body.date !== undefined) {
    fields.push('date = ?');
    values.push(body.date);
  }
  if (body.active !== undefined) {
    fields.push('active = ?');
    values.push(body.active ? 1 : 0);
  }

  if (fields.length > 0) {
    values.push(id);
    await env.DB.prepare(`UPDATE giveaways SET ${fields.join(', ')} WHERE id = ?`)
      .bind(...values)
      .run();
  }

  const row = await env.DB.prepare('SELECT * FROM giveaways WHERE id = ?').bind(id).first();
  return json(row);
}
