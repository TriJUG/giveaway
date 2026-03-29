import {
  listGiveaways,
  getActiveGiveaway,
  createGiveaway,
  updateGiveaway,
} from './routes/giveaways';
import { listEntries, submitEntry, deleteEntry } from './routes/entries';
import { listSelections, recordSelection } from './routes/selections';

interface Env {
  DB: D1Database;
  ADMIN_TOKEN: string;
}

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

function withCors(response: Response): Response {
  const r = new Response(response.body, response);
  Object.entries(CORS_HEADERS).forEach(([k, v]) => r.headers.set(k, v));
  return r;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    // Strip /api prefix
    const path = url.pathname.replace(/^\/api/, '');
    const method = request.method;

    let response: Response;

    try {
      // GET /giveaways
      if (method === 'GET' && path === '/giveaways') {
        response = await listGiveaways(request, env);
      }
      // GET /giveaways/active
      else if (method === 'GET' && path === '/giveaways/active') {
        response = await getActiveGiveaway(request, env);
      }
      // POST /giveaways
      else if (method === 'POST' && path === '/giveaways') {
        response = await createGiveaway(request, env);
      }
      // PATCH /giveaways/:id
      else if (method === 'PATCH' && /^\/giveaways\/[^/]+$/.test(path)) {
        const id = path.split('/')[2]!;
        response = await updateGiveaway(request, env, id);
      }
      // GET /giveaways/:id/entries
      else if (method === 'GET' && /^\/giveaways\/[^/]+\/entries$/.test(path)) {
        const id = path.split('/')[2]!;
        response = await listEntries(request, env, id);
      }
      // POST /giveaways/:id/entries
      else if (method === 'POST' && /^\/giveaways\/[^/]+\/entries$/.test(path)) {
        const id = path.split('/')[2]!;
        response = await submitEntry(request, env, id);
      }
      // DELETE /entries/:id
      else if (method === 'DELETE' && /^\/entries\/[^/]+$/.test(path)) {
        const id = path.split('/')[2]!;
        response = await deleteEntry(request, env, id);
      }
      // GET /giveaways/:id/selections
      else if (method === 'GET' && /^\/giveaways\/[^/]+\/selections$/.test(path)) {
        const id = path.split('/')[2]!;
        response = await listSelections(request, env, id);
      }
      // POST /giveaways/:id/selections
      else if (method === 'POST' && /^\/giveaways\/[^/]+\/selections$/.test(path)) {
        const id = path.split('/')[2]!;
        response = await recordSelection(request, env, id);
      } else {
        response = new Response(JSON.stringify({ error: 'Not found' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        });
      }
    } catch (err) {
      console.error(err);
      response = new Response(JSON.stringify({ error: 'Internal server error' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    return withCors(response);
  },
};
